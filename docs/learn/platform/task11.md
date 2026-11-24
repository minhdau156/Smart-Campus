# Task 11: `notification-service` + idempotent consumer - learning note

> Task: [task11.md](../../../tasks/platform/task11.md) · Commit: `baa3ed3` · Date: 2026-11-24

## In one minute
Kafka delivers every event **at least once**, so sometimes twice. T7 showed this really happens:
when Kafka is slow to answer, the outbox sends the event again. If a consumer acts on both copies,
a student gets two emails or two tickets. This task makes a consumer **idempotent**: doing it twice
has the same result as doing it once. The tool is `IdempotentConsumer` in platform-starter. Before
doing the work, it writes "I handled event X" in a table, and both writes happen in **one**
database transaction. The task also builds the second service, `notification-service`, with a
small example listener that reads core-app's probe events. It is locked to its own schema, like
registration-service in T10.

## The big picture

```
 core-app (T7 outbox)                 Kafka                    notification-service
 POST /outbox-probe ──► event_publication ──► platform.probe ──► ProbeListener.on(json)
                                         (maybe twice!)           │
                                                                  ▼
                          IdempotentConsumer.runOnce("notification.probe", eventId, work)
                          ┌─────────────── ONE transaction ───────────────────────┐
                          │ INSERT INTO notification.processed_events             │
                          │        (consumer, event_id) ... ON CONFLICT DO NOTHING │
                          │   1 row inserted → first time → run work:             │
                          │        INSERT INTO notification.probe_read_model ...   │
                          │   0 rows inserted → seen before → skip                 │
                          └──────────────── COMMIT (both) or ROLLBACK (both) ──────┘
```

## The files

### `backend/platform-starter/pom.xml`
- **What changed:** a new `optional` dependency, `spring-jdbc`.
- **Why:** `IdempotentConsumer` needs `JdbcTemplate` and transactions. "Optional" means the
  starter can use it, but apps that use the starter do not get it automatically. Every app that
  uses a database has it anyway.

### `platform-starter/…/consumer/IdempotentConsumer.java`
- **What it does:** runs a piece of work ("side effect") at most once per (consumer, eventId).
- **Key lines:**
  ```java
  insertSql = "INSERT INTO " + table + " (consumer, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING";

  Boolean ran = transaction.execute(status -> {
      if (jdbc.update(insertSql, consumer, eventId) == 0) {
          return false;      // seen before: skip
      }
      sideEffect.run();      // e.g. save a row; an exception rolls back BOTH writes
      return true;
  });
  ```
  - `ON CONFLICT DO NOTHING`: a PostgreSQL rule. If the key already exists, insert nothing and
    return "0 rows", instead of failing with an error.
  - `TransactionTemplate.execute(...)`: everything inside is one transaction. If the side effect
    throws an exception, the dedupe row is undone too. So the next delivery tries again.
  - **Two copies at the same moment:** the second `INSERT` waits until the first transaction
    ends. If the first committed, the second gets 0 rows and skips. If it rolled back, the second
    inserts and runs.
- **Why it is a plain class (no auto-configuration):** spec D2 puts `processed_events` in each
  *consuming module's* schema. In core-app there will be many modules with many schemas, so one
  app-wide bean would point at the wrong table. Each consumer creates its own:
  `new IdempotentConsumer(jdbc, txManager, "notification")`.
- **Safety:** the schema name is glued into the SQL text, so the constructor only accepts plain
  names (`[a-z][a-z0-9_]*`). That blocks SQL injection through the schema name.

### `platform-starter/…/consumer/IdempotentConsumerTest.java`
- **What it does:** fast tests without a database: the table name, invalid schema names are
  refused, `null` arguments are refused. The real behavior is tested against PostgreSQL in
  notification-service (below).

### `pom.xml` (root) and `backend/notification-service/pom.xml`
- **What changed:** the new module is in the root `<modules>` list. Its pom has web, actuator,
  jdbc, Flyway, Postgres, springdoc and the **Kafka starter**. There is no outbox (Modulith event
  registry) yet: this app only *reads* events for now.
- Test libraries: Testcontainers for Postgres **and Kafka**, plus Awaitility ("wait until …").

### `NotificationApplication.java`, `ClockConfiguration.java`, `PingController.java`
- The same pattern as registration-service (T10): `main`, one `Clock` bean,
  `GET /api/v1/notification/ping`. The gateway (T13) will route to this ping.

### `ConsumerConfiguration.java`
- **What it does:** creates this app's `IdempotentConsumer` bean, bound to the `notification`
  schema. Every listener in this service uses it.

### `ProbeListener.java`
- **What it does:** the example consumer (dev/test profiles only). It reads `platform.probe`,
  turns the JSON into a `DomainEvent<ProbePayload>`, and saves one row per event.
- **Key lines:**
  ```java
  static final String CONSUMER = "notification.probe";   // stored in processed_events; never rename
  record ProbePayload(UUID id, String note, Instant createdAt) {}   // our OWN copy of the payload shape

  @KafkaListener(id = CONSUMER, groupId = CONSUMER, topics = "platform.probe")
  void on(String envelope) {
      DomainEvent<ProbePayload> event = json.readValue(envelope, PROBE_EVENT);
      idempotent.runOnce(CONSUMER, event.eventId(), () -> jdbc.update("INSERT INTO notification.probe_read_model …"));
  }
  ```
  - `@KafkaListener`: Spring calls this method for every record on the topic. `groupId` is the
    **consumer group**: Kafka remembers how far this group has read (its "offset").
  - `ProbePayload` is a copy, not an import from core-app. Services must not depend on each
    other's code, only on the event's JSON shape.
  - If the method throws, Spring Kafka does not move the offset forward and delivers the record
    again. Thanks to the rollback, the retry starts clean.

### `src/main/resources/application.yml`
- **Key lines:**
  ```yaml
  datasource.username: notification_svc       # may only use schema notification (D3)
  flyway.default-schema: notification
  kafka.consumer.auto-offset-reset: earliest  # a NEW group starts at the oldest record
  server.port: 8083
  ```
  - `auto-offset-reset: earliest`: Kafka's own default is `latest`. With that default, a
    listener's first start would skip every event sent before it. `earliest` reads them.
  - The `dev` and `test` profiles add the folder `db/migration/notification-probe`, so the probe
    table never exists in production.

### Migrations
- `V202611242000__baseline.sql`: a comment on the schema. It makes Flyway create its history
  table, which proves migrations work as `notification_svc`.
- `V202611242010__processed_events.sql`: the dedupe table, with `PRIMARY KEY (consumer, event_id)`.
  `processed_at` gets `DEFAULT now()` from the database.
- `notification-probe/V202611242020__probe_read_model.sql` (dev/test only): the listener's table.
  **It has its own `id` on purpose.** If the probe id were the primary key, a second copy would
  already fail without any dedupe, and the tests would prove nothing.

### Test: `TestcontainersConfiguration.java`
- The same idea as T10: Postgres runs the real `infra/postgres/init` script, and the app logs in as
  `notification_svc`, not as a superuser. New here: a real **Kafka** container
  (`@ServiceConnection`), and the `platform.probe` topic (in Compose, core-app creates it).

### Test: `IdempotentConsumerIT.java` (4 tests, real PostgreSQL)
1. The same event twice → the first call returns `true`, the second `false`, and there is 1 row.
2. **The error case from 11.5:** the side effect writes its row and then throws. Afterwards there
   is **no** dedupe row and **no** probe row: both were rolled back. A retry then works, once.
3. Two different consumer names → each one runs once (the key is consumer + eventId).
4. Two copies at the **same moment** (two threads) → exactly one runs, the other skips.

### Test: `ProbeListenerIT.java` (real Kafka)
- Sends the **same** envelope twice, then a third "marker" event with the same key.
- **Why the marker:** you cannot wait for "nothing happened". Kafka keeps order per key, so once the
  marker's row exists, both copies before it were processed. Only then does the test count:
  exactly 1 row for the duplicated event.

### Tests: `SchemaIsolationIT`, `NotificationApplicationIT`, `PingControllerTest`, `OpenApiExportIT`
- Copies of T10's tests for this service. Isolation: as `notification_svc`, every other schema
  (now including `registration`) answers `42501` (permission denied). The app IT checks that the
  `processed_events` and `probe_read_model` tables exist. The export writes
  `docs/api/notification-service.json`.

### `SPEC-platform.md`, `tasks/…`
- O4 ticked. I2 ticked (both service users are now proven). Decision D2 updated: the key is
  `(consumer, event_id)`, and the spec names `IdempotentConsumer`. T11 ticked in the task file,
  `todo.md` and `plan.md`.

## Decisions and why
| Decision | Options | Why |
|---|---|---|
| Dedupe row + side effect in one DB transaction | Kafka "exactly once" transactions | Kafka transactions do not cover our database writes (spec D2) |
| `ON CONFLICT DO NOTHING` | Catch the duplicate-key exception | In PostgreSQL an error ends the transaction, so you cannot continue in it. "0 rows" is a clean signal |
| Plain class, one per schema | Auto-configured bean | `processed_events` lives in each module's schema, so one app-wide bean is wrong for core-app |
| Key `(consumer, event_id)` | `event_id` alone (old D2 text) | Two listeners in one schema must each handle the event once. The spec was updated |
| Probe table with its own `id` | `probe_id` as the key | Otherwise duplicates are blocked without the dedupe, and the test proves nothing |
| `auto-offset-reset: earliest` in this app | Put it in the starter now | Smallest change. T12 (topics and replay) may move it to the starter |
| Ping + OpenAPI export here too | Leave them for later | The gateway (T13) and the smoke script call `/api/v1/notification/ping`, and spec A1 wants every app's docs |

## Problems we hit
- **My first "prove it can fail" edit broke the code** (one parenthesis too many), so that run
  proved nothing. The files were restored and the change was done again correctly.
- Otherwise none. The tests passed after the first implementation. The expected RED moments were:
  missing classes, and "notification-service.json is missing" before the export.

## How we checked it works
- RED → GREEN for `IdempotentConsumerTest`, and for the service (missing classes, then green).
- **Proved the tests can fail:** (1) the listener writing without the dedupe → `ProbeListenerIT`
  failed; (2) `IdempotentConsumer` without its transaction → "dedupe row rolled back: expected 0
  but was 1". Both changes were then undone.
- Full `./mvnw verify`: green for all 6 modules. notification-service has 2 unit tests and 20 ITs.
  The starter has 17 unit tests and 6 ITs.
- **End to end on Compose:** core-app and notification-service in the `dev` profile,
  `curl -X POST "localhost:8081/api/v1/platform/outbox-probe?note=end-to-end-T11"` → exactly one
  row in `notification.probe_read_model` with its mark in `processed_events`. So the real outbox
  JSON is read correctly.

## Words to know
| Word | Meaning in simple words |
|---|---|
| Idempotent | Doing it twice gives the same result as doing it once |
| At-least-once delivery | Every event arrives, but maybe more than once |
| Side effect | The real work a consumer does (save a row, send an email) |
| Dedupe | Removing repeats ("de-duplicate") |
| `ON CONFLICT DO NOTHING` | PostgreSQL: if the key exists, skip the insert instead of failing |
| Consumer group | A name Kafka uses to remember how far a listener has read |
| Offset | A record's position in a partition; the group's "bookmark" |
| `auto-offset-reset` | Where a group with no bookmark starts: `earliest` or `latest` |
| Read model | A table built from events, for reading (here: `probe_read_model`) |

## Test yourself
1. Why must the dedupe insert and the side effect be in the same transaction? What goes wrong if
   they are in two?
2. Why does `IdempotentConsumer` use `ON CONFLICT DO NOTHING` instead of catching a
   duplicate-key error?
3. In `ProbeListenerIT`, why is a third "marker" event sent after the two copies?
4. Why does `probe_read_model` have its own `id` instead of using the probe id as the key?
5. What would a brand-new consumer group miss with `auto-offset-reset: latest`?

<details><summary>Answers</summary>

1. With two transactions, a crash between them breaks things. If the mark is saved first and the
   work fails, the event is marked "done" but nothing happened (lost). If the work is saved first
   and the mark fails, the retry does the work again (doubled). In one transaction, both happen or
   neither.
2. In PostgreSQL, an error makes the whole transaction unusable until it is rolled back. "0 rows
   inserted" is a normal answer that lets the code simply skip.
3. You cannot wait for "nothing happened". Kafka keeps order per key, so when the marker's row
   appears, both copies were already processed. Only then is "1 row" a real result.
4. With the probe id as key, a second insert would fail on its own, and the test would pass even
   without the dedupe. With its own `id`, only the dedupe can stop the second row.
5. Every event sent before the group's first start. It would begin at the newest record and never
   see the older ones.
</details>

## Learn more
- [PostgreSQL: INSERT … ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT): how "do nothing on a duplicate key" works.
- [Spring Framework: Programmatic transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html): `TransactionTemplate`.
- [Spring for Apache Kafka: @KafkaListener](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html): listener methods, ids and groups.
- [Apache Kafka: consumer config `auto.offset.reset`](https://kafka.apache.org/documentation/#consumerconfigs_auto.offset.reset): earliest vs latest.
- [microservices.io: Idempotent consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html): the pattern in one page.
- Lessons in this repo: [07 Kafka](../../lessons/07-kafka.html), [08 Reliable events](../../lessons/08-outbox-idempotency.html).

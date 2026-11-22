# Task 7: Outbox spike - learning note

> Task: [task7.md](../../../tasks/platform/task7.md) · Commit: `a90e346` · Date: 2026-11-22

## In one minute
Our modules will talk to each other through Kafka events (for example, "student enrolled"). This
raises a hard question: when we save data in PostgreSQL **and** send an event to Kafka, how can
we be sure that both happen, or neither? This task answers it with an **outbox**. The event is
saved in a database table in the same transaction as the data, and it is sent to Kafka after the
commit. It is marked "done" only when Kafka says "got it". This was the riskiest choice in the
spec (decision D1), so we tested it now, with a real database and a real Kafka, before building
anything on top of it. Result: no lost events, and no events for undone changes. But an event can
arrive **twice**, so consumers must skip repeats (spec D2).

## The big picture

```
 OutboxProbeService.create()  ── one transaction ──────────────────────┐
   1. INSERT platform.outbox_probe       (the "business row")          │
   2. publishEvent(DomainEvent)  ──► Modulith writes a row in          │
                                     platform.event_publication        │
   3. COMMIT  (both rows saved, or neither) ◄───────────────────────────┘
        │
        ▼ after commit (background thread)
   4. KafkaTemplate.send(topic "platform.probe", key = probe id, value = envelope JSON)
        │
        ├─ Kafka says OK   ──► row status COMPLETED, completion_date set
        └─ no answer/error ──► row status FAILED ──► OutboxRetryJob sends it again later
```

The row status moves like this: `PUBLISHED → PROCESSING → COMPLETED`, or `→ FAILED → RESUBMITTED → …`.

## The files

### `backend/core-app/pom.xml`
- **What changed:** 3 new main dependencies and 2 new test dependencies.
- **Key lines:**
  ```xml
  <artifactId>spring-modulith-starter-jdbc</artifactId>   <!-- outbox table in PostgreSQL -->
  <artifactId>spring-modulith-events-kafka</artifactId>   <!-- sends saved events to Kafka -->
  <artifactId>spring-boot-starter-kafka</artifactId>      <!-- KafkaTemplate + spring.kafka.* -->
  <artifactId>testcontainers-kafka</artifactId>           <!-- real Kafka in Docker (tests) -->
  <artifactId>awaitility</artifactId>                     <!-- "wait until X is true" (tests) -->
  ```
- **Why:** Modulith's "Event Publication Registry" is the outbox. With the JDBC starter, it stores
  events in a table. "Externalization" is Modulith's word for "send the event out to a broker".
- **Like in npm:** adding packages to `dependencies` and `devDependencies`. Versions come from the
  Boot and Modulith BOMs, so no `<version>` is written here.

### `db/migration/platform/V202611220100__event_publication.sql`
- **What it does:** creates the outbox table `platform.event_publication`.
- **Why it exists:** the spec says Flyway owns every table, so Modulith must not create it at
  startup. We copied the official SQL from the Modulith jar
  (`schemas/v2/schema-postgresql.sql`).
- **Key lines:**
  ```sql
  serialized_event TEXT NOT NULL,          -- the event as JSON
  completion_date  TIMESTAMP WITH TIME ZONE, -- NULL = not delivered yet
  status           TEXT,                   -- PUBLISHED, PROCESSING, COMPLETED, FAILED, RESUBMITTED
  ```
  `completion_date` is the most important column. It stays empty until Kafka confirms the event.

### `db/migration/platform-probe/V202611220200__outbox_probe.sql`
- **What it does:** creates `platform.outbox_probe`, a tiny table that plays the "business data".
- **Why a separate folder:** the probe is for dev and tests only. Only the `dev` and `test` profiles
  add this folder to `spring.flyway.locations`, so a production database never gets the table.

### `platform/internal/OutboxProbe.java`
- **What it does:** a record `(id, note, createdAt)`. It is one row of `outbox_probe`, and also the
  payload of the probe event.
- **Why:** the test can compare what was saved with what reached Kafka.

### `platform/internal/OutboxProbeService.java`
- **What it does:** `create(note)` saves a row and publishes an event in **one** `@Transactional`
  method. This is the pattern every feature module will copy.
- **Key lines:**
  ```java
  @Transactional
  UUID create(String note) {
      jdbc.update("INSERT INTO platform.outbox_probe ...", ...);
      events.publishEvent(new DomainEvent<>(UUID.randomUUID(), EVENT_TYPE, 1, now,
              probe.id().toString(), probe));
      return probe.id();
  }
  ```
  `publishEvent` does **not** talk to Kafka. It only makes Modulith write a row in
  `event_publication`, using the same database connection and transaction. If the method throws
  or the transaction rolls back, both rows disappear together.
- **`@Profile({"dev", "test"})`:** the bean exists only in these profiles.

### `platform/internal/ProbeTopic.java`
- **What it does:** describes the probe topic like an entry of the shared-kernel `Topics` table: a
  name (`platform.probe`), a partition count (3), and the key rule.
- **Key lines:**
  ```java
  static RoutingTarget route(DomainEvent<?> event) {
      return RoutingTarget.forTarget(NAME).andKey(keyOf(event)); // keyOf = event.aggregateId()
  }
  ```
  A `RoutingTarget` is Modulith's answer to "where does this event go?": a topic plus a key.
- **Why the key is `aggregateId`, not a payload field:** when an event is sent *again* (after a
  failure or restart), Modulith reads it back from the JSON in the table. At that point the payload
  is a plain `Map`, not an `OutboxProbe`, because the JSON does not say which Java class it was.
  The envelope fields (`aggregateId`, `type`, …) are always typed, so they are safe to use. T12
  must remember this for keys like `studentId` that live in the payload.

### `platform/internal/OutboxProbeConfiguration.java`
- **What it does:** two beans.
  ```java
  EventExternalizationConfiguration.externalizing()
          .select(ProbeTopic::isProbeEvent)          // which events go out
          .route(DomainEvent.class, ProbeTopic::route) // to which topic, with which key
          .build();

  TopicBuilder.name(ProbeTopic.NAME).partitions(3).replicas(1).build(); // a NewTopic bean
  ```
- **Why:** without the first bean, Modulith would externalize nothing. The second bean makes
  Spring's `KafkaAdmin` create the topic at startup, because our broker does not create topics
  by itself (auto-create is off in Compose).

### `platform/internal/OutboxProbeController.java`
- **What it does:** `POST /api/v1/platform/outbox-probe?note=hi` calls `create(...)` and returns `{"id": ...}`.
- **Why:** so you can try the outbox by hand with `curl`, without writing a test.

### `platform/internal/OutboxRetryJob.java`
- **What it does:** every `campus.outbox.retry-interval` (30 s, or 2 s in tests) it resends the
  events whose delivery FAILED.
- **Key lines:**
  ```java
  @Scheduled(initialDelayString = "${campus.outbox.retry-interval}",
             fixedDelayString   = "${campus.outbox.retry-interval}")
  void resubmitFailed() { failed.resubmit(ResubmissionOptions.defaults()); }
  ```
- **Why only FAILED:** an event in PROCESSING is still waiting for Kafka's answer. Resending it
  would make a copy. `FailedEventPublications` picks only FAILED rows, and marks each one
  RESUBMITTED before sending it, so two retry rounds cannot both take the same row.
- **`@EnableScheduling`** turns on Spring's `@Scheduled` support (like a cron job inside the app).

### `src/main/resources/application.yml`
- **What changed:** outbox, Kafka, and retry settings.
- **Key lines (all profiles):**
  ```yaml
  spring.modulith.events:
    jdbc.schema: platform                        # the table lives in schema platform
    jdbc.schema-initialization.enabled: false    # Flyway creates tables, not Modulith (see Problems)
    republish-outstanding-events-on-restart: true # at startup, resend what was not delivered
    staleness: { published: 5m, processing: 5m } # stuck "in flight" too long -> FAILED
  spring.kafka.producer:
    value-serializer: ...ByteArraySerializer     # Modulith already made the JSON bytes
    acks: all                                    # Kafka answers only after storing safely
  campus.outbox.retry-interval: 30s
  ```
- **`test` profile:** short producer timeouts (`request.timeout.ms` 1 s, `delivery.timeout.ms` 8 s,
  `max.block.ms` 2 s) and a 2 s retry interval, so the tests run in about 40 seconds.
  Production keeps Kafka's defaults (for example, 120 s delivery timeout).
- **`dev` profile:** `bootstrap-servers: localhost:9092` (the Compose broker) and the probe migration folder.

### `src/test/java/edu/campus/TestcontainersConfiguration.java`
- **What changed:** a second container, Kafka (`apache/kafka:4.4.0`, the same image as Compose).
- **Why:** `@ServiceConnection` gives the broker address to Spring Boot automatically, so no
  `bootstrap-servers` is needed in the `test` profile.

### `src/test/java/edu/campus/KafkaTestConsumer.java`
- **What it does:** a test helper. `recordsWithKey(key)` reads the whole topic from the beginning and
  returns only the records with that key.
- **Why this design:** each call uses a fresh consumer with `assign(...)` (we pick the partitions
  ourselves) instead of a consumer group. So calls never affect each other, and every call sees the
  full history. "Exactly one record with key X" becomes a simple count. Each test uses its own probe
  id as the key, so tests do not see each other's records.

### `src/test/java/edu/campus/platform/internal/OutboxIT.java`
- **What it does:** the spike itself. There are 4 tests against real PostgreSQL and Kafka.
  - **O1:** create a probe. Exactly one record arrives, with key = probe id and the full envelope
    (`eventId`, `type`, `schemaVersion`, `occurredAt`, `aggregateId`, `payload`), and the row
    becomes COMPLETED.
  - **O2:** create a probe inside a transaction, then `status.setRollbackOnly()`. After waiting 2×
    the retry interval, there is no record, no probe row and no outbox row.
  - **O3, short outage:** freeze Kafka (`docker pause`) for less than the delivery timeout. The
    commit works, the row stays incomplete, and after unfreezing **exactly one** record arrives.
  - **O3, long outage:** freeze Kafka for longer than the delivery timeout. The row becomes FAILED,
    and after unfreezing it is delivered **1 or 2 times, always with the same `eventId`**.
  - In both O3 tests, the row is COMPLETED only **after** Kafka was unfrozen.
- **Key lines:**
  ```java
  // "Nothing happens during the whole wait", not just "nothing at the end":
  await().during(twoRelayIntervals()).atMost(...).until(() -> topic.recordsWithKey(id).isEmpty());

  kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec(); // freeze the broker
  ```
- **The warm-up:** before freezing, each O3 test first delivers one probe. Then the producer is
  already connected, which is the realistic and harder case.

### `src/test/java/edu/campus/platform/internal/ProbeTopicTest.java`
- **What it does:** 4 fast unit tests for `ProbeTopic`:
  - probe events go to `platform.probe` keyed by `aggregateId`;
  - the key is the same whether the payload is a record or a `Map`;
  - only probe envelopes are selected;
  - other events are refused.

### `src/test/java/edu/campus/CoreApplicationIT.java`
- **What changed:** 3 changes.
  1. The baseline check now looks for the row by version (`'202611220000'`), because there are now
     3 migrations.
  2. A new test checks that every migration succeeded.
  3. A new test, `modulithDoesNotCreateTablesItself`, checks that the bean `databaseSchemaInitializer`
     does not exist (see Problems).

### `SPEC-platform.md`, `tasks/platform/task7.md`, `tasks/todo.md`, `tasks/plan.md`
- **Spec changes:**
  - O1–O3 are ticked.
  - O3 is reworded to "at least once, one `eventId`".
  - The **D1 spike notes** were added: the settings used, what was observed, how the key is set,
    and the schema-creation trap.
- **Task files:** Task 7 and Checkpoint B are ticked, and the task got a Result paragraph.

## Decisions and why
| Decision | Options | Why we chose it |
|---|---|---|
| Keep D1 (Modulith registry), O3 = **at least once** | Switch to a hand-written outbox | The duplicate comes from "resend after a timeout". Every outbox has that, hand-written too. The real fix is consumer dedupe by `eventId` (D2). Decided by minhdau156 at Checkpoint B. |
| Key from envelope `aggregateId` for the probe | A payload field, read by reflection | Resent events come back from JSON with an untyped payload, but envelope fields are always typed. |
| Key computed in Java (`RoutingTarget...andKey(key)`) | `@Externalized("topic::#{key}")` with SpEL on the event | `DomainEvent` lives in shared-kernel, which has no Spring. Plain Java is also easier to test. |
| Retry only FAILED rows | Resend all incomplete rows older than X | Resending a row still waiting for Kafka would create copies. |
| Probe only in `dev`/`test` (bean `@Profile` + separate migration folder) | Probe in all profiles | Production should not have test tables or test endpoints. |
| Long producer timeout in production (Kafka default 120 s) | Short timeouts everywhere | An outage shorter than the delivery timeout gives no duplicate at all. |

## Problems we hit
1. **The same event reached Kafka twice (the main finding).** During a long outage, the producer gave
   up and the row became FAILED. But the frozen broker still had our request in its network buffer
   and wrote it when it woke up. Then the retry job sent the event again. The result was 2 identical
   records with the same `eventId`, seen in 2 of 2 runs. We stopped and asked. The decision was to
   keep D1 and change O3 to "at least once".
2. **Modulith created its schema although we did not ask.** The app crashed on `spring-boot:run` with
   `permission denied for database campus` on `CREATE SCHEMA IF NOT EXISTS platform`. The reason:
   in Modulith 2.1.3 the switch `schema-initialization.enabled` is `matchIfMissing = true`, so
   *missing* means ON (the docs say the default is false). The tests did not catch it because the
   Testcontainers DB user is a superuser. **Fix:** write `enabled: false` explicitly, plus a test
   that fails when the bean exists. We saw that test fail first (RED), then pass.
3. **A timing bug in our own test.** The test took the time "Kafka is back" *after* `unpause`. But the
   broker confirmed the record within about 4 ms of waking up, before that line ran. **Fix:** take
   the time *before* unpausing, because Kafka cannot confirm anything earlier than that.
4. **The old T6 test broke.** It expected exactly one Flyway row, and now there are three. It now
   looks for the baseline row by its version.
5. **Small things:**
   - `-Dit.test=...` also needs `-Dfailsafe.failIfNoSpecifiedTests=false`.
   - `spotless:check` failed on formatting, which `./mvnw spotless:apply` fixed.
   - In Git Bash, `docker exec ... /opt/kafka/...` needs `MSYS_NO_PATHCONV=1`, because Git Bash
     rewrites paths that start with `/`.

## How we checked it works
- `./mvnw -pl backend/core-app -am verify -DskipUTs` was green **3 times in a row**: 5 tests in
  `CoreApplicationIT` and 4 in `OutboxIT`, about 1 minute per run.
- `./mvnw verify` (everything) was green: kernel 81 tests, core-app 20 unit tests and 9 ITs.
- **Guard seen failing:** with the retry call switched off in `OutboxRetryJob`, the long-outage test
  failed. So the test really depends on the retry.
- **By hand, with Compose up:**
  ```bash
  cd backend/core-app && ../../mvnw spring-boot:run
  curl -X POST "localhost:8081/api/v1/platform/outbox-probe?note=hello-from-curl"
  MSYS_NO_PATHCONV=1 docker exec smart-campus-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:29092 --topic platform.probe --from-beginning \
    --property print.key=true --timeout-ms 8000
  ```
  The record had key = probe id and value = the full envelope. In psql (as `core_app`), the
  registry row was `COMPLETED`, and `flyway_schema_history` showed all 3 migrations as successful.

## Words to know
| Word | Meaning in simple words |
|---|---|
| Outbox | A table where events wait, saved in the same transaction as the data, until they are sent. |
| Event Publication Registry | Spring Modulith's outbox: the `event_publication` table plus the code that fills and updates it. |
| Externalization | Modulith's word for sending an event out of the app, to a broker like Kafka. |
| Publication | One row in the registry: one event waiting for (or done with) one listener. |
| Acknowledge (ack) | Kafka's answer "I stored your record". With `acks=all`, it is stored safely. |
| `delivery.timeout.ms` | How long the Kafka producer keeps trying one record before it reports failure. |
| Idempotent producer | A producer setting (on by default) that makes the broker drop a repeat of the *same* batch. |
| At least once | Every event arrives, but maybe more than once. Consumers must skip repeats. |
| Exactly once | Every event arrives one time. Not possible end to end when a sender resends after a timeout. |
| Rollback | Undoing a transaction: nothing it wrote is kept. |
| `docker pause` | Freezes a container's processes. It keeps its data but answers nothing. |
| Spike | A short experiment to test a risky idea before building on it. |
| Staleness | Modulith's check for rows stuck "in flight" too long. It marks them FAILED. |

## Test yourself
1. Why does `publishEvent(...)` not send to Kafka directly inside the transaction?
2. A transaction rolls back. Why can no event reach Kafka?
3. Why can the same event arrive twice, and why does switching to a hand-written outbox not fix it?
4. Why does the retry job pick only FAILED rows, and not every incomplete row?
5. Why is the probe key the envelope's `aggregateId` and not a field in the payload?

<details>
<summary>Answers</summary>

1. If we sent to Kafka inside the transaction and the commit then failed, Kafka would already have
   an event for a change that never happened. So the event is only *saved* in the transaction, and
   it is sent after the commit.
2. The event row in `event_publication` was written in the same transaction, so it is rolled back
   too. Also, the "send to Kafka" listener runs only *after a commit*, and a rollback never commits.
3. The producer may give up (timeout) on a record that the broker still writes later. Then we resend
   it, and Kafka has two copies. Any sender that resends after a timeout has this problem. The
   answer is on the consumer side: skip an `eventId` already processed (D2).
4. A PROCESSING row may still get its answer from Kafka. Resending it would create a copy. Rows stuck
   for too long are turned into FAILED by the staleness check, and then they are retried.
5. A resent event is read back from JSON, and its payload is then a `Map` with no Java class. The
   envelope fields are always typed, so reading the key from them always works.
</details>

## Learn more
- [Spring Modulith: working with application events](https://docs.spring.io/spring-modulith/reference/events.html) - the Event Publication Registry, externalization, and resubmission.
- [Spring Modulith: appendix (properties and schemas)](https://docs.spring.io/spring-modulith/reference/appendix.html) - all `spring.modulith.events.*` settings and the registry table SQL.
- [Spring for Apache Kafka reference](https://docs.spring.io/spring-kafka/reference/) - `KafkaTemplate`, `NewTopic` beans, and message converters.
- [Apache Kafka producer configs](https://kafka.apache.org/documentation/#producerconfigs) - `acks`, `delivery.timeout.ms`, `enable.idempotence`.
- [Testcontainers Kafka module](https://java.testcontainers.org/modules/kafka/) - running a real Kafka broker in tests.
- [Awaitility usage](https://github.com/awaitility/awaitility/wiki/Usage) - `await().during(...)` and `untilAsserted(...)`.

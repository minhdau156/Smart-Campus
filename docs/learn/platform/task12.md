# Task 12: Topics, keys, retention, Kafka-only rule, replay - learning note

> Task: [task12.md](../../../tasks/platform/task12.md) · Commit: `b6acf97` · Date: 2026-11-26

## In one minute
Up to now only one test topic existed (`platform.probe`). This task creates the **five real
topics** from the architecture, each with the right number of partitions and **unlimited
retention** (Kafka keeps every event forever). Every event now gets its **message key** from the
shared `Topics` table, and an event without its key is refused before anything is saved. A new
**ArchUnit rule** stops modules inside core-app from talking to each other "in-process": they must
use Kafka. Finally, `infra/replay.sh` can **rebuild a read model** by emptying its tables and
reading the topic again from the start. All of this is set now, before any feature module exists,
because changing it later would mean touching every module.

## The big picture

```
 a module: publishEvent(DomainEvent "GradePosted")        (inside the business transaction)
        │
        ├─► EventKeyCheck (starter)  ── key missing? ──► throw → whole transaction rolls back
        │
        └─► Modulith saves it in event_publication ... COMMIT ... then sends it:
               EventRouting: "GradePosted" → EventRoute(grade.events, key = payload.studentId)
               → Kafka topic grade.events (3 partitions, retention.ms=-1), key "student-42"

 Replay a read model (dev):
   stop the consumer → ./infra/replay.sh notification.probe notification.probe_read_model
     1 registry says "replayable"?  2 tables in its own schema?  3 group has 0 members?
     4 TRUNCATE tables + DELETE its processed_events rows (one transaction)
     5 reset the consumer group to offset 0
   → restart the app → it reads every event again → same rows as before
```

## The files

### `backend/shared-kernel/.../event/Topics.java` (+ `TopicsTest.java`)
- **What it does:** the table of the 5 topics (name, partitions, key field). `keyOf(event)` gives
  the Kafka key for an event. New: it also works when the payload is a `Map`.
- **Why it exists:** when Modulith **resends** an event (after a failure or restart), it reads it
  back from JSON and does not know the payload class, so the payload is a `Map`. Before this
  change, `keyOf` would fail on every resent event of a topic keyed by a payload field.
- **Key lines:**
  ```java
  if (payload instanceof Map<?, ?> map) { ... return map.get(keyField); }
  // a typed id comes back from JSON as {"value": "<uuid>"}
  if (value instanceof Map<?, ?> map && map.size() == 1 && map.get("value") != null) ...
  ```

### `backend/platform-starter/.../events/EventRoute.java`
- **What it does:** one line of the routing table: "events of type X go to topic Y, key = Z".
  `EventRoute.to(Topics.GRADE_EVENTS, "GradePosted")` uses the `Topics` key rule;
  `EventRoute.custom(...)` is for dev topics outside the table (the probe).
- **Why it exists:** each module will declare its own events as small beans, instead of one big
  central list that every module must edit.

### `backend/platform-starter/.../events/EventRouting.java` (+ `EventRoutingTest.java`)
- **What it does:** collects all routes into a map by event type. `isRouted(event)` says if an
  event goes to Kafka; `target(event)` returns Modulith's `RoutingTarget` (topic + key).
- **Why it exists:** this is the "key computed as a String" approach written down in T7.3, now
  shared by all apps. It throws when the key is null or blank (spec D6) and when two routes claim
  the same event type.

### `backend/platform-starter/.../events/EventKeyCheck.java`
- **What it does:** a plain `@EventListener` for every `DomainEvent`. If the event is routed, it
  computes the key; a missing key throws.
- **Why it exists:** Modulith computes the key only **after commit**, when it sends. A missing key
  found then would leave a saved business change whose event can never be sent. A plain
  `@EventListener` runs **inside** `publishEvent`, so the exception rolls back the whole
  transaction. The IT `anEventWithoutItsKeyFailsAndRollsBackTheChange` proves no outbox row stays.

### `backend/platform-starter/.../events/EventRoutingAutoConfiguration.java` (+ `AutoConfiguration.imports`)
- **What it does:** when an app has at least one `EventRoute` bean, it creates `EventRouting`, the
  `EventExternalizationConfiguration` that Modulith uses, and `EventKeyCheck`.
- **Key lines:**
  ```java
  @AutoConfiguration(beforeName = "org.springframework.modulith.events.config.EventExternalizationAutoConfiguration")
  @ConditionalOnBean(EventRoute.class)
  ```
  `beforeName` matters: Modulith's own default bean is `@ConditionalOnMissingBean`, so ours must
  exist first, or Modulith would use its default.
- **Like in ...:** an npm package that registers a plugin only if the app config lists one.

### `backend/platform-starter/.../events/EventTopics.java` (+ `EventTopicsTest.java`, `pom.xml`)
- **What it does:** `EventTopics.declare(Topics.GRADE_EVENTS)` builds a `NewTopic` with the
  partition count and `retention.ms=-1`. The starter now has `spring-kafka` as an optional
  dependency for `TopicBuilder`.
- **Why it exists:** both apps declare topics; one helper keeps "unlimited retention" in one place.

### `backend/core-app/.../platform/TopicConfig.java` and `backend/registration-service/.../TopicConfig.java`
- **What they do:** `NewTopic` beans. Spring's `KafkaAdmin` creates every `NewTopic` bean at
  startup. core-app: `course.events`, `grade.events`, `campus.events`. registration-service:
  `registration.requests` (12 partitions), `registration.events` (6).
- **Why they exist:** the broker never creates topics by itself (`auto.create` is off), so the
  producing app must (spec O5).

### `backend/core-app/.../internal/OutboxProbeConfiguration.java`, `ProbeTopic.java`, `ProbeTopicTest.java`
- **What changed:** the probe no longer builds its own `EventExternalizationConfiguration`; it
  declares an `EventRoute` like any real event. `ProbeTopic` keeps only name, partitions and key
  rule. The test now checks the probe through the shared `EventRouting`.
- **Why:** one routing path for all events. `OutboxIT` (O1-O3) still passes, so the outbox,
  including resends, still works through it.

### `EventKeysIT.java` (core-app and registration-service)
- **What it does:** declares test event types with `EventRoute` beans (no feature modules exist
  yet), publishes one per topic in a transaction, and reads Kafka to check the record key:
  `course.events` → sectionId, `grade.events` → studentId (not the aggregateId!),
  `campus.events` → aggregateId, `registration.requests` → sectionId, `registration.events` →
  studentId. Plus the missing-key test.

### `TopicsIT.java` (core-app and registration-service)
- **What it does:** uses Kafka's `Admin` client to describe each topic: partition count and
  `retention.ms = -1`. It also checks that an app does **not** create the other app's topics.
- **Why two files:** each app creates only its own topics, and one test cannot start both apps.
  Together they cover all 5 topics.

### Test Kafka setup: `TestcontainersConfiguration.java` (both apps), `registration-service/pom.xml`, `KafkaTestConsumer.java` (copy)
- **What changed:** the test Kafka container runs with `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`,
  like Compose, so `TopicsIT` really proves the app created the topics. registration-service got
  its first Kafka container (plus `testcontainers-kafka` and `awaitility`) and a copy of the small
  `KafkaTestConsumer` reader.

### `backend/core-app/.../architecture/KafkaOnlyCrossModuleRule.java` (+ `KafkaOnlyCrossModuleRuleTest.java`)
- **What it does:** an ArchUnit rule (spec D5). Every method with `@EventListener`,
  `@TransactionalEventListener` or `@ApplicationModuleListener` in a core-app module may only
  handle events of its **own** module. It looks at parameter types, at `DomainEvent<X>` (it checks
  `X`), and at `@EventListener(classes = ...)`. A `DomainEvent<?>` is refused: it could carry any
  module's event.
- **Why it exists:** an event delivered in-process cannot be replayed; one read from Kafka can.
  The libraries `kernel` and `starter` are not modules, so `EventKeyCheck` is allowed.
- **Key lines:**
  ```java
  method.isAnnotatedWith(EventListener.class) || method.isMetaAnnotatedWith(EventListener.class)
  ```
  `@ApplicationModuleListener` is built on `@TransactionalEventListener`, which is built on
  `@EventListener`, so "meta-annotated" catches all three.

### `backend/core-app/src/test/java/fixture/listener/**` (8 files)
- **What they are:** a fake app with modules `alpha` and `beta`. Four `BetaListensTo...` classes
  break the rule in four ways; `AlphaOwnListener` and `BetaAllowedListener` are allowed (own events,
  Spring's `ApplicationReadyEvent`). The test says the rule must fail and name exactly the four.

### `infra/consumer-registry.txt` (+ `notification-service/.../ConsumerRegistryTest.java`)
- **What it does:** one line per Kafka consumer: `<group> <schema> <replayable|non-replayable>`.
  Today: `notification.probe notification replayable`.
- **Why it exists:** `replay.sh` must know if a consumer may be replayed. A consumer that sends
  emails would re-send them all. It is a plain text file because a shell script reads it.
  `ConsumerRegistryTest` reads the class files of notification-service and fails if a
  `@KafkaListener` is missing from the file.

### `infra/replay.sh`
- **What it does:** the 6 steps in the big picture. It runs the Kafka and Postgres tools **inside
  the Compose containers** (`docker compose exec`), so nothing extra is needed on your computer.
- **Key lines:**
  ```bash
  MEMBERS="$(awk -v g="$GROUP" '$1 == g { print $NF }' <<<"$DESCRIBE")"   # #MEMBERS column
  [ "$MEMBERS" = "0" ] || die "... Stop the app first."
  ... --reset-offsets --to-earliest --all-topics --execute
  ```
  Names are checked with regular expressions before they go into SQL, so a bad argument cannot
  inject SQL.

### Docs: `SPEC-platform.md`, `tasks/platform/task12.md`, `tasks/todo.md`, `tasks/plan.md`
- O5 and O6 ticked, the "How the key is set" caveat marked as settled, the registry file added to
  the project layout, task boxes ticked.

## Decisions and why
- **Map support in `Topics.keyOf` instead of a custom deserializer.** The spec offered a
  deserializer using `EventTypes`. Reading the key from the `Map` is a few lines, needs no Jackson
  setup, and keeps the payload class out of the resend path.
- **Check the key at publish time, not only when sending.** Failing after commit would leave an
  event stuck forever; failing inside the transaction keeps data and events consistent.
- **Routes as beans (`EventRoute`), not a central list.** Each module owns its events.
- **Registry as a text file + a test**, not Java code: the shell script must read it, and the
  test stops it from drifting away from the code.
- **Replication factor left to the broker default** for the 5 topics (1 on the dev broker), so a
  later multi-broker setup does not need a code change.
- **The probe topic stays as it was** (default retention). It is a dev topic, and KafkaAdmin does
  not change settings of a topic that already exists.

## Problems we hit
- **Stale class files from the IDE.** The editor's background compiler wrote broken class files
  ("Unresolved compilation problems") while Maven was building. Fixed with `./mvnw clean`.
- **Spring's component scan skipped `@Profile` classes.** `ConsumerRegistryTest` first used
  `ClassPathScanningCandidateComponentProvider`, which applies `@Profile("dev","test")` and so
  missed `ProbeListener`. Fixed by reading class metadata directly.
- **Git Bash path conversion.** On Windows, Git Bash turned `/opt/kafka/bin/...` into
  `C:/Program Files/Git/opt/...` before `docker exec`. The script now sets `MSYS_NO_PATHCONV=1`,
  and takes the repo path with `pwd -W` (Windows form) so the Compose file is still found.
- **A force-killed consumer still counts as "running" for about 45 s.** It never said goodbye to
  Kafka, so its member stays until the session times out. The script correctly refused; after the
  timeout it ran.
- **The read model had 2 old rows from T11.** So the replay rebuilt 12 rows, not 10: the 10 new
  probes plus the 2 older ones. That is correct, since replay reads the whole topic.

## How we checked it works
- `./mvnw verify` → BUILD SUCCESS for all 6 modules. New tests: `TopicsTest` (+3),
  `EventRoutingTest` (7), `EventTopicsTest` (5), `KafkaOnlyCrossModuleRuleTest` (2),
  `ConsumerRegistryTest` (3), `EventKeysIT` (4 in core-app, 3 in registration),
  `TopicsIT` (2 + 2).
- Every new test was seen failing first (RED). For the ArchUnit rule, the rule was broken on
  purpose (ignore the `X` in `DomainEvent<X>`): the fixture test failed on
  `BetaListensToAlphaEnvelope`, then passed again after restoring it.
- `replay.sh` refusals checked by hand: non-replayable, unknown group, wrong schema,
  `processed_events`, bad table name, bad group name, wrong argument count. All exit 1.
- **Manual replay (12.7):** started Postgres + Kafka, core-app and notification-service (dev),
  `POST /outbox-probe` 10 times → 12 rows. `replay.sh` while running → refused ("1 member").
  Stopped the service → `replay.sh` → 0 rows, offsets 0 on all 3 partitions, "Now restart the
  app". Restarted → 12 rows, `diff` of sorted probe ids before/after = identical, 0 duplicates,
  all 10 new probes present.

## Words to know
| Word | Meaning in simple words |
|---|---|
| Partition | One of the parallel "lanes" of a topic. Order is kept only inside one lane. |
| Message key | A label on a Kafka record. Records with the same key go to the same partition, so they stay in order. |
| Retention (`retention.ms`) | How long Kafka keeps records. `-1` = forever. |
| `NewTopic` bean | A Spring bean that says "this topic must exist"; `KafkaAdmin` creates it at startup. |
| Externalization | Modulith sending an in-app event out to Kafka after the commit. |
| `RoutingTarget` | Modulith's answer to "where does this event go": topic plus key. |
| In-process listener | A method called directly inside the same app when an event is published (`@EventListener`). |
| ArchUnit | A library that checks code structure rules in a unit test. |
| Fixture | Small fake code made only so a test has something to check. |
| Consumer group | The name under which Kafka remembers how far a consumer has read (its offsets). |
| Offset | The position of a record in a partition. Resetting to "earliest" = read again from the start. |
| Replay | Rebuild a read model by emptying it and reading all events again. |
| Read model | A table built only from events, so it can always be thrown away and rebuilt. |

## Test yourself
1. Why does `Topics.keyOf` need to handle a `Map` payload at all?
2. Why does the key check run in an `@EventListener` instead of only in the routing function?
3. Why must a consumer that sends emails be "non-replayable"?
4. Why does the rule refuse a listener on `DomainEvent<?>` but allow one on `DomainEvent<BetaChanged>` in module beta?
5. Why does `replay.sh` require the consumer to be stopped before it empties the tables?

<details>
<summary>Answers</summary>

1. When Modulith resends an event, it reads it back from its JSON registry without knowing the
   payload class, so the payload is a `Map`. Without Map support, every resent event on a topic
   keyed by a payload field would fail.
2. The routing function runs after commit. A failure there leaves a saved change with an event that
   can never be sent. The listener runs inside `publishEvent`, so the error rolls back everything.
3. Replay reads every old event again. A read model just gets the same rows back, but an email
   consumer would send every old email again, and emails cannot be taken back.
4. With `<?>` the rule cannot know whose event it is: it could be another module's. With
   `<BetaChanged>` the payload type is named and belongs to beta itself.
5. A running consumer would keep writing rows while we delete them, and Kafka refuses to reset the
   offsets of a group that still has members.
</details>

## Learn more
- [Spring Modulith: Externalizing events](https://docs.spring.io/spring-modulith/reference/events.html#externalization) - routing, keys, `EventExternalizationConfiguration`.
- [Spring for Apache Kafka: Configuring topics](https://docs.spring.io/spring-kafka/reference/kafka/configuring-topics.html) - `NewTopic` beans, `TopicBuilder`, `KafkaAdmin`.
- [Apache Kafka: topic configs (`retention.ms`)](https://kafka.apache.org/documentation/#topicconfigs_retention.ms) - what `-1` means.
- [Apache Kafka: consumer groups tool](https://kafka.apache.org/documentation/#basic_ops_consumer_group) - `--describe` and `--reset-offsets`.
- [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html) - custom conditions and meta-annotations.

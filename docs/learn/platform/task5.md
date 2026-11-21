# Task 5: `shared-kernel`: event envelope and topics - learning note

> Task: [task5.md](../../../tasks/platform/task5.md) · Commit: `312f2b2` · Date: 2026-11-21

## In one minute
Our services will talk to each other by sending **events** (messages like "a student enrolled")
through **Kafka** (a message system). Before anyone sends an event, everyone must agree on two
things: the **format** of an event, and the **list of topics** (channels) with the rule for each
topic's **key**. This task writes both down in code, in `shared-kernel`, so every service uses the
same ones. A golden JSON file and a table test make sure nobody changes them by accident.

## The files

### `backend/shared-kernel/src/main/java/edu/campus/kernel/event/DomainEvent.java`
- **What it does:** the **envelope** around every event. The envelope is like a letter's envelope:
  the same outside for every letter, with a different letter (the **payload**) inside.
- **Why it exists:** a consumer must always find the same fields: an id to skip duplicates, a type
  to know what happened, a version, a time, and what changed. Without one shared shape, each team
  would invent its own and services could not read each other's events.
- **Key lines:**
  ```java
  public record DomainEvent<P>(
          UUID eventId, String type, int schemaVersion, Instant occurredAt, String aggregateId, P payload) {
      public DomainEvent {
          Objects.requireNonNull(eventId, "eventId");
          ...
          if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1, ...");
      }
  }
  ```
  - `<P>` is a **generic**: the payload can be any type (`DomainEvent<EnrollmentConfirmed>`).
  - `eventId`: consumers remember it and skip an event they have already handled (spec D2).
  - `schemaVersion`: when the payload changes in a breaking way, the number goes up (spec D4).
  - `aggregateId`: the thing that changed, e.g. `"section-1234"`. It is **not** always the Kafka
    key (see `Topics`).
  - The **compact constructor** (the block without parameters) checks the values. A bad event
    cannot even be created, so it can never be sent.

### `backend/shared-kernel/src/main/java/edu/campus/kernel/event/EventTypes.java`
- **What it does:** a small **registry** (a lookup table) from a type name to a payload class:
  `"EnrollmentConfirmed"` → `EnrollmentConfirmed.class`.
- **Why it exists:** JSON has no Java classes in it. When a consumer reads
  `"type": "EnrollmentConfirmed"`, it needs to know which class to build for `payload`. If two
  classes used the same name, the reader could pick the wrong one, so a duplicate fails at once.
- **Key lines:**
  ```java
  Class<?> existing = byType.putIfAbsent(type, payloadClass);
  if (existing != null) {
      throw new IllegalStateException("Event type '%s' is already registered to %s" ...);
  }
  ```
  `putIfAbsent` adds the entry only if the name is new, and returns the old class if it was
  already there. It is one step on a `ConcurrentHashMap`, so it stays correct if two threads
  register at the same moment.
- **Like in plain Java:** a `Map<String, Class<?>>` with a rule "no overwrite".

### `backend/shared-kernel/src/main/java/edu/campus/kernel/event/Topics.java`
- **What it does:** an `enum` with the 5 Kafka topics from the architecture document (Arch §6).
  Each has a name, a **partition** count, a **key field** and a `replayable` flag:

  | Topic | Partitions | Key | Replayable |
  |---|---|---|---|
  | `registration.requests` | 12 | sectionId | no |
  | `registration.events` | 6 | studentId | yes |
  | `course.events` | 3 | sectionId | yes |
  | `grade.events` | 3 | studentId | yes |
  | `campus.events` | 3 | aggregateId | yes |

- **Why it exists:** Kafka keeps messages **in order only inside one partition**, and the key
  decides the partition. Same key → same partition → same order. So the key decides *what* stays
  in order. For seat requests we need order **per section** (a fair waitlist), so the key is
  `sectionId`. For a student's grades we need order **per student**, so the key is `studentId`.
  If every service picked its own key, the order guarantee would break (spec D6).
- **Key lines:**
  ```java
  public String keyOf(DomainEvent<?> event) {
      if (keyField.equals(AGGREGATE_ID)) {
          return event.aggregateId();
      }
      Object value = readPayloadField(event.payload());
      if (value == null) throw new IllegalArgumentException("Topic %s needs a key, ...");
      return value instanceof TypedId id ? id.value().toString() : value.toString();
  }
  ```
  - `"aggregateId"` means "use the envelope's `aggregateId`". Any other name is a field of the
    payload **record**, read with Java **reflection** (`getRecordComponents()`), which is code
    that looks at a class while the program runs.
  - A missing field or a `null` value **throws**: the spec says publishing without a key must fail.
  - Here the `TypedId` interface from Task 4 is used for the first time: a `SectionId` key
    becomes its UUID text, not `SectionId[value=...]`.
  - `replayable = false` for `registration.requests`: it carries **commands** ("give me a seat").
    Reading them again would act twice. Events ("a seat was given") can be read again to rebuild
    a view (spec D5).

### `backend/shared-kernel/src/test/resources/golden/domain-event.json`
- **What it does:** a **golden file**: the one correct example of an event as JSON.
- **Why it exists:** the JSON is a contract between services. If someone renames a field (for
  example `aggregateId` → `aggregate_id`), other services stop understanding the events. The test
  compares against this file, so such a change fails the build.
- **Key lines:**
  ```json
  "schemaVersion": 1,
  "occurredAt": "2026-09-07T08:00:00Z",
  ```
  The time is **ISO-8601** text in **UTC** (the `Z` at the end), not a number.

### `backend/shared-kernel/src/test/java/edu/campus/kernel/event/DomainEventJsonTest.java`
- **What it does:** 4 tests with **Jackson 3** (the JSON library; its new package name is
  `tools.jackson`): the event turns into exactly the golden JSON; `occurredAt` is ISO-8601 UTC
  text; the golden file reads back into an equal event; and a write-then-read **round trip** gives
  the same event.
- **Why it exists:** proves the contract both ways (writing and reading).
- **Key lines:**
  ```java
  Class<?> payloadClass = types.resolve(mapper.readTree(json).get("type").asString());
  JavaType eventType = mapper.getTypeFactory().constructParametricType(DomainEvent.class, payloadClass);
  DomainEvent<?> read = mapper.readValue(json, eventType);
  ```
  This is how a real consumer will read events: look at `type`, ask `EventTypes` for the class,
  then tell Jackson "this is a `DomainEvent` of that class".

### `backend/shared-kernel/src/test/java/edu/campus/kernel/event/DomainEventTest.java`, `EventTypesTest.java`
- **What they do:** check the rules: each `null` field is rejected, `schemaVersion` 0 and -1 are
  rejected, a blank type or aggregateId is rejected; a registered type resolves, a duplicate
  fails, an unknown type fails.
- **Why they exist:** the "Done when" says invalid envelopes are rejected at construction.

### `backend/shared-kernel/src/test/java/edu/campus/kernel/event/TopicsTest.java`
- **What it does:** a **table test** (`@CsvSource`): one row per topic, the same as the Arch §6
  table. It also checks there are exactly 5 topics, and tests `keyOf`: key by student (not by
  aggregate), a typed-id key, key by aggregateId, and the 3 failure cases.
- **Key lines:**
  ```java
  @CsvSource({
      "REGISTRATION_REQUESTS, registration.requests, 12, sectionId,   false",
      "REGISTRATION_EVENTS,   registration.events,    6, studentId,   true",
      ...
  })
  ```
  JUnit turns the first column into the enum constant by its name, and runs the test once per row.

### `backend/shared-kernel/pom.xml`
- **What changed:** one test-only dependency:
  ```xml
  <dependency>
      <groupId>tools.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <scope>test</scope>
  </dependency>
  ```
  No `<version>`: the Spring Boot parent already manages Jackson (3.1.8). `<scope>test</scope>`
  means Jackson is on the classpath only for tests. `shared-kernel` itself stays plain Java.
- **Like in npm:** a `devDependency`.

### `tasks/platform/task5.md`, `tasks/todo.md`, `tasks/plan.md`
- Boxes ticked and a "Result" paragraph added, as for every task.

## Decisions and why
| Decision | Options | Why |
|---|---|---|
| `Topics` is an `enum` | enum, class with constants, config file | A fixed list known at compile time. `values()` lets a test check "exactly 5". |
| Key is a **field name** | a Java function per topic, a marker interface per key | A name can also be used later by the Kafka setup (T12, e.g. as an expression `payload.sectionId`). It works for any payload record without extra interfaces. |
| `registration.requests` not replayable | all replayable | It holds commands; running them twice would give seats twice. |
| `EventTypes` is an object, not static | one global static map | Tests and apps each make their own; no hidden shared state between tests. |
| Also reject blank `type` / `aggregateId` | only nulls (as in the spec code) | An empty string is as useless as `null`. The fields do not change, so the envelope contract is the same. |
| Jackson only in tests | Jackson in main code with annotations | The kernel must stay framework-free. Jackson 3 handles records and `Instant` without annotations. |

## Problems we hit
- Writing the test files with one long shell **heredoc** failed (a quoting error in the shell).
  Fixed by writing each file with the editor tool instead.
- The first `Topics` draft used an odd nested class to hold the `"aggregateId"` name, because I
  thought the enum constants could not use it. Only the constant *arguments* have that limit;
  methods can read a `static final` field normally. So it became a plain literal in the constant
  plus a `private static final String AGGREGATE_ID` for `keyOf`.
- Python is not installed on this machine, so a small clean-up script could not run; the edits
  were made with the editor tool.

## How we checked it works
- **RED:** tests written first; `./mvnw -pl backend/shared-kernel test` failed with "cannot find
  symbol `DomainEvent` / `EventTypes` / `Topics`".
- **GREEN:** `./mvnw clean verify` → 81 unit tests + 1 IT pass, `BUILD SUCCESS`, Spotless clean,
  "All coverage checks have been met". Line coverage **97.6%** (122 of 125). Also green with
  `mvnw.cmd` in PowerShell.
- **The guards really guard:** renaming `aggregateId` to `aggregate_id` in the golden file made
  `serializesToTheGoldenShape` fail; changing `grade.events` to 4 partitions made row 4 of the
  table test fail. Both were then put back.
- `grep Instant.now` in main code: nothing found.

## Words to know
| Word | Meaning in simple words |
|---|---|
| Event | A message that says something already happened ("EnrollmentConfirmed"). |
| Envelope | The fixed outer part of every event (id, type, version, time, aggregateId). |
| Payload | The event-specific data inside the envelope. |
| Topic | A named channel in Kafka where events are written and read. |
| Partition | One slice of a topic. Order is kept only inside one partition. |
| Message key | A value that picks the partition. Same key → same partition → same order. |
| Golden file | A saved "correct answer" the test compares against. |
| Round trip | Write to JSON, read back, and check you get the same object. |
| Reflection | Code that inspects classes (their fields, methods) while the program runs. |
| Replay | Reading a topic again from the start to rebuild data. |

## Test yourself
1. Why is the key of `registration.events` `studentId` and not the `aggregateId`?
2. What would go wrong if two payload classes were registered under the same type name?
3. Why does `keyOf` throw when the key value is `null`, instead of sending with no key?
4. Why is `registration.requests` not replayable, but `grade.events` is?
5. Why is Jackson only a `test` dependency of `shared-kernel`?

<details><summary>Answers</summary>

1. Kafka keeps order per key. Consumers of registration events care about the order of events
   **for one student** (e.g. enrolled, then dropped). The aggregate may be a section, which would
   mix students together and give no order per student.
2. A consumer reading `"type": "X"` could build the wrong class, and fail or read wrong data.
   Failing at registration finds the mistake at startup, not in production traffic.
3. With no key, Kafka spreads messages over partitions, so the order guarantee is lost silently.
   Failing loudly is safer (spec D6).
4. A request is a command; reading it again would do the action again (give a seat twice). An
   event is a fact; reading it again just rebuilds the same view.
5. The main code needs no Jackson to work. Tests use it to prove the JSON shape. Keeping it out of
   main code keeps the kernel small and framework-free; apps bring their own Jackson.

</details>

## Learn more
- [Kafka: main concepts and terms](https://kafka.apache.org/documentation/#intro_concepts_and_terms) - topics, partitions, and why keys keep order.
- [Java records (Java 21 guide)](https://docs.oracle.com/en/java/javase/21/language/records.html) - records and compact constructors.
- [`RecordComponent` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/RecordComponent.html) - how `Topics` reads a payload field by name.
- [`ConcurrentHashMap.putIfAbsent`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) - the safe "add only if new" used by `EventTypes`.
- [JUnit 5 parameterized tests](https://docs.junit.org/current/user-guide/#writing-tests-parameterized-tests) - `@CsvSource` and `@ValueSource`.
- [Jackson databind](https://github.com/FasterXML/jackson-databind) - the JSON library used in the golden test.

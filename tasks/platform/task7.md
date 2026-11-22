# T7: Outbox spike ⚠ highest risk

> Module `platform` · Phase 2: First app + outbox risk spike · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T6](task6.md) · [T8 →](task8.md)

`Scope M · Depends on: T6 · Closes: O1, O2, O3`

**Goal:** prove that an event is never lost and never sent for a rolled-back change before
anything else is built on top of it.

**Subtasks**
- [x] **7.1** Add the Spring Modulith JDBC event-publication registry and Kafka externalization
  dependencies. Create the registry table with a Flyway migration in schema `platform` (no
  automatic schema creation).
- [x] **7.2** Add a dev-only probe:
  - a migration for the `outbox_probe` table;
  - `OutboxProbeService.create()`, which saves a row and publishes a `DomainEvent` in one `@Transactional` method.
- [x] **7.3** Configure externalization: the probe event goes to the dev-only topic
  `platform.probe`, with its key taken from a `Topics`-style definition. **Write down how the key
  is set**, because T12 depends on it.
- [x] **7.4** Set up the IT base: Testcontainers Postgres + Kafka via `@ServiceConnection`, plus a
  test Kafka consumer helper that collects records.
- [x] **7.5** **O1 test:** create a probe → exactly one Kafka record with the full envelope and the expected key.
- [x] **7.6** **O2 test:** the transaction rolls back → no record appears, after waiting at least 2× the relay interval.
- [x] **7.7** **O3 test** *(revised at Checkpoint B, see spec O3)*:
  - pause the Kafka container, then create a probe (the commit succeeds);
  - assert the publication is still *incomplete* in the registry;
  - unpause Kafka, then trigger resubmission or restart;
  - assert the record is delivered and only then marked complete: **exactly once** for an outage
    shorter than `delivery.timeout.ms`, **at least once with one `eventId`** for a longer outage.
- [x] **7.8** Run the IT suite 3 times in a row. Append the spike notes (settings used, what was
  observed, how the key is set) to spec D1.

**Done when**
- [x] O1, O2 and O3 each have a green IT
- [x] The O3 test *asserts* that completion happens only after the broker acknowledges
- [x] Spike notes are in spec D1

**Verify**
- [x] `./mvnw -pl backend/core-app -am verify -DskipUTs` is green 3 times in a row

**Result (2026-11-22):** `OutboxIT` has 4 tests: O1, O2, O3 short outage, O3 long outage.
`CoreApplicationIT` now has 5 (baseline row found by version, all migrations succeeded, Modulith
does not create tables). Full `./mvnw verify`: kernel 81 + core-app 20 unit tests + 9 ITs.
The IT suite was green 3 times in a row (each run about 1 min).
**Found 1, a duplicate:** a broker outage longer than `delivery.timeout.ms` delivered the event twice
(same `eventId`). The frozen broker wrote the request waiting in its network buffer, and the
retry job sent it again. Any outbox that resends after a timeout has this limit, so **D1 is kept** and spec
O3 is now "at least once, one `eventId`"; consumers drop repeats (D2/O4).
**Found 2, a schema-creation trap:** Modulith 2.1.3 creates its schema unless
`schema-initialization.enabled: false` is written out (`matchIfMissing = true`). This was seen only on
`spring-boot:run` as `core_app` and is now guarded by a test.
**Found 3, a test timing bug:** the broker can acknowledge within ms of `unpause`, so "Kafka back" is
timed *before* unpausing.
**Guard seen failing:** with `OutboxRetryJob` switched off, the long-outage test fails.
**Key:** an `EventExternalizationConfiguration` routes with
`RoutingTarget.forTarget(topic).andKey(key)`. The probe key is `aggregateId`, because events that
are resent are read back from JSON with an untyped payload (details in spec D1 notes).
**Manual check:** with Compose up, `spring-boot:run` started, then
`POST /api/v1/platform/outbox-probe?note=hello-from-curl` was sent. `kafka-console-consumer` showed key = probe id
and value = full envelope, and the registry row was `COMPLETED`.

**Files:** `core-app/…/platform/internal/OutboxProbe.java`, `…/OutboxProbeService.java`, `…/ProbeTopic.java`,
`…/OutboxProbeConfiguration.java`, `…/OutboxProbeController.java`, `…/OutboxRetryJob.java`,
`application.yml`, `db/migration/platform/V202611220100__event_publication.sql`,
`db/migration/platform-probe/V202611220200__outbox_probe.sql`, `OutboxIT.java`, `ProbeTopicTest.java`,
`KafkaTestConsumer.java`, `TestcontainersConfiguration.java`, `CoreApplicationIT.java`, `pom.xml`

---

## ◆ Checkpoint B: D1 decision gate
- [x] If O1–O3 are green, **keep D1**. If any test fails, **stop**: switch spec D1 to a hand-written outbox and re-plan T7
  *(O3 "exactly once" failed for long outages; minhdau156 decided 2026-11-22: keep D1, O3 = at least once)*
- [x] 🙋 **Human review** of the spike result before Phase 3 *(reviewed by minhdau156, 2026-11-22)*

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

# T7: Outbox spike ⚠ highest risk

> Module `platform` · Phase 2: First app + outbox risk spike · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T6](task6.md) · [T8 →](task8.md)

`Scope M · Depends on: T6 · Closes: O1, O2, O3`

**Goal:** prove that an event is never lost and never sent for a rolled-back change before
anything else is built on top of it.

**Subtasks**
- [ ] **7.1** Add the Spring Modulith JDBC event-publication registry and Kafka externalization
  dependencies. Create the registry table with a Flyway migration in schema `platform` (no
  automatic schema creation).
- [ ] **7.2** Add a dev-only probe:
  - a migration for the `outbox_probe` table;
  - `OutboxProbeService.create()`, which saves a row and publishes a `DomainEvent` in one `@Transactional` method.
- [ ] **7.3** Configure externalization: the probe event goes to the dev-only topic
  `platform.probe`, with its key taken from a `Topics`-style definition. **Write down how the key
  is set**, because T12 depends on it.
- [ ] **7.4** Set up the IT base: Testcontainers Postgres + Kafka via `@ServiceConnection`, plus a
  test Kafka consumer helper that collects records.
- [ ] **7.5** **O1 test:** create a probe → exactly one Kafka record with the full envelope and the expected key.
- [ ] **7.6** **O2 test:** the transaction rolls back → no record appears, after waiting at least 2× the relay interval.
- [ ] **7.7** **O3 test:**
  - pause the Kafka container, then create a probe (the commit succeeds);
  - assert the publication is still *incomplete* in the registry;
  - unpause Kafka, then trigger resubmission or restart;
  - assert the record is delivered exactly once and only then marked complete.
- [ ] **7.8** Run the IT suite 3 times in a row. Append the spike notes (settings used, what was
  observed, how the key is set) to spec D1.

**Done when**
- [ ] O1, O2 and O3 each have a green IT
- [ ] The O3 test *asserts* that completion happens only after the broker acknowledges
- [ ] Spike notes are in spec D1

**Verify**
- [ ] `./gradlew :backend:core-app:integrationTest` is green 3 times in a row

**Files:** `core-app/…/platform/internal/OutboxProbe.java`, `…/OutboxProbeService.java`, `application.yml`, `db/migration/platform/V…__outbox.sql`, `OutboxIT.java`

---

## ◆ Checkpoint B: D1 decision gate
- [ ] If O1–O3 are green, **keep D1**. If any test fails, **stop**: switch spec D1 to a hand-written outbox and re-plan T7
- [ ] 🙋 **Human review** of the spike result before Phase 3

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

# T5: `shared-kernel`: event envelope and topics

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T4](task4.md) · [T6 →](task6.md)

`Scope M · Depends on: T4 · Closes: —`

**Goal:** one agreed event format and one agreed list of topics (with their keys) that every service uses.

**Subtasks**
- [ ] **5.1** Write the `DomainEvent<P>` record (`eventId, type, schemaVersion, occurredAt, aggregateId, payload`), with validation that rejects nulls and `schemaVersion < 1`. Add tests.
- [ ] **5.2** Write the `EventTypes` registry: register a type name → payload class, resolve it, and fail on a duplicate. Add tests.
- [ ] **5.3** Write `Topics`, with one entry per Arch §6 topic. Each entry has a name, a partition count, a key extractor and a `replayable` flag:

  | Topic | Partitions | Key |
  |---|---|---|
  | `registration.requests` | 12 | sectionId |
  | `registration.events` | 6 | studentId |
  | `course.events` | 3 | sectionId |
  | `grade.events` | 3 | studentId |
  | `campus.events` | 3 | aggregateId |

- [ ] **5.4** Add a golden file `domain-event.json` and a round-trip test using Jackson 3 (`tools.jackson`, test scope only). `occurredAt` must be ISO-8601 UTC.
- [ ] **5.5** Add a table test asserting `Topics` matches the table in 5.3 exactly. Confirm coverage is still ≥ 90%.

**Done when**
- [ ] The JSON field names and format match the golden file (Arch §6 envelope + `schemaVersion`)
- [ ] `Topics` contains exactly those 5 topics with those partitions and keys
- [ ] Invalid envelopes are rejected at construction

**Verify**
- [ ] `./gradlew :backend:shared-kernel:build` passes

**Files:** `kernel/event/DomainEvent.java`, `kernel/event/EventTypes.java`, `kernel/event/Topics.java`, `DomainEventJsonTest.java`, `src/test/resources/golden/domain-event.json`, `TopicsTest.java`

---

## ◆ Checkpoint A: Foundation
- [ ] `./gradlew build` is green on Windows from `C:\Smart Campus`
- [ ] Compose infra is healthy, and the DB permission check passes
- [ ] 🙋 **Human review** of the pinned versions (T1.1) before any app code is written

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

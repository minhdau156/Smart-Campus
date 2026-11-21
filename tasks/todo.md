# Tasks: `platform` module

> Spec: [SPEC-platform.md](../SPEC-platform.md) · Plan: [plan.md](plan.md)
> **Task details live in [`tasks/platform/`](platform/), one file per task.** This page is the index
> and the progress tracker.

## How to use this checklist

- Open a task file. Each one has the same 5 parts: **Goal** (one sentence), **Subtasks** (do them in
  order and tick each one), **Done when** (the acceptance criteria), **Verify** (how to prove it),
  and **Files**. Each file also has links to the previous and next task.
- Tick subtasks **in the task file**. When every **Done when** and **Verify** box there is ticked,
  tick the task here.
- A **◆ Checkpoint** is a stop: show the result to the human and wait for an OK before the next
  phase. Each checkpoint is at the bottom of the task file just before it.
- ⚠ = high risk. 🙋 = needs a human decision or action before that subtask can run.
- **Building:** implementation runs only when the human runs `/agent-skills:build`. Each run does
  **one subtask** (the next unticked one), verifies it, ticks only that box, and stops.

**Definition of Done (every task):**
`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that the task closes are ticked.

## Progress

### Phase 1: Foundation
- [x] [T1](platform/task1.md): Maven parent pom, wrapper, pinned versions (7 subtasks)
- [x] [T2](platform/task2.md): Build rules in the parent pom (7)
- [x] [T3](platform/task3.md): Compose core infra, schemas, DB users (8) · closes I1, I2 (infra) · *parallel with T1–T2*
- [x] [T4](platform/task4.md): `shared-kernel`: ids, money, time, problem codes (6)
- [ ] [T5](platform/task5.md): `shared-kernel`: event envelope and topics (5)
- [ ] ◆ **Checkpoint A: Foundation** (in [task5.md](platform/task5.md)) 🙋

### Phase 2: First app + outbox risk spike
- [ ] [T6](platform/task6.md): `core-app` skeleton (8) · closes P3
- [ ] [T7](platform/task7.md) ⚠: Outbox spike (8) · closes O1, O2, O3
- [ ] ◆ **Checkpoint B: D1 decision gate** (in [task7.md](platform/task7.md)) 🙋

### Phase 3: Services, errors, events, routing, containers
- [ ] [T8](platform/task8.md): Problem JSON handler (7) · closes E1
- [ ] [T9](platform/task9.md): OpenAPI docs, backend part (5) · closes A1 (backend)
- [ ] [T10](platform/task10.md): `registration-service` skeleton (6) · closes I2 (D3 part)
- [ ] [T11](platform/task11.md): `notification-service` + idempotent consumer (6) · closes O4
- [ ] [T12](platform/task12.md): Topics, keys, retention, Kafka-only rule, replay (7) · closes O5, O6
- [ ] [T13](platform/task13.md): Gateway routes + smoke script (6) · closes I3
- [ ] [T14](platform/task14.md): Gateway per-IP rate limiting (5) · closes R1
- [ ] [T15](platform/task15.md): App container images + `apps` profile (6) · closes K1
- [ ] ◆ **Checkpoint C: Platform runs end to end** (in [task15.md](platform/task15.md)) · closes P1 🙋

### Phase 4: Observability
- [ ] [T16](platform/task16.md): Traces (7) · closes B1
- [ ] [T17](platform/task17.md): JSON logs → Loki (5) · closes B2
- [ ] [T18](platform/task18.md): Metrics, dashboard, alerts (6) · closes B3 · 🙋 at 18.3
- [ ] ◆ **Checkpoint D: Observability** (in [task18.md](platform/task18.md)) 🙋

### Phase 5: Web scaffold, CI, handover
- [ ] [T19](platform/task19.md): Web scaffold + generated API types (7) · closes P2, A1 (frontend)
- [ ] [T20](platform/task20.md): GitHub Actions CI (7) · closes C1 · 🙋 at 20.5, 20.7
- [ ] [T21](platform/task21.md): README + ADRs (6) · closes H1, H2
- [ ] ◆ **Checkpoint E: `platform` done** (in [task21.md](platform/task21.md)) 🙋

**Total:** 21 tasks · 141 subtasks · 5 checkpoints

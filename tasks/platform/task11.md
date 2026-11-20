# T11: `notification-service` skeleton + idempotent consumer

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T10](task10.md) · [T12 →](task12.md)

`Scope M · Depends on: T9 · Closes: O4`

**Goal:** the Notification service runs, and receiving the same event twice has the effect of receiving it once.

**Subtasks**
- [ ] **11.1** Create `backend/notification-service` on port 8083, with datasource user `notification_svc` and Flyway schema `notification`.
- [ ] **11.2** Add a migration for `processed_events(consumer, event_id, processed_at, PK(consumer, event_id))` and a `probe_read_model` table.
- [ ] **11.3** Add `IdempotentConsumer` to the starter. In **one transaction** it inserts into
  `processed_events`, and if the key is a duplicate it skips; otherwise it runs the side effect.
- [ ] **11.4** Write a `ProbeListener` (`@KafkaListener` on `platform.probe`) that uses `IdempotentConsumer` to insert one probe row.
- [ ] **11.5** Write ITs:
  - the same event delivered twice → 1 row;
  - an error injected after the dedup insert → both inserts rolled back, and a retry succeeds.
- [ ] **11.6** Add a schema-isolation IT (same as 10.5). Manual end to end: create a probe on core-app → one row appears in `notification`.

**Done when**
- [ ] Duplicate delivery causes exactly one side effect (O4)
- [ ] The dedup record and the side effect commit or roll back together
- [ ] The isolation IT passes

**Verify**
- [ ] `./gradlew :backend:notification-service:build` passes

**Files:** `platform-starter/…/IdempotentConsumer.java`, `notification-service/build.gradle.kts`, `NotificationApplication.java`, `ProbeListener.java`, `V…__processed_events.sql`, `IdempotentConsumerIT.java`

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

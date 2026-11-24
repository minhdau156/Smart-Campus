# T11: `notification-service` skeleton + idempotent consumer

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T10](task10.md) · [T12 →](task12.md)

`Scope M · Depends on: T9 · Closes: O4`

**Goal:** the Notification service runs, and receiving the same event twice has the effect of receiving it once.

**Subtasks**
- [x] **11.1** Create `backend/notification-service` on port 8083, with datasource user `notification_svc` and Flyway schema `notification`.
- [x] **11.2** Add a migration for `processed_events(consumer, event_id, processed_at, PK(consumer, event_id))` and a `probe_read_model` table.
- [x] **11.3** Add `IdempotentConsumer` to the starter. In **one transaction** it inserts into
  `processed_events`, and if the key is a duplicate it skips; otherwise it runs the side effect.
- [x] **11.4** Write a `ProbeListener` (`@KafkaListener` on `platform.probe`) that uses `IdempotentConsumer` to insert one probe row.
- [x] **11.5** Write ITs:
  - the same event delivered twice → 1 row;
  - an error injected after the dedup insert → both inserts rolled back, and a retry succeeds.
- [x] **11.6** Add a schema-isolation IT (same as 10.5). Manual end to end: create a probe on core-app → one row appears in `notification`.

**Done when**
- [x] Duplicate delivery causes exactly one side effect (O4)
- [x] The dedup record and the side effect commit or roll back together
- [x] The isolation IT passes

**Verify**
- [x] `./mvnw -pl backend/notification-service -am verify` passes

**Result (2026-11-24):** starter: `edu.campus.starter.consumer.IdempotentConsumer` (plain class, no
auto-configuration; optional `spring-jdbc` dependency). Built per schema
(`new IdempotentConsumer(jdbc, txManager, "notification")`), because D2 puts `processed_events` in
each *consuming module's* schema, so one app-wide bean would point at the wrong schema in core-app.
`runOnce(consumer, eventId, sideEffect)`: one `TransactionTemplate` with
`INSERT … (consumer, event_id) … ON CONFLICT DO NOTHING` then the side effect; 0 rows = repeat →
skip, returns false. The schema name must match `[a-z][a-z0-9_]*` (it is part of the SQL). New
module `backend/notification-service` (port 8083, user `notification_svc`, Flyway schema
`notification`): ping, health, OpenAPI export (`docs/api/notification-service.json`), its own
`ClockConfiguration`, `ConsumerConfiguration` (the `IdempotentConsumer` bean), no outbox yet (it
only consumes). Migrations: `V202611242000__baseline`, `V202611242010__processed_events`
(`PK(consumer, event_id)`, `processed_at DEFAULT now()`), and dev/test-only
`notification-probe/V202611242020__probe_read_model` (own `id`, so without the dedupe a repeat
WOULD add a row). `ProbeListener` (dev/test): `@KafkaListener(id = groupId = "notification.probe",
topics = "platform.probe")`, parses the envelope into `DomainEvent<ProbePayload>` (its own record,
no core-app import), writes the row inside `runOnce`. `spring.kafka.consumer.auto-offset-reset:
earliest` so a new group sees older events. Tests (as in T10, Postgres runs the Compose init script
and the app logs in as `notification_svc`; plus a Kafka container and the `platform.probe` topic):
`IdempotentConsumerIT` (twice → 1 row; failure after the dedupe insert → both rolled back, retry →
1 row; two consumers → each once; two concurrent copies → one runs), `ProbeListenerIT` (same
envelope twice over real Kafka + a marker event with the same key → after the marker, exactly 1 row
for the duplicated event), `SchemaIsolationIT` (7, `42501` on every other schema),
`NotificationApplicationIT`, `PingControllerTest`, `OpenApiExportIT`; starter
`IdempotentConsumerTest` (8). **Proven able to fail:** listener without the dedupe →
`ProbeListenerIT` failed; `IdempotentConsumer` without its transaction → "dedupe row rolled back:
expected 0 but was 1". Full `./mvnw verify` green: kernel 81, starter 17 + 6 IT, core-app 27 + 14 IT,
registration 2 + 14 IT, notification 2 + 20 IT. **End to end on Compose:** core-app and
notification-service (dev), `POST :8081/api/v1/platform/outbox-probe?note=end-to-end-T11` → one row
in `notification.probe_read_model` with its `processed_events` mark (consumer
`notification.probe`), so the real outbox envelope parses. Spec D2 text updated to
`PK(consumer, event_id)` (the task's design), O4 and I2 ticked. **Note for T12:** the
`auto-offset-reset: earliest` consumer default is in this app's yml; T12 may move it to the starter.

**Files:** `platform-starter/pom.xml`, `platform-starter/…/consumer/IdempotentConsumer.java` (+ `IdempotentConsumerTest`), root `pom.xml`, `notification-service/pom.xml`, `NotificationApplication.java`, `ClockConfiguration.java`, `PingController.java`, `ConsumerConfiguration.java`, `ProbeListener.java`, `application.yml`, `V202611242000__baseline.sql`, `V202611242010__processed_events.sql`, `notification-probe/V202611242020__probe_read_model.sql`, tests `TestcontainersConfiguration`, `IdempotentConsumerIT`, `ProbeListenerIT`, `SchemaIsolationIT`, `NotificationApplicationIT`, `PingControllerTest`, `OpenApiExportIT`, `docs/api/notification-service.json`, `SPEC-platform.md`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

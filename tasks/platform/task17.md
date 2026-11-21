# T17: JSON logs with trace ids → Loki

> Module `platform` · Phase 4: Observability · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T16](task16.md) · [T18 →](task18.md)

`Scope S · Depends on: T16 · Closes: B2`

**Goal:** every log line can be found by trace id, and traces link to their logs.

**Subtasks**
- [ ] **17.1** Turn on Boot structured (JSON) console logging for non-test profiles, and make sure each line has `traceId` and `spanId` fields.
- [ ] **17.2** Keep plain-text logging in tests so test output stays readable.
- [ ] **17.3** Add Loki to the `observability` profile, and add a logs pipeline in the Collector (OTLP → Loki).
- [ ] **17.4** In Grafana, add a Loki datasource with a `traceId` → Tempo link, and turn on Tempo's "trace to logs" link to Loki.
- [ ] **17.5** Manual: from the B1 trace, open its logs, then from a log line jump back to the trace.

**Done when**
- [ ] Every app log line is single-line JSON with `traceId`/`spanId` (inside a request)
- [ ] Trace → logs and logs → trace both work in Grafana

**Verify**
- [ ] Step 17.5 was observed, and `./mvnw verify` passes

**Files:** `platform-starter/…/logging defaults`, `infra/docker-compose.yml`, `infra/otel/collector.yaml`, `infra/grafana/provisioning/datasources/datasources.yaml`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

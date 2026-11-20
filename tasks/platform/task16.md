# T16: Traces through gateway → service → Kafka → consumer

> Module `platform` · Phase 4: Observability · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T15](task15.md) · [T17 →](task17.md)

`Scope M · Depends on: Checkpoint C · Closes: B1`

**Goal:** one request can be followed across every app and through Kafka in Grafana/Tempo.

**Subtasks**
- [ ] **16.1** Pin the OpenTelemetry Java agent version in the catalog, and add a Gradle task that downloads it to `build/otel/`.
- [ ] **16.2** In the `spring-app` convention, make `bootRun` add `-javaagent` only when `OTEL_EXPORTER_OTLP_ENDPOINT` is set, and set `service.name` per app.
- [ ] **16.3** Copy the agent into the image in the Dockerfile. In the `apps` profile, enable it through `JAVA_TOOL_OPTIONS` when the observability endpoint is set.
- [ ] **16.4** Add Compose profile `observability`:
  - an OTel Collector (OTLP in → Tempo out);
  - Tempo;
  - Grafana with the Tempo datasource provisioned.
- [ ] **16.5** Add `?emit=true` to core-app ping (`dev` profile only). It publishes a probe event, which notification-service consumes.
- [ ] **16.6** Switch the starter's `traceId` provider to the current span's trace id, falling back to a generated id when no agent is attached.
- [ ] **16.7** Manual: call `ping?emit=true` through :8080 in both run modes, find the trace in
  Tempo, and look up a problem response's `traceId`.

**Done when**
- [ ] One trace shows gateway → core-app → Kafka produce → notification-service consume, in both run modes
- [ ] A problem JSON `traceId` can be found in Tempo
- [ ] Builds and tests still pass with no collector running

**Verify**
- [ ] Step 16.7 was observed, with the trace id noted in the PR
- [ ] `./gradlew build` passes with observability off

**Files:** `infra/docker-compose.yml`, `infra/otel/collector.yaml`, `infra/grafana/provisioning/datasources/datasources.yaml`, `infra/docker/spring-app.Dockerfile`, `build-logic/…/campus.spring-app.gradle.kts`

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

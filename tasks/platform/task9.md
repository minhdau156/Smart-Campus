# T9: OpenAPI docs (backend part)

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T8](task8.md) · [T10 →](task10.md)

`Scope M · Depends on: T8 · Closes: A1 (backend part)`

**Goal:** each app documents its API automatically, with one group per module, saved as JSON that the frontend can generate types from.

**Subtasks**
- [ ] **9.1** Add springdoc to the starter. Register a shared `Problem` schema and attach it as the default error response for every operation.
- [ ] **9.2** In `core-app`, write `ModuleApiGroups`, which creates one `GroupedOpenApi` per module found by `ApplicationModules`. New modules then get a group automatically.
- [ ] **9.3** Write `OpenApiExportIT`, which fetches `/v3/api-docs/<group>` and writes
  `docs/api/<app>-<group>.json` with sorted keys and pretty printing.
- [ ] **9.4** Add an `export-openapi` Maven profile (in the root `pom.xml`) that runs only that IT: `./mvnw verify -Pexport-openapi`.
- [ ] **9.5** Run `./mvnw verify -Pexport-openapi` twice and check `git diff` (or a file compare) shows no change. Open Swagger UI.

**Done when**
- [ ] `core-app` serves `/v3/api-docs/platform`, and Swagger UI lists one group per module
- [ ] The export is deterministic: two runs produce identical files
- [ ] Error responses in the doc point to the shared `Problem` schema (with `code` and `traceId`)

**Verify**
- [ ] `./mvnw -pl backend/core-app -am verify -Pexport-openapi` passes
- [ ] http://localhost:8081/swagger-ui.html shows the `platform` group

**Files:** `platform-starter/…/OpenApiAutoConfiguration.java`, `core-app/…/platform/ModuleApiGroups.java`, root `pom.xml`, `OpenApiExportIT.java`, `docs/api/core-app-platform.json`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

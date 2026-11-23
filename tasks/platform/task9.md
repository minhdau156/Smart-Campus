# T9: OpenAPI docs (backend part)

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T8](task8.md) · [T10 →](task10.md)

`Scope M · Depends on: T8 · Closes: A1 (backend part)`

**Goal:** each app documents its API automatically, with one group per module, saved as JSON that the frontend can generate types from.

**Subtasks**
- [x] **9.1** Add springdoc to the starter. Register a shared `Problem` schema and attach it as the default error response for every operation.
- [x] **9.2** In `core-app`, write `ModuleApiGroups`, which creates one `GroupedOpenApi` per module found by `ApplicationModules`. New modules then get a group automatically.
- [x] **9.3** Write `OpenApiExportIT`, which fetches `/v3/api-docs/<group>` and writes
  `docs/api/<app>-<group>.json` with sorted keys and pretty printing.
- [x] **9.4** Add an `export-openapi` Maven profile (in the root `pom.xml`) that runs only that IT: `./mvnw verify -Pexport-openapi`.
- [x] **9.5** Run `./mvnw verify -Pexport-openapi` twice and check `git diff` (or a file compare) shows no change. Open Swagger UI.

**Done when**
- [x] `core-app` serves `/v3/api-docs/platform`, and Swagger UI lists one group per module
- [x] The export is deterministic: two runs produce identical files
- [x] Error responses in the doc point to the shared `Problem` schema (with `code` and `traceId`)

**Verify**
- [x] `./mvnw -pl backend/core-app -am verify -Pexport-openapi` passes
- [x] http://localhost:8081/swagger-ui.html shows the `platform` group *(checked by `OpenApiExportIT`: page 200, one group per module; browser check on :8081 pending, see Result)*

**Result (2026-11-23):** starter: `OpenApiAutoConfiguration` (on when springdoc is present) with
`ProblemSchemaCustomizer` (a `GlobalOpenApiCustomizer`, so it runs for every group): schemas
`Problem` + `FieldError`, and a `default` response → `application/problem+json` `$ref Problem` on every
operation (a controller's own `default` is kept); an `OpenAPI` bean with title/version and the
relative server `/` (otherwise the random test port ends up in the file); defaults
`springdoc.default-produces-media-type: application/json` (not `*/*`). core-app:
`springdoc-openapi-starter-webmvc-ui` (version 3.2.0 from the root pom; `springdoc-openapi-starter-common`
added there for the starter), `ModuleApiGroups` = one `@Bean List<GroupedOpenApi>` from
`ApplicationModules` (group = module id, display name = module name, packages = module package).
`OutboxProbeController` is `@Hidden` (dev tool, not API). `OpenApiExportIT`: Swagger UI 200 and one
URL per module in `/v3/api-docs/swagger-config`; `Problem` with `code`/`traceId`; ping's `default`
→ `Problem`; probe hidden; writes `docs/api/core-app-<group>.json` (sorted keys, pretty, `
`) when
`-Dopenapi.export=true`, otherwise **compares** and fails with "out of date. Run ./mvnw verify
-Pexport-openapi" (seen failing with a tampered file). Profile `export-openapi` in the root pom
(skipUTs, `it.test=OpenApiExportIT`, failsafe system property). Two export runs → byte-identical
file. Full `./mvnw verify` green: kernel 81, starter 9 + 6 IT, core-app 27 + 14 IT. **Found:**
a `BeanDefinitionRegistryPostProcessor` registering one bean per group came too late for springdoc's
`@ConditionalOnBean(GroupedOpenApi)` → no groups (404); springdoc also accepts a `List<GroupedOpenApi>`
bean, which works and is simpler. **Found:** bean-name clash class `moduleApiGroups` vs method of
the same name → method renamed. **Found:** VS Code's compiler wrote broken classes into `target/`
while editing → `clean` before verify. Swagger UI checked by the IT (200); the manual browser check
on :8081 needs Compose, whose Postgres port 5432 is still taken by another project's container.

**Files:** `platform-starter/…/OpenApiAutoConfiguration.java`, `core-app/…/platform/ModuleApiGroups.java`, root `pom.xml`, `OpenApiExportIT.java`, `docs/api/core-app-platform.json`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

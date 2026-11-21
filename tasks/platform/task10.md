# T10: `registration-service` skeleton

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T9](task9.md) · [T11 →](task11.md)

`Scope M · Depends on: T9 · Closes: I2 (D3 part)`

**Goal:** the separate Registration service runs and can only touch its own schema.

**Subtasks**
- [ ] **10.1** Create `backend/registration-service` (`spring-app`, starter, web, Flyway, Postgres, Kafka) and register it in settings.
- [ ] **10.2** Write `RegistrationApplication` in package `edu.campus.registration`, on port 8082.
- [ ] **10.3** Write `application.yml`: datasource user `registration_svc`, Flyway schema `registration`, event-registry table in `registration`.
- [ ] **10.4** Add a baseline migration and a `PingController` for `GET /api/v1/registration/ping`.
- [ ] **10.5** Write `SchemaIsolationIT`: Testcontainers Postgres runs the **same** init SQL as
  Compose (`infra/postgres/init/`). Connected as `registration_svc`, `SELECT` on `notification.*`
  and `course.*` must fail with a permission error.
- [ ] **10.6** Add the app to the `export-openapi` Maven profile, then start it against Compose and curl ping and health.

**Done when**
- [ ] Health is `UP` and ping returns 200 on :8082
- [ ] The isolation IT proves the service can't read other schemas
- [ ] `docs/api/` contains this app's OpenAPI JSON

**Verify**
- [ ] `./mvnw -pl backend/registration-service -am verify` passes

**Files:** `backend/registration-service/pom.xml`, `RegistrationApplication.java`, `PingController.java`, `application.yml`, `SchemaIsolationIT.java`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

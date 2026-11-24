# T10: `registration-service` skeleton

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T9](task9.md) · [T11 →](task11.md)

`Scope M · Depends on: T9 · Closes: I2 (D3 part)`

**Goal:** the separate Registration service runs and can only touch its own schema.

**Subtasks**
- [x] **10.1** Create `backend/registration-service` (`spring-app`, starter, web, Flyway, Postgres, Kafka) and register it in settings.
- [x] **10.2** Write `RegistrationApplication` in package `edu.campus.registration`, on port 8082.
- [x] **10.3** Write `application.yml`: datasource user `registration_svc`, Flyway schema `registration`, event-registry table in `registration`.
- [x] **10.4** Add a baseline migration and a `PingController` for `GET /api/v1/registration/ping`.
- [x] **10.5** Write `SchemaIsolationIT`: Testcontainers Postgres runs the **same** init SQL as
  Compose (`infra/postgres/init/`). Connected as `registration_svc`, `SELECT` on `notification.*`
  and `course.*` must fail with a permission error.
- [x] **10.6** Add the app to the `export-openapi` Maven profile, then start it against Compose and curl ping and health.

**Done when**
- [x] Health is `UP` and ping returns 200 on :8082
- [x] The isolation IT proves the service can't read other schemas
- [x] `docs/api/` contains this app's OpenAPI JSON

**Verify**
- [x] `./mvnw -pl backend/registration-service -am verify` passes

**Result (2026-11-24):** new module `backend/registration-service` (in the root `<modules>`), same
dependencies as core-app minus Modulith core/test and ArchUnit (one app = one module, nothing to
verify): starter, webmvc, springdoc webmvc-ui, actuator, jdbc, Flyway + Postgres, the outbox
(`spring-modulith-starter-jdbc`, `-events-kafka`, Kafka starter). `RegistrationApplication`
(`edu.campus.registration`, port 8082), its own `ClockConfiguration` (the starter has no `Clock`),
`PingController` (`GET /api/v1/registration/ping`, same shape as core-app's). `application.yml`:
user `registration_svc`, Flyway schema/default schema/locations `registration`; the outbox table
schema follows from the starter (`jdbc.schema` = Flyway default schema). Migrations
`V202611240000__baseline` and `V202611240100__event_publication` (the registry table, copied like
core-app's). **Tests run as the real restricted user:** `TestcontainersConfiguration` copies
`infra/postgres/init/01-schemas-and-users.sh` into the container's `/docker-entrypoint-initdb.d/`
(same schemas, users and grants as Compose) and logs the app in as `registration_svc` through a
`DynamicPropertyRegistrar` (no `@ServiceConnection`, which would use the superuser). So every IT
also proves Flyway and the outbox work with only the `registration` rights. `SchemaIsolationIT`
(7 tests): the superuser first creates a table with a row in every other schema, then as
`registration_svc` SELECT on notification/course (and all 11 other schemas), INSERT, CREATE TABLE
there and in `public` all fail with SQLState `42501`; own schema read/write works. Proven able to
fail: with the login switched to the superuser, 6 of 7 failed. `OpenApiExportIT` (one document,
no module groups) writes/compares `docs/api/registration-service.json`; the existing
`export-openapi` profile picks it up by name, so the profile needed no change. Full `./mvnw verify`
green: kernel 81, starter 9 + 6 IT, core-app 27 + 14 IT, registration-service 2 + 14 IT. Against
Compose (jar, `dev` profile): Flyway applied 2 migrations to `registration`, health `UP` (db UP),
ping 200 on :8082, `/v3/api-docs` 200; psql as `registration_svc`: own outbox table readable,
`platform` and `notification` → "permission denied for schema". **Found:** core-app's
`OutboxIT.o3_longOutage…` failed once in the first full build (Awaitility timeout at the "not
complete during the outage" check), right after Docker Desktop started; it passed alone and in the
next full build. Likely timing-sensitive under load; not changed here (outside this task).
**Found:** port 5432 is used by another project's container (`postgres_container`); it was
stopped for the Compose check and started again afterwards (Compose `postgres` + `keycloak`
stopped to free the port).

**Files:** `backend/registration-service/pom.xml`, `RegistrationApplication.java`, `ClockConfiguration.java`, `PingController.java`, `application.yml`, `db/migration/registration/V202611240000__baseline.sql`, `V202611240100__event_publication.sql`, test `TestcontainersConfiguration.java`, `PingControllerTest.java`, `RegistrationApplicationIT.java`, `SchemaIsolationIT.java`, `OpenApiExportIT.java`, root `pom.xml`, `docs/api/registration-service.json`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

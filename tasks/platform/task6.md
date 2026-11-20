# T6: `core-app` skeleton

> Module `platform` · Phase 2: First app + outbox risk spike · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T5](task5.md) · [T7 →](task7.md)

`Scope M · Depends on: T3, T5 · Closes: P3`

**Goal:** the modular-monolith app starts, answers a ping, and the build fails if modules break their boundaries.

**Subtasks**
- [ ] **6.1** Create `backend/core-app` (plugins `spring-app` + `integration-test`). Dependencies:
  web, actuator, JDBC, Flyway, the Postgres driver, the Modulith starter and `shared-kernel`.
  Add it to `<modules>` in the root `pom.xml`.
- [ ] **6.2** Write `CoreApplication`, and create the `edu.campus.platform` package as the first Modulith module (`package-info.java` with `@ApplicationModule`).
- [ ] **6.3** Write a `PingController` for `GET /api/v1/platform/ping`, returning `{app, version, time}`. It gets the time from an injected `Clock`.
- [ ] **6.4** Write `application.yml`: port 8081, datasource user `core_app`, Flyway default schema `platform`, and `dev` and `test` profiles.
- [ ] **6.5** Add the first Flyway migration (`db/migration/platform/V…__baseline.sql`).
- [ ] **6.6** Write `ModularityTest`: `ApplicationModules.of(CoreApplication.class).verify()`.
- [ ] **6.7** Add a boundary-violation fixture in test sources: two fake modules, one reaching
  into the other's `internal` package. Write a test asserting that `verify()` **throws** for them,
  and add an ArchUnit rule for the same thing.
- [ ] **6.8** With Compose up, run `./mvnw -pl backend/core-app spring-boot:run` and curl the health and ping endpoints.

**Done when**
- [ ] Health returns `UP` and ping returns 200 on :8081
- [ ] `ModularityTest` passes, and the violation fixture test proves violations are caught (P3)
- [ ] `platform.flyway_schema_history` exists and has the baseline row

**Verify**
- [ ] `./mvnw -pl backend/core-app -am verify` passes
- [ ] `curl localhost:8081/api/v1/platform/ping` returns JSON

**Files:** `backend/core-app/pom.xml`, `CoreApplication.java`, `platform/PingController.java`, `application.yml`, `ModularityTest.java` (+ fixture package)

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

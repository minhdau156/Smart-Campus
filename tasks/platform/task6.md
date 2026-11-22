# T6: `core-app` skeleton

> Module `platform` · Phase 2: First app + outbox risk spike · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T5](task5.md) · [T7 →](task7.md)

`Scope M · Depends on: T3, T5 · Closes: P3`

**Goal:** the modular-monolith app starts, answers a ping, and the build fails if modules break their boundaries.

**Subtasks**
- [x] **6.1** Create `backend/core-app` (plugins `spring-app` + `integration-test`). Dependencies:
  web, actuator, JDBC, Flyway, the Postgres driver, the Modulith starter and `shared-kernel`.
  Add it to `<modules>` in the root `pom.xml`.
- [x] **6.2** Write `CoreApplication`, and create the `edu.campus.platform` package as the first Modulith module (`package-info.java` with `@ApplicationModule`).
- [x] **6.3** Write a `PingController` for `GET /api/v1/platform/ping`, returning `{app, version, time}`. It gets the time from an injected `Clock`.
- [x] **6.4** Write `application.yml`: port 8081, datasource user `core_app`, Flyway default schema `platform`, and `dev` and `test` profiles.
- [x] **6.5** Add the first Flyway migration (`db/migration/platform/V…__baseline.sql`).
- [x] **6.6** Write `ModularityTest`: `ApplicationModules.of(CoreApplication.class).verify()`.
- [x] **6.7** Add a boundary-violation fixture in test sources: two fake modules, one reaching
  into the other's `internal` package. Write a test asserting that `verify()` **throws** for them,
  and add an ArchUnit rule for the same thing.
- [x] **6.8** With Compose up, run `./mvnw -pl backend/core-app spring-boot:run` and curl the health and ping endpoints.

**Done when**
- [x] Health returns `UP` and ping returns 200 on :8081
- [x] `ModularityTest` passes, and the violation fixture test proves violations are caught (P3)
- [x] `platform.flyway_schema_history` exists and has the baseline row

**Verify**
- [x] `./mvnw -pl backend/core-app -am verify` passes
- [x] `curl localhost:8081/api/v1/platform/ping` returns JSON

**Result (2026-11-22):** core-app: 16 unit tests + 3 ITs (Testcontainers `postgres:19.0-alpine3.24`).
`spring-boot:run` (profile `dev`, set in the plugin config) starts in ~3.4 s; health `UP` with `db`
`UP`; ping → `{"app":"core-app","version":"0.1.0-SNAPSHOT","time":"…Z"}`;
`platform.flyway_schema_history` has `202611220000 | baseline | core_app | t` in the Compose DB.
P3 proven twice: the kept fixture (`fixture.boundary`, test sources, loaded with an "include all"
`ImportOption` because Modulith skips test classes), and once by adding a real
`edu.campus.demo` class using `platform.internal` → `ModularityTest` and the ArchUnit rule both
failed (then removed). **Found and fixed:** Modulith also treated shared-kernel's
`edu.campus.kernel` as a module, so `platform` using `kernel.event.Topics` failed `verify()`;
`CoreModuleDetection` (= direct sub-packages minus `edu.campus.kernel`, set by
`spring.modulith.detection-strategy`) fixes it, seen passing with a temporary probe class.
Maven translation of "plugins spring-app + integration-test": `spring-boot-maven-plugin` + the
root pom's failsafe. Boot 4 names: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`
(+ `flyway-database-postgresql`), `spring-boot-starter-webmvc-test`. `dev` reads passwords from
`infra/.env` via `spring.config.import: optional:file:../../infra/.env[.properties]`. Version in
ping = Maven resource filtering (`@project.version@`). `KernelPlaceholderIT` removed (replaced by
`CoreApplicationIT`). Warnings seen (not failures): Flyway 12.4 "PostgreSQL 19.0 is newer than the
latest verified version (18)"; Mockito self-attaching its agent.

**Files:** `backend/core-app/pom.xml`, `CoreApplication.java`, `platform/PingController.java`, `application.yml`, `ModularityTest.java` (+ fixture package)

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

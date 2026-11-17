# Tasks: `platform` module

> Spec: [SPEC-platform.md](../SPEC-platform.md) · Plan: [plan.md](plan.md)
> **Definition of Done (every task):** `./gradlew build` green (plus frontend checks if touched),
> no new suppressions or disabled tests, spec updated first if a decision changed, and the
> success-criterion ids it closes are ticked in the spec.
> File counts below exclude generated files (Gradle wrapper, lockfiles).

---

## Phase 1: Foundation

### - [ ] Task 1: Gradle root, wrapper, version catalog

**Description:** Create the root Gradle build (Kotlin DSL) and pin the Boot 4 line. Look up the
current GA versions of Spring Boot 4.x, Spring Modulith 2.x, the Spring Cloud release train for
that Boot version, Testcontainers, ArchUnit, and the Spotless plugin from official release notes and
compatibility pages, then pin them in the version catalog. Create an empty `backend/shared-kernel`
project so there is something to build.

**Acceptance criteria:**
- [ ] `settings.gradle.kts` declares `rootProject.name = "smart-campus"` and includes `backend:shared-kernel`
- [ ] Every library and plugin version is in `gradle/libs.versions.toml`. No version strings appear anywhere else
- [ ] The Spring Cloud train is the one officially listed as compatible with the pinned Boot version

**Verification:**
- [ ] `./gradlew build` and `.\gradlew.bat build` both succeed from `C:\Smart Campus` (path with a space)
- [ ] Manual: catalog versions match the cited release notes (links in a comment at the top of the catalog)

**Dependencies:** None
**Files:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `.editorconfig` (+ generated wrapper)
**Scope:** M

### - [ ] Task 2: Build conventions

**Description:** Add `build-logic` convention plugins so every project gets the same setup:
`campus.java-conventions` (Java 21 toolchain, Spotless + palantir-java-format, JaCoCo, JUnit 5),
`campus.integration-test` (`src/integrationTest/java` source set and `integrationTest` task, wired
into `check`), and `campus.spring-app` (Boot plugin + dependency management). Apply them to
`shared-kernel` and add one trivial test in each source set to prove they run.

**Acceptance criteria:**
- [ ] `./gradlew test` runs only unit tests. `./gradlew integrationTest` runs only ITs. `build` runs both
- [ ] A badly formatted Java file makes `spotlessCheck` (and therefore `build`) fail
- [ ] A JaCoCo report is produced for `shared-kernel`

**Verification:**
- [ ] `./gradlew build` succeeds. Then misformat a file and confirm `build` fails, then revert it
- [ ] `./gradlew test integrationTest --info` shows the two placeholder tests running in separate tasks

**Dependencies:** T1
**Files:** `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts`, `build-logic/src/main/kotlin/campus.java-conventions.gradle.kts`, `…/campus.integration-test.gradle.kts`, `…/campus.spring-app.gradle.kts`, `backend/shared-kernel/build.gradle.kts`
**Scope:** M

### - [ ] Task 3: Compose core infra, schemas, DB users *(parallel with T1–T2)*

**Description:** Write `infra/docker-compose.yml` with pinned image tags and healthchecks for
Postgres, Redis, Kafka (KRaft, single broker), Keycloak (dev mode, its own DB, empty), OpenSearch
(heap 512m, security off in dev), MinIO, and Mailpit. A Postgres init script creates the 12
schemas from spec I2, plus users `core_app` (owns all core schemas plus `platform`),
`registration_svc` (`registration` only), and `notification_svc` (`notification` only).
Credentials go in `infra/.env.example`. The real `.env` is gitignored.

**Acceptance criteria:**
- [ ] `docker compose -f infra/docker-compose.yml up -d --wait` returns with every service healthy in under 3 minutes (warm cache)
- [ ] `\dn` in psql lists all 12 schemas. `registration_svc` gets "permission denied" on `SELECT` from a table in `notification`
- [ ] No image uses `latest` or a tag without a version

**Verification:**
- [ ] Manual: run `up --wait`, then `docker compose ps` shows all services healthy
- [ ] Manual: psql permission check as above. Record peak Docker memory for the README

**Dependencies:** None
**Files:** `infra/docker-compose.yml`, `infra/.env.example`, `infra/postgres/init/01-schemas-and-users.sql`, `.gitignore`
**Scope:** S

### - [ ] Task 4: `shared-kernel` value types and problem codes

**Description:** Write framework-free value types: a typed-id pattern (`record StudentId(UUID value)`
style plus a small `Ids` helper), `Money` (amount + ISO currency, VND-safe scale, no `double`), and
`ProblemCode` (enum of stable codes with HTTP status and title, seeded with `INTERNAL_ERROR`,
`VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`) plus `CampusProblem` (runtime exception carrying a
`ProblemCode` and detail). Feature modules add their own codes later.

**Acceptance criteria:**
- [ ] `Money` rejects a null currency, mixed-currency arithmetic, and a scale wider than the currency allows. `equals` is scale-insensitive
- [ ] `ProblemCode` maps to a status and a kebab-case `type` URI (`https://campus.edu/problems/<code>`)
- [ ] `shared-kernel` has no dependency on Spring (enforced by a dependency check in its build file)

**Verification:**
- [ ] `./gradlew :backend:shared-kernel:test` passes. JaCoCo shows ≥ 90% line coverage and the gate is enforced in the build

**Dependencies:** T2
**Files:** `kernel/id/Ids.java`, `kernel/money/Money.java`, `kernel/error/ProblemCode.java`, `kernel/error/CampusProblem.java`, matching tests
**Scope:** M

### - [ ] Task 5: `shared-kernel` event envelope

**Description:** Add `DomainEvent<P>` (spec D4) and an `EventTypes` registry that maps a type name to
a payload class, so consumers can deserialize. Prove a JSON round-trip using Jackson 3
(`tools.jackson`, test scope only, so the kernel stays framework-free).

**Acceptance criteria:**
- [ ] The JSON shape matches Arch §6 plus `schemaVersion`. Field names are exactly `eventId, type, schemaVersion, occurredAt, aggregateId, payload`
- [ ] `occurredAt` is serialized as an ISO-8601 UTC string, not epoch millis
- [ ] Constructor validation rejects null fields and `schemaVersion < 1`

**Verification:**
- [ ] `./gradlew :backend:shared-kernel:test` passes with a golden-JSON test, and coverage stays ≥ 90%

**Dependencies:** T4
**Files:** `kernel/event/DomainEvent.java`, `kernel/event/EventTypes.java`, `DomainEventJsonTest.java`, `src/test/resources/golden/domain-event.json`
**Scope:** S

### ◆ Checkpoint A: Foundation
- [ ] `./gradlew build` green on Windows from `C:\Smart Campus`
- [ ] Compose infra healthy. The DB-user permission check passes
- [ ] Human review of the pinned versions before any app code exists

---

## Phase 2: First app + outbox risk spike

### - [ ] Task 6: `core-app` skeleton

**Description:** Create the `core-app` Spring Boot 4 + Modulith application with a single
`edu.campus.platform` module that exposes `GET /api/v1/platform/ping` (returns the app name, version
and time). Flyway connects as `core_app` and owns the `platform` schema. Add `ModularityTest`
(`ApplicationModules.of(CoreApplication.class).verify()`), an ArchUnit rule banning cross-module
`internal` access, and a test fixture that proves a violation fails the build (spec P3).

**Acceptance criteria:**
- [ ] Against Compose, `./gradlew :backend:core-app:bootRun` starts on :8081. `/actuator/health` is `UP`. `/api/v1/platform/ping` returns 200
- [ ] `ModularityTest` passes, and the violation fixture test asserts that `verify()` throws (P3)
- [ ] Flyway history exists in `platform.flyway_schema_history`

**Verification:**
- [ ] `./gradlew :backend:core-app:build` passes. Manual: `curl localhost:8081/api/v1/platform/ping`

**Dependencies:** T3, T5
**Files:** `backend/core-app/build.gradle.kts`, `CoreApplication.java`, `platform/PingController.java`, `application.yml`, `ModularityTest.java` (+ violation fixture)
**Scope:** M

### - [ ] Task 7: Outbox spike: O1, O2, O3 ⚠ highest risk

**Description:** Turn on the Spring Modulith JDBC event publication registry and Kafka
externalization in `core-app`. Add a dev-only `platform` test aggregate whose service saves a row
and publishes a `DomainEvent` in one transaction. Write ITs (Testcontainers Postgres + Kafka) for
O1 (event published with the envelope and `aggregateId` as key), O2 (a rollback publishes nothing),
and O3 (Kafka paused at commit → the publication stays incomplete → after resubmission or restart
it is delivered). Record which Modulith settings make O3 hold.

**Acceptance criteria:**
- [ ] O1, O2 and O3 each have a green integration test
- [ ] A publication is marked complete only after the broker acks. This is asserted in the O3 test, not assumed
- [ ] Spike notes (settings used, behavior observed) are appended to spec D1

**Verification:**
- [ ] `./gradlew :backend:core-app:integrationTest` passes 3 times in a row, to rule out flakiness

**Dependencies:** T6
**Files:** `core-app/…/platform/internal/OutboxProbe.java`, `…/OutboxProbeService.java`, `application.yml`, `db/migration/platform/V…__outbox_probe.sql`, `OutboxIT.java`
**Scope:** M

### ◆ Checkpoint B: D1 decision gate
- [ ] O1–O3 green → keep D1. Any failure → stop, update spec D1 to the hand-written outbox, and re-plan T7 before continuing
- [ ] **Human review** of the spike result before Phase 3

---

## Phase 3: Services, errors, routing

### - [ ] Task 8: `platform-starter` with RFC 7807 problem handler (E1)

**Description:** Create the `platform-starter` library (Spring auto-configuration used by all 4
apps). It maps `CampusProblem`, bean-validation failures, unknown routes and unhandled exceptions to
`application/problem+json` with `type, title, status, detail, code, traceId`. It never includes stack
traces. Wire it into `core-app`. Move the outbox/Modulith defaults from T7 into the starter so the
services inherit them.

**Acceptance criteria:**
- [ ] The 4 error kinds in E1 each return the exact problem shape. Unhandled exceptions return 500 `INTERNAL_ERROR` with a generic detail
- [ ] `traceId` is present (it may be a generated id until T13 adds OTel)
- [ ] `core-app` no longer has its own outbox config. It comes from the starter

**Verification:**
- [ ] `./gradlew :backend:platform-starter:build :backend:core-app:build` passes. ITs cover all 4 E1 cases, and the T7 ITs still pass

**Dependencies:** T7
**Files:** `backend/platform-starter/build.gradle.kts`, `ProblemHandlingAutoConfiguration.java`, `CampusProblemHandler.java`, `META-INF/spring/…AutoConfiguration.imports`, `ProblemJsonIT.java`
**Scope:** M

### - [ ] Task 9: `registration-service` skeleton (D3)

**Description:** Create a Boot app on :8082 that uses `platform-starter`, has
`GET /api/v1/registration/ping`, and runs Flyway against the `registration` schema as
`registration_svc`. Add an IT proving the service's datasource cannot read another schema.

**Acceptance criteria:**
- [ ] Starts against Compose. Health is `UP`. Ping returns 200
- [ ] An IT shows `SELECT` on `notification.*` and `course.*` fails with a permission error (D3, I2)

**Verification:**
- [ ] `./gradlew :backend:registration-service:build` passes. Manual curl on :8082

**Dependencies:** T8
**Files:** `backend/registration-service/build.gradle.kts`, `RegistrationApplication.java`, `PingController.java`, `application.yml`, `SchemaIsolationIT.java`
**Scope:** M

### - [ ] Task 10: `notification-service` skeleton with idempotent consumer (O4)

**Description:** Create a Boot app on :8083 with ping, Flyway on `notification` as `notification_svc`,
and the D2 idempotent-consumer helper (in `platform-starter`), backed by
`notification.processed_events`. A dev-only listener consumes the `core-app` outbox probe event
and writes one row per event.

**Acceptance criteria:**
- [ ] Delivering the same event twice produces exactly one side-effect row (O4)
- [ ] The dedup insert and the side effect commit or roll back together (failure-injection IT)
- [ ] D3 isolation IT, same as T9

**Verification:**
- [ ] `./gradlew :backend:notification-service:build` passes. Manual: trigger the probe on core-app and see one row in `notification`

**Dependencies:** T8
**Files:** `platform-starter/…/IdempotentConsumer.java`, `notification-service/build.gradle.kts`, `NotificationApplication.java`, `ProbeListener.java`, `V…__processed_events.sql`, `IdempotentConsumerIT.java`
**Scope:** M

### - [ ] Task 11: Kafka topic declarations (O5)

**Description:** Declare the Arch §6 topics as `NewTopic` beans in the app that produces each one:
`registration.requests` (12) and `registration.events` (6) in registration-service;
`course.events` (3), `grade.events` (3) and `campus.events` (3) in core-app. Topic names live in
`shared-kernel` constants so producers and consumers cannot drift apart.

**Acceptance criteria:**
- [ ] After all apps start, `kafka-topics --describe` shows the 5 topics with the stated partition counts
- [ ] No topic is auto-created by the broker. `auto.create.topics.enable=false` is set in Compose

**Verification:**
- [ ] IT per producing app asserts topic existence and partition count. `./gradlew build` passes

**Dependencies:** T9, T10
**Files:** `kernel/event/Topics.java`, `core-app/…/platform/TopicConfig.java`, `registration-service/…/TopicConfig.java`, `infra/docker-compose.yml`, `TopicsIT.java`
**Scope:** S

### - [ ] Task 12: `gateway` routes and smoke script (I3)

**Description:** Create the Spring Cloud Gateway (server-webflux) app on :8080 with the 3 routes
from the spec, the `/ws/**` route to notification-service (WebSocket upgrade passthrough), and
problem JSON for unmatched routes and backend-unreachable errors (a reactive variant of the T8
handler). Write `infra/smoke.sh`, which curls the 3 pings and the 4 health endpoints.

**Acceptance criteria:**
- [ ] The 3 `/ping` URLs in the spec Commands section answer through :8080
- [ ] An unknown path → 404 problem JSON. A stopped backend → 503 problem JSON, not an HTML whitelabel page
- [ ] `infra/smoke.sh` exits 0 with everything up and non-zero if any app is down

**Verification:**
- [ ] `./gradlew :backend:gateway:build` passes. Its routing IT uses stub backends
- [ ] Manual: all apps + Compose up → `bash infra/smoke.sh` exits 0

**Dependencies:** T9, T10
**Files:** `backend/gateway/build.gradle.kts`, `GatewayApplication.java`, `application.yml`, `GatewayProblemHandler.java`, `GatewayRoutingIT.java`, `infra/smoke.sh`
**Scope:** M

### ◆ Checkpoint C: Platform runs end to end
- [ ] `./gradlew build` green. Compose up plus 4 apps running → `smoke.sh` passes
- [ ] Spec criteria P1, P3, I1–I3, O1–O5 and E1 ticked
- [ ] Human review

---

## Phase 4: Observability

### - [ ] Task 13: Traces through gateway, service and Kafka (B1)

**Description:** Add a Compose `observability` profile with the OTel Collector, Tempo and Grafana
(Tempo datasource provisioned). Attach the pinned OTel Java agent to every `bootRun` (Gradle
convention) and to the app images later. Add the dev-only `?emit=true` flag on core-app ping, which
publishes a probe event consumed by notification-service. Replace the generated `traceId` in
problem JSON with the real trace id.

**Acceptance criteria:**
- [ ] One `ping?emit=true` call through :8080 shows in Grafana → Tempo as one trace containing gateway, core-app, Kafka produce, and notification-service consume spans
- [ ] The `traceId` in a problem JSON response can be looked up in Tempo
- [ ] Agent is attached only when `OTEL_EXPORTER_OTLP_ENDPOINT` is set, so builds and tests do not need the collector

**Verification:**
- [ ] Manual: as above, with a screenshot or trace id noted in the PR. `./gradlew build` still passes with no collector running

**Dependencies:** Checkpoint C
**Files:** `infra/docker-compose.yml`, `infra/otel/collector.yaml`, `infra/grafana/provisioning/datasources/datasources.yaml`, `build-logic/…/campus.spring-app.gradle.kts`, `core-app/…/PingController.java`
**Scope:** M

### - [ ] Task 14: JSON logs with trace ids → Loki (B2)

**Description:** Use Boot's structured logging (JSON) in all apps, with `traceId` and `spanId` in
every line. Add Loki to the observability profile, ship logs via the Collector, and link Loki ↔
Tempo (derived field) in Grafana.

**Acceptance criteria:**
- [ ] Every log line from the 4 apps is single-line JSON with `traceId` and `spanId` when inside a request
- [ ] In Grafana, the B1 trace links to its log lines, and the log lines link back to the trace

**Verification:**
- [ ] Manual: as above. `./gradlew build` passes (default console format stays readable in tests)

**Dependencies:** T13
**Files:** `platform-starter/…/application-logging.yml` (or the 4 `application.yml` files), `infra/docker-compose.yml`, `infra/otel/collector.yaml`, `infra/grafana/provisioning/datasources/datasources.yaml`
**Scope:** S

### - [ ] Task 15: Metrics, dashboard, alerts (B3)

**Description:** Expose `/actuator/prometheus` on all apps. Add Prometheus to the profile, scraping
all 4 apps and with the alert rules for consumer lag > 30 s and 5xx rate > 1%. Provision a
"Platform overview" Grafana dashboard: request rate, p95 latency per route, 5xx rate, JVM heap, and
Kafka consumer lag.

**Acceptance criteria:**
- [ ] Prometheus targets page shows all 4 apps `UP`
- [ ] Dashboard loads with data for every panel after running `smoke.sh` a few times
- [ ] Alert rules load without errors (`promtool check rules` in a container)

**Verification:**
- [ ] Manual: as above. `promtool check rules infra/prometheus/alerts.yml` exits 0

**Dependencies:** T14
**Files:** `infra/prometheus/prometheus.yml`, `infra/prometheus/alerts.yml`, `infra/grafana/dashboards/platform-overview.json`, `infra/grafana/provisioning/dashboards/dashboards.yaml`, `infra/docker-compose.yml`
**Scope:** M

### ◆ Checkpoint D: Observability
- [ ] B1–B3 ticked. Observability profile memory use noted in the README
- [ ] Human review

---

## Phase 5: Web scaffold, CI, handover

### - [ ] Task 16: `frontend/web` scaffold (P2)

**Description:** Create a Vite + React + TypeScript (strict) app with ESLint, Prettier and Vitest +
Testing Library. Add a dev proxy `/api` → `:8080` and the empty `src/{shell,modules,shared}`
folders. Add a `shared/api/http.ts` client that parses problem JSON into a typed `ProblemError`.
The page shows the core-app ping result. The real shell comes in the `shell` module.

**Acceptance criteria:**
- [ ] `npm --prefix frontend/web run lint`, `test` and `build` all pass
- [ ] `http.ts` turns a 409 problem response into a `ProblemError` carrying `code` and `traceId` (unit test)
- [ ] With the backend up, `npm run dev` shows the ping result from the gateway

**Verification:**
- [ ] The commands above. Manual browser check at http://localhost:5173

**Dependencies:** T12
**Files:** `frontend/web/package.json`, `vite.config.ts`, `src/main.tsx`, `src/shared/api/http.ts`, `src/shared/api/http.test.ts` (+ generated config files)
**Scope:** M

### - [ ] Task 17: GitHub Actions CI (C1)

**Description:** Write `.github/workflows/ci.yml` with backend (JDK 21, Gradle cache,
`./gradlew build`, Docker available for Testcontainers), frontend (Node 24, npm cache,
lint/test/build) and security (Trivy filesystem scan and OWASP Dependency-Check, failing on
HIGH/CRITICAL, suppression file path set). Upload test and coverage reports as artifacts.

**Acceptance criteria:**
- [ ] Workflow passes `actionlint` (run via Docker locally)
- [ ] All 3 jobs run on `push` and `pull_request`. The security job fails on HIGH/CRITICAL findings unless they are listed in the suppression file
- [ ] **After the repo is pushed:** the first run is green on GitHub (closes C1)

**Verification:**
- [ ] Locally: actionlint exits 0. Remotely: green run link recorded in the spec

**Dependencies:** T16
**Files:** `.github/workflows/ci.yml`, `config/dependency-check-suppressions.xml`, `.trivyignore`
**Scope:** S

### - [ ] Task 18: README and ADRs (H1, H2)

**Description:** Write a README covering prerequisites (JDK 21, Node 24, Docker and its memory
needs), the Commands section, ports, the troubleshooting notes collected during T1–T17, and links
to the map and specs. Copy Arch ADR-1…8 into `docs/adr/0001…0008` and write ADRs for D1–D4 and the
Boot 4 decision (Q1).

**Acceptance criteria:**
- [ ] Following only the README, a new clone reaches the Checkpoint C state (H1)
- [ ] `docs/adr/` contains 13 ADRs in one consistent template: context, decision, consequences, status (H2)

**Verification:**
- [ ] Manual: fresh clone into a temp folder → follow the README → `smoke.sh` passes

**Dependencies:** Checkpoint D (can be drafted earlier)
**Files:** `README.md`, `docs/adr/0001-…md` … `docs/adr/0013-…md`
**Scope:** M (docs only, so the file count limit does not apply)

### ◆ Checkpoint E: `platform` done
- [ ] Every success criterion in SPEC-platform.md is ticked. C1 may stay pending until the first push
- [ ] Fresh-clone P1 run is green
- [ ] Spec status → DONE. Map index updated. `tasks/` archived to `tasks/archive/platform/`
- [ ] Human sign-off → start `SPEC-identity.md`

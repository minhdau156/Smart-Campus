# Spec: `platform`

> **Module id:** `platform` (module 1 of 14 in [CAPABILITY-MAP.md](CAPABILITY-MAP.md))
> **Depends on:** nothing. **Every other module depends on this one.**
> **Status:** APPROVED 2026-11-17 · Plan: [tasks/plan.md](tasks/plan.md) · Tasks: [tasks/todo.md](tasks/todo.md)
> **Stories:** NFR-4 (dashboards and alerts, baseline only). NFR-6 is deferred to the shipping phase (map C18).
> **Resolves:** C2 (Kafka only), C4 (no cross-schema FKs; enforcement), C6 (outbox, revised),
> C12 (app containers), C13 (OpenAPI + generated types), C14 (per-IP rate limit), C15 (topic keys),
> C16 (Kafka-only cross-module consumption, replayable topics)

## Objective

Build the empty skeleton that every feature module plugs into. When this module is done, a
developer on a fresh Windows/macOS/Linux machine can:

1. start all the infrastructure with one command,
2. build and test every backend service and the web app with one command each,
3. publish a domain event from one module and consume it in another service without losing it
   or applying it twice,
4. follow one request across gateway → service → Kafka → consumer in a trace viewer.

None of this is visible to students. Its users are the developers building modules 2–14 and the
CI pipeline. Success means **module 2 (`identity`) can start immediately and never touches
build, infra, eventing, or error-handling plumbing.**

### In scope

| Area | Deliverable |
|---|---|
| Monorepo | Layout from Arch §9, root build, version catalog, formatting, `.editorconfig`, `.gitignore`, `README.md` |
| `shared-kernel` | Typed ids, `Money`, time (`TimeRange` with overlap check, test clocks), `DomainEvent` envelope, `Topics` (name, key, partitions), `ProblemCode` error model (Arch §7: "ids, money, time") |
| Backend apps (empty) | `core-app` (Spring Modulith, no feature modules yet), `registration-service`, `notification-service`, `gateway`. Each starts, reports health, and exposes metrics |
| Persistence | PostgreSQL with one schema per module. Flyway per app. Each extracted service uses a DB user limited to its own schema |
| Eventing | Transactional outbox → Kafka. Event envelope. Per-topic message keys (Arch §6). Cross-module consumption only from Kafka, even inside `core-app`. Event topics with unlimited retention, plus a replay procedure. Idempotent-consumer support. Trace context in Kafka headers |
| Errors | RFC 7807 problem JSON with `code` and `traceId`, the same in all apps |
| API docs | springdoc OpenAPI 3 in every app, one group per Modulith module in `core-app` (Arch §7). OpenAPI JSON exported to `docs/api/` by a test and committed. `openapi-typescript` generates frontend types from it |
| Gateway | Spring Cloud Gateway: `/api/v1/registration/**` → registration-service, `/api/v1/notification/**` and `/ws/**` → notification-service, all other `/api/v1/**` → core-app. **Per-IP rate limiting** (Redis), returning 429 with `Retry-After` and problem JSON (Arch §7) |
| Container images | One shared Dockerfile (layered Spring Boot jar, pinned JRE 21 base, non-root, healthcheck) used for all 4 apps. A Compose `apps` profile runs them (Arch §8 dev column) |
| Web app (empty) | Vite + React + TS app in `frontend/web` that builds, lints, tests, proxies `/api` to the gateway in dev, and uses the generated API types |
| Local infra | `infra/docker-compose.yml`: Postgres, Redis, Kafka (KRaft), Keycloak (empty), OpenSearch, MinIO, Mailpit. Profile `apps` adds the 4 backend apps. Profile `observability` adds OTel Collector, Tempo, Prometheus, Loki, Grafana |
| Observability | OTel Java agent in every app, Micrometer → Prometheus, JSON logs with `traceId`, one provisioned "Platform overview" Grafana dashboard |
| CI | GitHub Actions: backend build + unit + Modulith `verify()` + Testcontainers ITs; frontend lint + test + build; Trivy and OWASP Dependency-Check |

### Out of scope (owned elsewhere)

- Keycloak realm, roles, JWT validation, `@PreAuthorize` → `identity`
- Module manifests, `GET /api/v1/modules`, disabled-module 404 filter, React shell/menu, i18n → `shell`
- Per-user rate limiting, waiting room, Redis seat scripts, k6 → `registration`
- WebSocket/STOMP endpoint → `notification`. The gateway route exists, but nothing serves it yet
- Any domain table, endpoint, or UI screen
- Kubernetes, Helm, CDN, image registry push, zero-downtime deploys (NFR-6), production deploy → a later shipping phase (map assumption 2, C18)

## Tech Stack

The docs fix the stack. Exact versions are pinned once, in `gradle/libs.versions.toml` and
`frontend/web/package.json`, during task 1, using the newest GA release at that time. No floating
versions (`latest`, `+`, `^` on majors) anywhere, including Docker image tags.

| Concern | Choice | Notes |
|---|---|---|
| Language | Java 21 (LTS) | Already installed on this machine: 21.0.13 |
| Framework | Spring Boot 4.x + Spring Modulith 2.x + Spring Cloud Gateway (server-webflux) | Q1. Boot 4 ships Jackson 3 (`tools.jackson.*`) |
| Build | Gradle (Kotlin DSL) multi-project, version catalog, wrapper committed | Q2 |
| Migrations | Flyway | One history table per schema |
| Messaging | Apache Kafka (KRaft), Spring for Apache Kafka | C2 |
| Event outbox | Spring Modulith Event Publication Registry (JDBC) + Kafka externalization | See Design decisions D1 |
| Testing | JUnit 5, AssertJ, Spring Modulith test, Testcontainers (Postgres, Kafka, Redis), ArchUnit | |
| API docs | springdoc-openapi (Boot 4 compatible line), `openapi-typescript` (web) | C13 |
| Images | Dockerfile using Spring Boot layered jar on a pinned Eclipse Temurin 21 JRE base | C12. Buildpacks rejected: harder to add the OTel agent and to read |
| Formatting | Spotless + palantir-java-format (Java), ESLint + Prettier (TS) | |
| Web | Node 24 LTS, npm, Vite, React, TypeScript (strict), Vitest + Testing Library | Already installed: Node 24.22, npm 11.19 |
| Containers | Docker Compose v2 | Already installed: Docker 29.9, Compose 5.6 |
| Observability | OpenTelemetry Java agent, OTel Collector, Tempo, Prometheus, Loki, Grafana | Tempo instead of Jaeger so that Grafana is the single UI |
| Mail (dev) | Mailpit | Stand-in for SMTP (map assumption 4) |

## Commands

Run all commands from the repo root. On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`.

```bash
# Infrastructure
docker compose -f infra/docker-compose.yml up -d --wait            # core infra, blocks until healthy
docker compose -f infra/docker-compose.yml --profile observability up -d --wait
docker compose -f infra/docker-compose.yml down -v                  # wipe all local data

# Backend
./gradlew build                                   # compile + spotlessCheck + unit + modulith verify + ITs
./gradlew test                                    # unit tests only (fast, no Docker)
./gradlew integrationTest                         # Testcontainers ITs (needs Docker)
./gradlew spotlessApply                           # format
./gradlew :backend:core-app:bootRun               # port 8081
./gradlew :backend:registration-service:bootRun   # port 8082
./gradlew :backend:notification-service:bootRun   # port 8083
./gradlew :backend:gateway:bootRun                # port 8080 (single entry point)

# Apps as containers (Arch §8 dev setup). Stop any bootRun apps first: same ports
./gradlew bootJar
docker compose -f infra/docker-compose.yml --profile apps up -d --build --wait

# API docs
./gradlew exportOpenApi                           # refreshes docs/api/*.json (commit the diff)
#   live: http://localhost:8081/swagger-ui.html (core-app; one group per module)

# Frontend
npm --prefix frontend/web ci
npm --prefix frontend/web run gen:api             # docs/api/*.json → src/shared/api/generated/
npm --prefix frontend/web run dev                 # http://localhost:5173, /api proxied to :8080
npm --prefix frontend/web run lint
npm --prefix frontend/web run test                # vitest run
npm --prefix frontend/web run build

# Replay a read model (dev): drop its state, reset its consumer group, restart it
./infra/replay.sh <consumer-group> <schema>.<table>[,…]

# Smoke check (all apps running)
curl -s localhost:8080/api/v1/platform/ping       # → core-app via gateway
curl -s localhost:8080/api/v1/registration/ping   # → registration-service via gateway
curl -s localhost:8080/api/v1/notification/ping   # → notification-service via gateway
```

Local ports: gateway 8080, core-app 8081, registration 8082, notification 8083, Postgres 5432,
Redis 6379, Kafka 9092, Keycloak 8180, OpenSearch 9200, MinIO 9000 (console 9001), Mailpit 8025,
Grafana 3000.

## Project Structure

```
smart-campus/                          (repo root = C:\Smart Campus)
├── CAPABILITY-MAP.md  SPEC-*.md        specs (index = capability map)
├── settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml  gradlew(.bat)
├── build-logic/                        convention plugins: java-conventions, spring-app, integration-test
├── backend/
│   ├── shared-kernel/                  plain Java library, no Spring Boot app. Ids, Money, time, events, topics, errors
│   │   └── src/main/java/edu/campus/kernel/{id,money,time,event,error}/
│   ├── platform-starter/               Spring auto-config shared by all 4 apps: problem handler,
│   │                                   outbox/externalization defaults, idempotent consumer, logging
│   ├── core-app/
│   │   └── src/main/java/edu/campus/
│   │       ├── CoreApplication.java
│   │       └── platform/               "platform" Modulith module: /api/v1/platform/ping only
│   ├── registration-service/           edu.campus.registration (empty + ping)
│   ├── notification-service/           edu.campus.notification (empty + ping)
│   └── gateway/                        edu.campus.gateway (routes only)
├── frontend/web/                       Vite app; src/{shell,modules,shared}/, shared/api/generated/ (do not edit)
├── infra/
│   ├── docker-compose.yml              profiles: (default) infra · apps · observability
│   ├── docker/spring-app.Dockerfile    one image recipe for all 4 apps
│   ├── postgres/init/                  creates schemas + per-service DB users
│   ├── otel/  prometheus/  grafana/{provisioning,dashboards}/
│   ├── smoke.sh  replay.sh
├── docs/                               source PDFs
│   ├── api/                            exported OpenAPI JSON per app/group (committed)
│   └── adr/                            ADR-0001… (Arch decisions 1–8 copied in + D1–D6 + Boot 4)
└── .github/workflows/ci.yml
```

Conventions:
- **One Modulith module = one top-level package** under `edu.campus` in `core-app`. Its API is the
  package root. `internal` sub-packages are private, and `verify()` enforces this.
- **Migrations:** `src/main/resources/db/migration/<schema>/V<yyyyMMddHHmm>__<what>.sql`. A module's
  migrations only create or alter objects in its own schema.
- **Tests:** unit tests in `src/test/java`, integration tests in `src/integrationTest/java`
  (separate source set and Gradle task), Testcontainers shared via `@ServiceConnection`.

## Code Style

```java
// shared-kernel — immutable, no framework annotations, validated in the constructor
package edu.campus.kernel.event;

public record DomainEvent<P>(
        UUID eventId,
        String type,            // "EnrollmentConfirmed": past tense, PascalCase
        int schemaVersion,      // bump on breaking payload change (see Q4)
        Instant occurredAt,
        String aggregateId,     // "section-1234". NOT automatically the Kafka key: see Topics (D6)
        P payload) {

    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
    }
}
```

```java
// Error model — every 4xx/5xx body is RFC 7807 plus a stable machine code
throw new CampusProblem(ProblemCode.SECTION_FULL, "Section %s has no seats left".formatted(id));
// → 409 {"type":"https://campus.edu/problems/section-full","title":"Section full",
//        "status":409,"detail":"…","code":"SECTION_FULL","traceId":"4bf92f…"}
```

Rules:
- Java: packages `edu.campus.<module>`. Records for DTOs, events, and value objects. Constructor
  injection only. No Lombok. `var` only when the type is obvious. Use `Clock` and never call
  `Instant.now()` directly.
- REST: paths and pagination follow the Arch §7 API conventions table. JSON is camelCase.
  Timestamps are UTC ISO-8601.
- SQL: snake_case. Plural table names. `id UUID PRIMARY KEY`. `created_at timestamptz NOT NULL`.
  **Foreign keys only within the same schema** (C4).
- Events: past-tense names. The payload carries ids and the changed facts, never whole entities
  from another module.
- TS: `strict: true`, no `any`, function components, named exports, file names in kebab-case.
- Commits: Conventional Commits (`feat(platform): …`, `test(registration): …`).

## Design decisions (each becomes an ADR in `docs/adr/` during implementation)

| # | Decision | Rationale | Rejected alternative |
|---|---|---|---|
| D1 | **Outbox = Spring Modulith Event Publication Registry (JDBC) + Kafka externalization.** Events are written to `event_publication` in the business transaction. They are published to Kafka after commit. Incomplete publications are retried on startup and on a schedule. | Satisfies ADR-5 (no lost or phantom events) without writing our own relay. Works the same in all three backend apps. | Hand-written `outbox_events` table + `@Scheduled` relay (more code, same guarantees). Debezium (more infra). |
| D2 | **Consumers deduplicate by `eventId`.** Each consuming module has a `<schema>.processed_events(event_id PK, processed_at)` row, inserted in the same transaction as the side effect. A duplicate key means skip. | Kafka delivers at least once. This makes delivery effectively once per consumer. | Kafka exactly-once transactions (do not cover DB side effects). |
| D3 | **Schema isolation is enforced twice.** (a) Modulith `verify()` + an ArchUnit rule ban cross-module JPA/JDBC access in `core-app`. (b) `registration-service` and `notification-service` connect as DB users with privileges only on their own schema. | A schema violation fails the build in core-app and fails at runtime in the extracted services. | Separate DB users per core-app module (one datasource makes this impractical). |
| D4 | **Event envelope adds `schemaVersion`** to the Arch §6 envelope. The W3C `traceparent` travels in Kafka headers, not in the payload. | Allows payloads to evolve. Tracing stays out of the business data. | Version encoded in the type name (`EnrollmentConfirmedV2`). |
| D5 | **Cross-module events are consumed only from Kafka**, even between modules inside `core-app`. In-process listeners (`@ApplicationModuleListener`, `@EventListener`, `@TransactionalEventListener`) may only handle the module's *own* events, and an ArchUnit rule enforces this. Event topics use `retention.ms=-1`. **Replay** = delete the consumer's derived tables and its `processed_events`, reset the consumer group to the earliest offset, restart. Only read-model consumers can be replayed. Consumers with external side effects (email, push) are registered as non-replayable, and `replay.sh` refuses them. | Arch §3 (all cross-module changes via Kafka) and Arch §4 (read models can be dropped and replayed). In-process delivery cannot be replayed. | In-process Modulith listeners inside `core-app` (faster, but no replay and two delivery paths). Compacted topics (lose history per key). |
| D6 | **The message key is defined per topic** in `shared-kernel` `Topics`, as in the Arch §6 table: `registration.requests` → sectionId, `registration.events` → studentId, `course.events` → sectionId, `grade.events` → studentId, `campus.events` → aggregateId. The publisher must supply the key, and a missing key fails publication. | Ordering is guaranteed per key, and Arch §6 chose these keys for that reason (e.g. waitlist ordering per section). | Always keying by `aggregateId` (wrong for `registration.events` and `grade.events`). |

**D1 storage note (map C6, revised):** with the Modulith registry, each app has *one*
`event_publication` table, owned by `platform` (in `core-app`, in schema `platform`; in each
extracted service, in its own schema). It is written in the same transaction as the business row,
because each app has one datasource.

**D1 must be verified in task 7.** If any outbox success criterion (O1–O4) cannot be met with the
Modulith registry, fall back to the hand-written outbox and update D1 before going further.

## Testing Strategy

| Level | Tool | Lives in | Covers | Runs |
|---|---|---|---|---|
| Unit | JUnit 5 + AssertJ | `src/test/java` | `shared-kernel` value objects, problem mapping, envelope serialization | `./gradlew test`, every commit |
| Architecture | Spring Modulith `ApplicationModules.verify()` + ArchUnit | `core-app/src/test` | Module boundaries, no `internal` leaks, no cross-schema repository access, no in-process listeners on other modules' events (D5) | `./gradlew test` |
| Integration | Testcontainers (Postgres, Kafka, Redis) + `@SpringBootTest` / `@ApplicationModuleTest` | `src/integrationTest/java` | Outbox O1–O6, Flyway per schema, DB-user isolation (D3), problem JSON end to end, gateway routing and rate limit, OpenAPI export | `./gradlew integrationTest`, CI |
| Smoke | `curl` script | `infra/smoke.sh` | All apps reachable through the gateway, run against both `bootRun` apps and the `apps` Compose profile | Manual, before closing the module |
| Frontend | Vitest + Testing Library | `frontend/web/src/**/*.test.tsx` | App renders. API client parses problem JSON. Generated types are up to date (CI `git diff --exit-code`) | `npm run test` |

Coverage: JaCoCo report on every build. **`shared-kernel` ≥ 90% line coverage, enforced.** Other
platform code has no gate yet, because each module spec sets its own. Flaky tests are fixed or
quarantined with an issue link. They are never silently deleted.

## Success Criteria

**Repo and build**
- [ ] P1. A fresh clone with JDK 21, Node 24, and Docker can run `./gradlew build` with no other
  setup. The build passes, including `integrationTest` and `spotlessCheck`.
- [ ] P2. `npm --prefix frontend/web ci && npm --prefix frontend/web run lint test build` passes.
- [ ] P3. Adding a class in `core-app` that imports another module's `internal` package makes
  `./gradlew test` fail with a Modulith violation. Proven by a test fixture that is kept in the repo.

**Infrastructure**
- [ ] I1. `docker compose -f infra/docker-compose.yml up -d --wait` returns with every container
  healthy in under 3 minutes on a warm image cache.
- [ ] I2. Postgres contains the schemas `identity, course, timetable, registration, grade,
  announcement, club, marketplace, lostfound, notification, payment, platform`. User
  `registration_svc` can read and write only `registration`. User `notification_svc` can read and
  write only `notification`. Both proven by IT.
- [ ] I3. All 4 apps start against Compose. `GET /actuator/health` returns `UP` on each. The three
  `/ping` routes in Commands answer through the gateway on :8080.

**Eventing (outbox)**
- [ ] O1. A test event saved in the same transaction as a business row appears on Kafka with the
  full envelope (D4) and the key defined for its topic (D6).
- [ ] O2. If that transaction rolls back, nothing is published, after waiting at least 2× the relay
  interval.
- [ ] O3. If the app is killed after commit but before Kafka acknowledges, the event is published
  after restart.
- [ ] O4. Delivering the same event twice to a consumer causes the side effect exactly once (D2).
- [ ] O5. The topics from Arch §6 (`registration.requests` 12 partitions, `registration.events` 6,
  `course.events` 3, `grade.events` 3, `campus.events` 3) are declared in code by their producing
  app and exist after startup with `retention.ms=-1`. The broker does not auto-create topics.
  Publishing to a topic without the key required by D6 fails.
- [ ] O6. Replay: after 10 probe events, running `infra/replay.sh` on the probe consumer rebuilds
  exactly the same 10 rows. A `core-app` class with an in-process listener on another module's
  event fails `./gradlew test` (D5).

**API docs**
- [ ] A1. Each app serves `/v3/api-docs`. `core-app` has one OpenAPI group per Modulith module.
  `./gradlew exportOpenApi` writes `docs/api/*.json`, and `npm run gen:api` turns it into TS types
  that the web app compiles against.

**Gateway rate limiting**
- [ ] R1. Exceeding the per-IP limit (default 20 req/s, burst 40, set in config) on any `/api/v1/**`
  route returns 429 problem JSON (`code: RATE_LIMITED`) with a `Retry-After` header. Requests under
  the limit are unaffected. The limiter state lives in Redis, so it is shared across gateway replicas.

**Containers**
- [ ] K1. `./gradlew bootJar` followed by `docker compose … --profile apps up -d --build --wait`
  brings all 4 apps up healthy, and `infra/smoke.sh` passes against them. Images run as non-root.

**Errors and API**
- [ ] E1. An unhandled exception, a validation failure, a `CampusProblem`, and an unknown route
  each return `application/problem+json` with `type, title, status, detail, code, traceId`. Stack
  traces never appear in the response.

**Observability**
- [ ] B1. One request to `/api/v1/platform/ping?emit=true` (test-only flag, dev profile only)
  shows in Grafana/Tempo as one trace spanning gateway → core-app → Kafka → a consumer in
  notification-service.
- [ ] B2. Every app logs single-line JSON containing `traceId` and `spanId`. Loki can find these
  logs by `traceId`.
- [ ] B3. Prometheus scrapes all 4 apps. The provisioned "Platform overview" dashboard shows
  request rate, p95 latency per route, 5xx rate, JVM memory, and Kafka consumer lag. The Arch §8
  alert rules (lag > 30 s, 5xx > 1%) exist in Prometheus. The seat-sync alert is added by `registration`.

**CI**
- [ ] C1. `.github/workflows/ci.yml` runs P1, P2, a check that generated API types are up to date, Trivy (filesystem + the 4 app images), and OWASP
  Dependency-Check on every push and PR. Any HIGH/CRITICAL finding fails the job unless it is
  listed in a reviewed suppression file. *Verifiable once the repo is pushed to GitHub (Q3).*

**Handover**
- [ ] H1. `README.md` gets a new developer from clone to the I3 state using only the Commands
  section.
- [ ] H2. `docs/adr/` contains Arch ADR-1…8, D1–D6, and the Boot 4 decision (15 ADRs).

## Boundaries

**Always**
- Run `./gradlew build` (and the frontend checks, if touched) before saying a task is done.
- Pin every dependency and image version. Update `libs.versions.toml` and nothing else for versions.
- Keep each schema owned by exactly one module. Use FKs only inside a schema.
- Send every cross-module change through an event or a published module API (Arch §4).
- Use problem JSON for every error response.
- Update this spec first when a decision in it changes.

**Ask first**
- Adding a dependency that is not in the Tech Stack table.
- Changing a Design decision (D1–D6), the event envelope, topic names, keys, partition counts, or retention.
- Changing a port, schema name, or DB user.
- Adding infrastructure containers beyond those listed.
- Lowering a coverage gate, adding a Trivy/Dependency-Check suppression, or disabling a check.
- `git init`, creating a GitHub repo, or pushing anything.

**Never**
- Commit secrets. Compose uses dev-only credentials from `infra/.env.example`, with a real `.env`
  that is gitignored.
- Use `latest` or floating version ranges.
- Read or write another module's tables, even "just for a test".
- Delete or `@Disabled` a failing test to get to green.
- Build domain features (tables, endpoints, screens) inside `platform`.

## Resolved Questions (2026-11-17)

| # | Question | Decision |
|---|---|---|
| Q1 | Spring Boot line | **Spring Boot 4.x** (current GA), with the matching Spring Modulith 2.x and Spring Cloud release train. This changes the Arch doc's "Spring Boot 3" and is recorded as an ADR. |
| Q2 | Build tool | **Gradle (Kotlin DSL)** |
| Q3 | GitHub repo | **The user creates it.** I write `ci.yml` locally. C1 is verified once the repo exists and is pushed. |
| Q4 | `schemaVersion` in the envelope | Accepted (default; not objected to) |
| Q5 | Tempo instead of Jaeger | **Tempo** |

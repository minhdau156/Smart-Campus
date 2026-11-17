# Implementation Plan: `platform` module

> **Spec:** [SPEC-platform.md](../SPEC-platform.md) (approved 2026-11-17, revised after the second
> review against the PDFs: map C12–C18, spec D5–D6) · **Map:** [CAPABILITY-MAP.md](../CAPABILITY-MAP.md)
> **Status:** APPROVED 2026-11-17 · **Tasks:** [todo.md](todo.md) is the index and progress
> tracker. Each task's details are in [`tasks/platform/task<N>.md`](platform/). No external tracker.
> When `platform` is done, `plan.md`, `todo.md` and `platform/` move to `tasks/archive/platform/`,
> and `identity` gets fresh ones.

## Overview

Build the empty, runnable skeleton described in the spec: a Gradle monorepo with
`shared-kernel`, 4 Spring Boot 4 apps (`core-app`, `registration-service`,
`notification-service`, `gateway`) runnable via Gradle and as Compose containers, a transactional
outbox to Kafka with per-topic keys and replayable topics, RFC 7807 errors, OpenAPI docs with
generated frontend types, per-IP rate limiting at the gateway, OTel/Tempo/Loki/Prometheus/Grafana,
a Vite web scaffold, and GitHub Actions CI. The work is 21 tasks in 5 phases, with a human
checkpoint after each phase.

## Architecture decisions carried into the plan

- **Boot 4 / Modulith 2 / Spring Cloud for Boot 4.** Exact versions are taken from official
  release notes in T1, not from memory. They are recorded in an ADR as a change from the Arch doc's
  "Spring Boot 3".
- **The outbox (D1) is the riskiest choice, so it is built second (T6–T7).** If Modulith's
  publication registry fails O1–O3, Checkpoint B switches to a hand-written outbox before anything
  is built on top.
- **Kafka is the only cross-module channel (D5), and keys are defined per topic (D6).** These are
  settled in T12, before any feature module exists. Retrofitting them later would mean touching
  every module.
- **Slices are runnable end to end.** Every task leaves `./gradlew build` green and something you
  can start or curl.
- **Infra (T3) is independent of the Gradle work (T1–T2)** and can be done in parallel.

## Dependency graph

```
T1 Gradle root ─→ T2 conventions ─→ T4 kernel types+time ─→ T5 kernel events+topics ─┐
T3 Compose infra (parallel) ─────────────────────────────────────────────────────────┤
                                                                                     ▼
                         T6 core-app skeleton ─→ T7 outbox spike (O1–O3)   ◆ Checkpoint B (D1 gate)
                                                        │
                                   T8 problem JSON ─→ T9 OpenAPI docs
                                         │
                        ┌────────────────┴────────────────┐
                        ▼                                 ▼
               T10 registration-svc            T11 notification-svc + idempotent consumer (O4)
                        └────────────────┬────────────────┘
                                         ▼
                   T12 topics: keys, retention, Kafka-only rule, replay (O5, O6)
                                         ▼
                   T13 gateway routes + smoke ─→ T14 per-IP rate limit ─→ T15 app images + `apps` profile
                                         ▼                                       ◆ Checkpoint C
                   T16 traces ─→ T17 logs ─→ T18 metrics/dashboard/alerts        ◆ Checkpoint D
                                         ▼
                   T19 web scaffold + generated types · T20 CI · T21 README + ADRs   ◆ Checkpoint E
```

## Task list (details, acceptance criteria and verification in [`tasks/platform/`](platform/))

### Phase 1: Foundation
- [ ] T1 Gradle root, wrapper, version catalog (pinned Boot 4 line)
- [ ] T2 Build conventions: Spotless, JaCoCo, `integrationTest` source set
- [ ] T3 Compose core infra + schemas + per-service DB users *(parallel with T1–T2)*
- [ ] T4 `shared-kernel`: typed ids, `Money`, time (`TimeRange`), `ProblemCode`
- [ ] T5 `shared-kernel`: `DomainEvent` envelope + `Topics` (name, key, partitions)

**Checkpoint A:** `./gradlew build` green, Compose healthy, kernel ≥ 90% coverage, versions reviewed.

### Phase 2: First app + outbox risk spike
- [ ] T6 `core-app` skeleton: ping, Flyway, Modulith `verify()`, boundary-violation fixture (P3)
- [ ] T7 Outbox spike in `core-app`: O1, O2, O3 as integration tests

**Checkpoint B (decision gate):** D1 confirmed or replaced. Human review before Phase 3.

### Phase 3: Services, errors, events, routing, containers
- [ ] T8 `platform-starter`: RFC 7807 problem handler (E1)
- [ ] T9 OpenAPI: springdoc, one group per module, `exportOpenApi` → `docs/api/` (A1, backend part)
- [ ] T10 `registration-service` skeleton + schema-restricted DB user (D3)
- [ ] T11 `notification-service` skeleton + idempotent consumer (O4)
- [ ] T12 Topics: per-topic keys, unlimited retention, Kafka-only cross-module rule, replay (O5, O6)
- [ ] T13 `gateway` routes + `infra/smoke.sh` (I3)
- [ ] T14 Gateway per-IP rate limiting (R1)
- [ ] T15 App container images + Compose `apps` profile (K1)

**Checkpoint C:** 4 apps up both via `bootRun` and as containers, smoke green both ways, full build green.

### Phase 4: Observability
- [ ] T16 OTel agent + Collector + Tempo, trace across Kafka (B1)
- [ ] T17 JSON logs with trace ids → Loki (B2)
- [ ] T18 Prometheus scrape, "Platform overview" dashboard, alert rules (B3)

**Checkpoint D:** one trace, its logs and its metrics can all be found in Grafana, for both run modes.

### Phase 5: Web scaffold, CI, handover
- [ ] T19 `frontend/web` scaffold + generated API types + problem-JSON client (P2, A1 frontend part)
- [ ] T20 GitHub Actions CI + Trivy (fs + images) + Dependency-Check (C1)
- [ ] T21 README (H1) + 15 ADRs (H2)

**Checkpoint E (module done):** every spec success criterion checked, plus a fresh-clone run of P1.

## Parallelization

| Can run in parallel | Must be sequential |
|---|---|
| T3 with T1–T2. T10 and T11 after T9. T19 and T21 drafts any time after Checkpoint C. | T1→T2→T4→T5→T6→T7 (each builds on the last). T13→T14→T15 (gateway then images). T16→T17→T18 (they share the observability profile). |

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Modulith publication registry marks an event complete before Kafka acks, or does not republish after a crash (O3) | **High.** Lost events would break registration correctness | Built second (T7) with a crash-simulation IT. Checkpoint B can switch to a hand-written outbox (spec D1 fallback). |
| Modulith Kafka externalization cannot set a per-topic key from something other than the event (D6) | Med | T12 tests every key from the Arch §6 table. If the externalizer cannot do it, the key is a field in the payload and is resolved by a routing SpEL expression. Fallback: our own `KafkaTemplate` relay on top of the registry. |
| Unlimited retention fills the dev disk, or a replay re-sends notifications | Med | Dev Compose caps the broker log size. Replay resets the consumer's *derived* state only; side-effecting consumers (email/push) are marked non-replayable in `Topics` docs and `replay.sh` refuses them. |
| Boot 4 ecosystem gaps: Spring Cloud Gateway artifact renames (`…-gateway-server-webflux`), Jackson 3 package move, OTel agent and springdoc support for Boot 4 | Med | T1 checks every version against official release notes and compatibility matrices before pinning. Each app's first task proves it starts. |
| Path with a space (`C:\Smart Campus`) breaks Gradle, Node or Docker bind mounts on Windows | Med | T1/T3 run the full build and `compose up` from this path early. If something breaks, the fix is to move the repo to a path without spaces (ask first). |
| Testcontainers + Docker Desktop on Windows is slow or flaky | Med | Shared static containers via `@ServiceConnection`. ITs in a separate `integrationTest` task so `./gradlew test` stays fast. |
| Compose stack (infra + 4 apps + observability) exceeds Docker Desktop memory | Med | `apps` and `observability` are opt-in profiles. JVM heap capped per container, OpenSearch heap 512m. T15/T18 record the memory needed in the README. |
| CI cannot be verified until the GitHub repo exists | Low | `ci.yml` is linted locally (actionlint via Docker). C1 is closed only after the first push. |

## Open questions

1. **Repo location:** will you clone your new GitHub repo into a fresh folder, or should I
   `git init` here in `C:\Smart Campus` and add your repo as `origin`? Either way, I will only push
   when you ask.
2. **Consumer lag in seconds (T18.3):** the Arch alert "lag over 30 s" needs lag measured in
   time. The Kafka client metric counts messages, so this likely needs one extra lag-exporter
   container. Decide at T18.
3. **NVD API key (T20.5):** OWASP Dependency-Check needs an `NVD_API_KEY` GitHub secret to run in a
   reasonable time. You add it when the repo exists.

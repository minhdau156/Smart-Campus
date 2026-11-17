# Implementation Plan: `platform` module

> **Spec:** [SPEC-platform.md](../SPEC-platform.md) (approved 2026-11-17) · **Map:** [CAPABILITY-MAP.md](../CAPABILITY-MAP.md)
> **Status:** DRAFT, awaiting review · **Tasks:** [todo.md](todo.md) (the task list target; no external tracker)
> When `platform` is done, both files move to `tasks/archive/platform/` and `identity` gets fresh ones.

## Overview

Build the empty, runnable skeleton described in the spec: a Gradle monorepo with
`shared-kernel`, 4 Spring Boot 4 apps (`core-app`, `registration-service`,
`notification-service`, `gateway`), a Docker Compose stack, a transactional outbox to Kafka,
RFC 7807 errors, OTel/Tempo/Loki/Prometheus/Grafana, a Vite web scaffold, and GitHub Actions CI.
The work is 18 tasks in 5 phases, with a human checkpoint after each phase.

## Architecture decisions carried into the plan

- **Boot 4 / Modulith 2 / Spring Cloud for Boot 4.** Exact versions are taken from official
  release notes in Task 1, not from memory. They are recorded in `gradle/libs.versions.toml` and
  in an ADR that notes the change from the Arch doc's "Spring Boot 3".
- **The outbox (D1) is the riskiest choice, so it is built second.** It comes right after the
  first runnable app (Tasks 6–7). If Modulith's publication registry fails any of O1–O3, Checkpoint B
  switches to a hand-written outbox before anything else is built on top.
- **Slices are runnable end to end.** Every task leaves `./gradlew build` green and something you
  can start or curl. No task builds a layer nobody can run yet.
- **Infra (Task 3) is independent of the Gradle work (Tasks 1–2)** and can be done in parallel.

## Dependency graph

```
T1 Gradle root ─→ T2 conventions ─→ T4 kernel types ─→ T5 kernel events ─┐
T3 Compose infra (parallel) ──────────────────────────────────────────────┤
                                                                          ▼
                               T6 core-app skeleton ─→ T7 outbox spike (O1–O3)  ◆ Checkpoint B (D1 gate)
                                        │
             ┌──────────────────────────┼──────────────────────┐
             ▼                          ▼                      ▼
     T8 problem JSON          T9 registration-svc       T10 notification-svc + idempotent consumer (O4)
             │                          │                      │
             └────────────┬─────────────┴──────────┬───────────┘
                          ▼                        ▼
                 T11 topic declarations    T12 gateway + smoke (I3)
                          └───────────┬────────────┘
                                      ▼
            T13 traces (B1) ─→ T14 logs (B2) ─→ T15 metrics/dashboard/alerts (B3)
                                      ▼
            T16 web scaffold (P2) · T17 CI (C1) · T18 README + ADRs (H1, H2)
```

## Task list (details, acceptance criteria and verification in [todo.md](todo.md))

### Phase 1: Foundation
- [ ] T1 Gradle root, wrapper, version catalog (pinned Boot 4 line)
- [ ] T2 Build conventions: Spotless, JaCoCo, `integrationTest` source set
- [ ] T3 Compose core infra + schemas + per-service DB users  *(parallel with T1–T2)*
- [ ] T4 `shared-kernel`: typed ids, `Money`, `ProblemCode`
- [ ] T5 `shared-kernel`: `DomainEvent` envelope + JSON round-trip

**Checkpoint A:** `./gradlew build` green, Compose healthy, kernel ≥ 90% coverage.

### Phase 2: First app + outbox risk spike
- [ ] T6 `core-app` skeleton: ping, Flyway, Modulith `verify()`, boundary-violation fixture (P3)
- [ ] T7 Outbox spike in `core-app`: O1, O2, O3 as integration tests

**Checkpoint B (decision gate):** D1 confirmed or replaced. Human review before Phase 3.

### Phase 3: Services, errors, routing
- [ ] T8 `platform-starter`: RFC 7807 problem handler (E1)
- [ ] T9 `registration-service` skeleton + schema-restricted DB user (D3)
- [ ] T10 `notification-service` skeleton + idempotent consumer (O4)
- [ ] T11 Kafka topic declarations per producing app (O5)
- [ ] T12 `gateway` routes + `infra/smoke.sh` (I3)

**Checkpoint C:** 4 apps up against Compose, smoke script green, full `./gradlew build` green.

### Phase 4: Observability
- [ ] T13 OTel agent + Collector + Tempo, trace across Kafka (B1)
- [ ] T14 JSON logs with trace ids → Loki (B2)
- [ ] T15 Prometheus scrape, "Platform overview" dashboard, alert rules (B3)

**Checkpoint D:** one trace, its logs and its metrics can all be found in Grafana.

### Phase 5: Web scaffold, CI, handover
- [ ] T16 `frontend/web` scaffold with problem-JSON-aware API client (P2)
- [ ] T17 GitHub Actions CI + Trivy + Dependency-Check (C1)
- [ ] T18 README (H1) + ADRs (H2)

**Checkpoint E (module done):** every spec success criterion checked, plus a fresh-clone run of P1.

## Parallelization

| Can run in parallel | Must be sequential |
|---|---|
| T3 with T1–T2. T8, T9 and T10 after Checkpoint B. T16 and T18 any time after Checkpoint C. | T1→T2→T4→T5→T6→T7 (each builds on the last). T13→T14→T15 (they share the Compose observability profile). |

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Modulith publication registry marks an event complete before Kafka acks, or does not republish after a crash (O3) | **High.** Lost events would break registration correctness | Built second (T7) with a crash-simulation IT. Checkpoint B can switch to a hand-written outbox (spec D1 fallback). |
| Boot 4 ecosystem gaps: Spring Cloud Gateway artifact renames (`…-gateway-server-webflux`), Jackson 3 package move, OTel agent support for Boot 4 | Med | T1 checks every version against official release notes and compatibility matrices before pinning. Each app's first task proves it starts. |
| Path with a space (`C:\Smart Campus`) breaks Gradle, Node or Docker bind mounts on Windows | Med | T1/T3 run the full build and `compose up` from this path early. If something breaks, the fix is to move the repo to a path without spaces (ask first). |
| Testcontainers + Docker Desktop on Windows is slow or flaky | Med | Reuse containers via `@ServiceConnection` and a shared static container per test suite. Keep ITs in a separate `integrationTest` task so `./gradlew test` stays fast. |
| Compose stack (Kafka + OpenSearch + Keycloak + observability) exceeds Docker Desktop memory | Med | Observability is opt-in via a Compose profile. OpenSearch heap is capped at 512m in dev. T3 records the memory needed in the README. |
| CI cannot be verified until the GitHub repo exists | Low | `ci.yml` is linted locally (actionlint via Docker). C1 is closed only after the first push. |

## Open questions

1. **Repo location:** will you clone your new GitHub repo into a fresh folder, or should I
   `git init` here in `C:\Smart Campus` and add your repo as `origin`? Either way, I will only push
   when you ask.

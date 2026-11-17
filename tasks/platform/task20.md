# T20: GitHub Actions CI

> Module `platform` · Phase 5: Web scaffold, CI, handover · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T19](task19.md) · [T21 →](task21.md)

`Scope S · Depends on: T19 · Closes: C1`

**Goal:** every push and PR runs the full build, the frontend checks, the "docs are up to date" checks and the security scans.

**Subtasks**
- [ ] **20.1** Create `.github/workflows/ci.yml`, triggered on `push` and `pull_request`, with older runs on the same branch cancelled. Pin each action to an exact version.
- [ ] **20.2** **backend** job:
  - JDK 21 (Temurin) with Gradle cache;
  - `./gradlew build`;
  - `./gradlew exportOpenApi` + `git diff --exit-code docs/api`;
  - upload test and coverage reports.
- [ ] **20.3** **frontend** job:
  - Node 24 with npm cache;
  - `npm ci`;
  - `gen:api` + `git diff --exit-code`;
  - `lint`, `test`, `build`.
- [ ] **20.4** **security** job:
  - `bootJar`, then build the 4 images;
  - Trivy filesystem + image scans (HIGH/CRITICAL fail, `.trivyignore`);
  - OWASP Dependency-Check with `config/dependency-check-suppressions.xml`.
- [ ] **20.5** 🙋 Ask the human to add an `NVD_API_KEY` repository secret. Without it, Dependency-Check is very slow and its downloads are rate-limited.
- [ ] **20.6** Run `actionlint` locally through its Docker image.
- [ ] **20.7** 🙋 After the repo is pushed, confirm the first run is green and record the link in the spec.

**Done when**
- [ ] All 3 jobs run on push and PR
- [ ] Stale OpenAPI JSON or stale generated types fail CI. HIGH/CRITICAL findings fail CI unless suppressed
- [ ] The first run on GitHub is green (C1)

**Verify**
- [ ] `actionlint` exits 0
- [ ] The green run link is in the spec

**Files:** `.github/workflows/ci.yml`, `config/dependency-check-suppressions.xml`, `.trivyignore`

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

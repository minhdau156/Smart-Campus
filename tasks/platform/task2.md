# T2: Build conventions

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T1](task1.md) · [T3 →](task3.md)

`Scope M · Depends on: T1 · Closes: —`

**Goal:** every Java project gets the same Java version, formatting, tests and coverage by
applying one plugin line.

**Subtasks**
- [ ] **2.1** Create the `build-logic` included build: `settings.gradle.kts` (shares the version
  catalog) and `build.gradle.kts` (`kotlin-dsl`). Add `includeBuild("build-logic")` to the root settings.
- [ ] **2.2** Write `campus.java-conventions`: Java 21 toolchain, UTF-8, `-parameters`, JUnit
  Platform, Spotless with palantir-java-format, a JaCoCo report after `test`.
- [ ] **2.3** Write `campus.integration-test`: an `integrationTest` source set
  (`src/integrationTest/java`) that inherits test dependencies, an `integrationTest` task that runs
  after `test`, and `check` depending on it.
- [ ] **2.4** Write `campus.spring-app`: applies the Boot plugin, imports the Boot BOM, and enables the layered `bootJar`.
- [ ] **2.5** Apply `java-conventions` + `integration-test` to `shared-kernel`. Add one placeholder
  unit test and one placeholder integration test.
- [ ] **2.6** Prove that formatting is enforced: misformat a file → `./gradlew build` fails → revert the file.

**Done when**
- [ ] `./gradlew test` runs only unit tests. `./gradlew integrationTest` runs only ITs. `build` runs both
- [ ] A misformatted Java file fails `spotlessCheck`, and so fails `build`
- [ ] `backend/shared-kernel/build/reports/jacoco/` contains a report

**Verify**
- [ ] `./gradlew test integrationTest --info` shows the two placeholder tests running in separate tasks
- [ ] Step 2.6 was observed (fail, then pass after the revert)

**Files:** `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts`, `build-logic/src/main/kotlin/campus.java-conventions.gradle.kts`, `…/campus.integration-test.gradle.kts`, `…/campus.spring-app.gradle.kts`, `backend/shared-kernel/build.gradle.kts`

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

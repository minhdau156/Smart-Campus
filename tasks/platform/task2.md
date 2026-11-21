# T2: Build rules in the parent pom

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T1](task1.md) · [T3 →](task3.md)

`Scope M · Depends on: T1 · Closes: —`

**Goal:** every Java module gets the same Java version, formatting, tests and coverage just by
having the root `pom.xml` as its parent.

> **Redo (2026-11-20):** T2 was first built with Gradle convention plugins in `build-logic/`
> (commit `d615a1c`). With Maven, the same rules live in the parent pom.

**Subtasks**
- [x] **2.1** In the root `pom.xml`, check the effective pom (`./mvnw help:effective-pom`) for what
  the Boot parent already sets (Java release from `java.version`, UTF-8, `-parameters`), and add
  only what is missing.
- [x] **2.2** Add JUnit Jupiter, AssertJ and the JUnit Platform launcher as `test` dependencies for
  every module (versions from the Boot parent). *Done early in T1: the kept placeholder test needed them.*
- [x] **2.3** Unit tests vs integration tests: `maven-surefire-plugin` runs `*Test` classes in the
  `test` phase. `maven-failsafe-plugin` runs `*IT` classes in `integration-test` and checks them in
  `verify`. A `skipUTs` property (`-DskipUTs`) skips only the unit tests.
- [x] **2.4** `spotless-maven-plugin` with palantir-java-format; the `check` goal runs in `verify`.
  JaCoCo: `prepare-agent`, and a `report` after the unit tests.
- [x] **2.5** Spring app rules for later apps: configure `spring-boot-maven-plugin` in
  `<pluginManagement>` with the layered jar. Apps (T6+) turn it on by listing the plugin.
- [x] **2.6** In `shared-kernel`: keep `KernelPlaceholderTest`, and move `KernelPlaceholderIT` to
  `src/test/java` (same package).
- [x] **2.7** Prove that formatting is enforced: misformat a file → `./mvnw verify` fails → revert the file.

**Done when**
- [x] `./mvnw test` runs only unit tests. `./mvnw verify -DskipUTs` runs only ITs. `./mvnw verify` runs both
- [x] A misformatted Java file fails `spotless:check`, and so fails `./mvnw verify`
- [x] `backend/shared-kernel/target/site/jacoco/` contains a report

**Verify**
- [x] The Maven log shows surefire running `KernelPlaceholderTest` and failsafe running `KernelPlaceholderIT`
- [x] Step 2.7 was observed (fail, then pass after the revert)

**Result (2026-11-21, Maven):** The Boot parent already sets Java 21, UTF-8, `-parameters`, the
plugin versions and the failsafe goals, so the root pom only *turns on* surefire/failsafe and adds
`skipUTs`, Spotless (palantir 2.103.0) and JaCoCo 0.8.16. `spring-boot-maven-plugin` is configured
(layers on) in `<pluginManagement>`, so it stays off until an app lists it (first in T6). ITs and
unit tests share `src/test/java`; the class name (`*Test` / `*IT`) decides which plugin runs it.

**Files:** `pom.xml` (build section), `backend/shared-kernel/src/test/java/edu/campus/kernel/KernelPlaceholderTest.java`, `…/KernelPlaceholderIT.java`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

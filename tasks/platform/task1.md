# T1: Gradle root, wrapper, version catalog

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> ← (first task) · [T2 →](task2.md)

`Scope M · Depends on: — · Closes: —`

**Goal:** an empty Gradle multi-project that builds, with every Boot 4 dependency version pinned
in one place.

**Subtasks**
- [ ] **1.1** Look up the current GA version and the official compatibility page for each of
  these: Spring Boot 4.x; the Spring Cloud release train for that Boot version; Spring Modulith
  2.x; springdoc-openapi (Boot 4 line); Testcontainers; ArchUnit; Spotless Gradle plugin;
  palantir-java-format; the Gradle version Boot 4 supports. Write down each version with its source link.
- [ ] **1.2** Generate the Gradle wrapper at that Gradle version, using a local Gradle install or
  the official `gradle` Docker image. Keep `gradlew`, `gradlew.bat` and `gradle/wrapper/*`.
- [ ] **1.3** Create `gradle/libs.versions.toml` with sections `[versions]`, `[libraries]`,
  `[plugins]`. Put the source links from 1.1 in a header comment.
- [ ] **1.4** Create `settings.gradle.kts`: `rootProject.name = "smart-campus"`,
  `mavenCentral()` repositories, `include("backend:shared-kernel")`.
- [ ] **1.5** Create the root `build.gradle.kts` with plugin aliases declared `apply false` and no other logic.
- [ ] **1.6** Create `backend/shared-kernel/build.gradle.kts` (`java-library`) and a `package-info.java` in `edu.campus.kernel`.
- [ ] **1.7** Create `.gitignore` (build output, `.gradle/`, IDE files, `node_modules/`,
  `infra/.env`) and `.editorconfig` (UTF-8, LF, 4 spaces for Java/Kotlin, 2 spaces for TS/JSON/YAML).
- [ ] **1.8** Run `./gradlew build` (Git Bash) and `.\gradlew.bat build` (PowerShell) from `C:\Smart Campus`.

**Done when**
- [ ] `settings.gradle.kts` names the project `smart-campus` and includes `backend:shared-kernel`
- [ ] Every version lives in `libs.versions.toml`. A search for version numbers in `*.gradle.kts` finds nothing
- [ ] The Spring Cloud train and springdoc line are the ones officially listed for the pinned Boot version

**Verify**
- [ ] Both builds in 1.8 succeed, despite the space in the path
- [ ] Each catalog version matches its cited source link

**Files:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `.editorconfig` (+ generated wrapper)

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

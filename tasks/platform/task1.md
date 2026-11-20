# T1: Gradle root, wrapper, version catalog

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> ← (first task) · [T2 →](task2.md)

`Scope M · Depends on: — · Closes: —`

**Goal:** an empty Gradle multi-project that builds, with every Boot 4 dependency version pinned
in one place.

**Subtasks**
- [x] **1.1** Look up the current GA version and the official compatibility page for each of
  these: Spring Boot 4.x; the Spring Cloud release train for that Boot version; Spring Modulith
  2.x; springdoc-openapi (Boot 4 line); Testcontainers; ArchUnit; Spotless Gradle plugin;
  palantir-java-format; the Gradle version Boot 4 supports. Write down each version with its source link.
- [x] **1.2** Generate the Gradle wrapper at that Gradle version, using a local Gradle install or
  the official `gradle` Docker image. Keep `gradlew`, `gradlew.bat` and `gradle/wrapper/*`.
- [x] **1.3** Create `gradle/libs.versions.toml` with sections `[versions]`, `[libraries]`,
  `[plugins]`. Put the source links from 1.1 in a header comment.
- [x] **1.4** Create `settings.gradle.kts`: `rootProject.name = "smart-campus"`,
  `mavenCentral()` repositories, `include("backend:shared-kernel")`.
- [x] **1.5** Create the root `build.gradle.kts` with plugin aliases declared `apply false` and no other logic.
- [x] **1.6** Create `backend/shared-kernel/build.gradle.kts` (`java-library`) and a `package-info.java` in `edu.campus.kernel`.
- [x] **1.7** Create `.gitignore` (build output, `.gradle/`, IDE files, `node_modules/`,
  `infra/.env`) and `.editorconfig` (UTF-8, LF, 4 spaces for Java/Kotlin, 2 spaces for TS/JSON/YAML).
- [x] **1.8** Run `./gradlew build` (Git Bash) and `.\gradlew.bat build` (PowerShell) from `C:\Smart Campus`.

**Done when**
- [x] `settings.gradle.kts` names the project `smart-campus` and includes `backend:shared-kernel`
- [x] Every version lives in `libs.versions.toml`. A search for version numbers in `*.gradle.kts` finds nothing
- [x] The Spring Cloud train and springdoc line are the ones officially listed for the pinned Boot version

**Verify**
- [x] Both builds in 1.8 succeed, despite the space in the path
- [x] Each catalog version matches its cited source link

**Result (2026-11-20):** Boot 4.1.3 · Spring Cloud 2025.1.5 · Modulith 2.1.3 · springdoc 3.2.0 ·
Testcontainers 2.0.5 (managed by the Boot BOM) · ArchUnit 1.5.1 · Spotless 8.11.0 ·
palantir-java-format 2.103.0 · Gradle 9.9.2. Boot 4.2.0 is GA, but springdoc does not list it yet,
so the 4.1 line is pinned. Sources are in the header of `gradle/libs.versions.toml`. Added
`.gitattributes` (LF for `gradlew`, CRLF for `.bat`) so the wrapper works in Git Bash on Windows.

**Files:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `.editorconfig` (+ generated wrapper)

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

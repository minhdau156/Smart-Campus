# T1: Maven parent pom, wrapper, pinned versions

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> ← (first task) · [T2 →](task2.md)

`Scope M · Depends on: — · Closes: —`

**Goal:** an empty Maven multi-module project that builds, with every Boot 4 dependency version
pinned in one place (the root `pom.xml`).

> **Redo (2026-11-20):** T1 was first built with Gradle (commit `7b8c84e`). The build tool changed
> to Maven (spec Q2), so this task is done again. The version research from the Gradle run still
> holds: Boot 4.1.3, Spring Cloud 2025.1.5, Modulith 2.1.3, springdoc 3.2.0, ArchUnit 1.5.1,
> palantir-java-format 2.103.0 (Boot 4.2.0 skipped: springdoc does not list it yet).

**Subtasks**
- [x] **1.1** Re-check the versions above, and look up the Maven-only ones: the current Maven GA
  and the Maven versions Boot 4.1 supports; the Maven Wrapper plugin; `spotless-maven-plugin`.
  Write down each version with its source link.
- [x] **1.2** Remove the Gradle build: `settings.gradle.kts`, `build.gradle.kts`, `gradle/`,
  `gradlew`, `gradlew.bat`, `build-logic/`, `backend/shared-kernel/build.gradle.kts`. Keep the Java
  sources and tests.
- [x] **1.3** Generate the Maven Wrapper at that Maven version, using the official `maven` Docker
  image. Keep `mvnw`, `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties` (with the SHA-256 of
  the Maven download).
- [x] **1.4** Create the root `pom.xml`: parent `spring-boot-starter-parent`, `groupId`
  `edu.campus`, `artifactId` `smart-campus`, packaging `pom`, `<modules>` with
  `backend/shared-kernel`, all versions in `<properties>` (source links from 1.1 in a comment), and
  `<dependencyManagement>` importing the Spring Cloud and Modulith BOMs and pinning springdoc and ArchUnit.
- [x] **1.5** Create `backend/shared-kernel/pom.xml` (packaging `jar`, parent = root pom). Keep
  `package-info.java` in `edu.campus.kernel`.
- [x] **1.6** Update `.gitignore` (`target/` in place of the Gradle folders), `.editorconfig` (XML
  uses 4 spaces) and `.gitattributes` (`mvnw` LF, `mvnw.cmd` CRLF).
- [x] **1.7** Run `./mvnw verify` (Git Bash) and `.\mvnw.cmd verify` (PowerShell) from `C:\Smart Campus`.

**Done when**
- [x] The root `pom.xml` names the project `smart-campus` and lists `backend/shared-kernel` in `<modules>`
- [x] Every version lives in the root `pom.xml` `<properties>` (or comes from the Boot parent). Child
  poms have no `<version>` tags except the `<parent>` reference
- [x] No Gradle files remain
- [x] The Spring Cloud train and springdoc line are the ones officially listed for the pinned Boot version

**Verify**
- [x] Both builds in 1.7 succeed, despite the space in the path
- [x] Each pinned version matches its cited source link

**Result (2026-11-20, Maven):** Boot 4.1.3 (parent) · Spring Cloud 2025.1.5 · Modulith 2.1.3 ·
springdoc 3.2.0 · ArchUnit 1.5.1 · spotless-maven-plugin 3.11.0 · palantir-java-format 2.103.0 ·
jacoco-maven-plugin 0.8.16 · Maven 3.9.16 (Maven 4 is still RC) · Maven Wrapper 3.3.4
(`only-script`, no jar). Sources are in the comment in the root `pom.xml`.
- 1.3 changed: Docker Desktop was not running, so the wrapper scripts were taken straight from the
  official `maven-wrapper-distribution` 3.3.4 zip on Maven Central (SHA-1 checked). This is the same
  set of files the wrapper plugin writes. `distributionSha256Sum` was checked against the official
  SHA-512 of the Maven zip, and a wrong checksum was shown to stop `mvnw`.
- JUnit, AssertJ and the JUnit Platform launcher (test scope, for every module) were added here and
  not in T2.2, because the kept `KernelPlaceholderTest` needs them to compile.

**Files:** `pom.xml`, `backend/shared-kernel/pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitignore`, `.editorconfig`, `.gitattributes` (− all Gradle files)

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

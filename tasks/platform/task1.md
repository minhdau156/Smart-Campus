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
- [ ] **1.1** Re-check the versions above, and look up the Maven-only ones: the current Maven GA
  and the Maven versions Boot 4.1 supports; the Maven Wrapper plugin; `spotless-maven-plugin`.
  Write down each version with its source link.
- [ ] **1.2** Remove the Gradle build: `settings.gradle.kts`, `build.gradle.kts`, `gradle/`,
  `gradlew`, `gradlew.bat`, `build-logic/`, `backend/shared-kernel/build.gradle.kts`. Keep the Java
  sources and tests.
- [ ] **1.3** Generate the Maven Wrapper at that Maven version, using the official `maven` Docker
  image. Keep `mvnw`, `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties` (with the SHA-256 of
  the Maven download).
- [ ] **1.4** Create the root `pom.xml`: parent `spring-boot-starter-parent`, `groupId`
  `edu.campus`, `artifactId` `smart-campus`, packaging `pom`, `<modules>` with
  `backend/shared-kernel`, all versions in `<properties>` (source links from 1.1 in a comment), and
  `<dependencyManagement>` importing the Spring Cloud and Modulith BOMs and pinning springdoc and ArchUnit.
- [ ] **1.5** Create `backend/shared-kernel/pom.xml` (packaging `jar`, parent = root pom). Keep
  `package-info.java` in `edu.campus.kernel`.
- [ ] **1.6** Update `.gitignore` (`target/` in place of the Gradle folders), `.editorconfig` (XML
  uses 4 spaces) and `.gitattributes` (`mvnw` LF, `mvnw.cmd` CRLF).
- [ ] **1.7** Run `./mvnw verify` (Git Bash) and `.\mvnw.cmd verify` (PowerShell) from `C:\Smart Campus`.

**Done when**
- [ ] The root `pom.xml` names the project `smart-campus` and lists `backend/shared-kernel` in `<modules>`
- [ ] Every version lives in the root `pom.xml` `<properties>` (or comes from the Boot parent). Child
  poms have no `<version>` tags except the `<parent>` reference
- [ ] No Gradle files remain
- [ ] The Spring Cloud train and springdoc line are the ones officially listed for the pinned Boot version

**Verify**
- [ ] Both builds in 1.7 succeed, despite the space in the path
- [ ] Each pinned version matches its cited source link

**Files:** `pom.xml`, `backend/shared-kernel/pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitignore`, `.editorconfig`, `.gitattributes` (− all Gradle files)

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

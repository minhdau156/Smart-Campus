# Task 1: Gradle root, wrapper, version catalog - learning note

> Task: [task1.md](../../../tasks/platform/task1.md) · Commit: `7b8c84e` · Date: 2026-11-20

> **Outdated (2026-11-20):** this note explains the first, Gradle version of this task. The project
> switched to Maven (spec Q2), and this note will be rewritten when the task is redone.

## In one minute

Task 1 turned an empty folder into a **Gradle multi-project build**. A multi-project build is one
big project that holds several smaller projects (Maven calls them "modules"). It now has one small
project, `backend/shared-kernel`, and it builds with one command: `./gradlew build`. We also wrote
down, **in one file only**, the exact version of every main library (Spring Boot, Spring Cloud,
and others). Every later task adds code on top of this base, so it has to come first.

## The files

### `settings.gradle.kts`
- **What it does:** tells Gradle the project name, which sub-projects exist, and where to download
  libraries from.
- **Why it exists:** Gradle reads this file **first**. Without it, Gradle does not see the folder as
  a project. We saw this: making the wrapper failed with "Directory does not contain a Gradle
  build" until this file existed.
- **Key lines:**
  ```kotlin
  rootProject.name = "smart-campus"
  dependencyResolutionManagement {
      repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
      repositories { mavenCentral() }
  }
  include("backend:shared-kernel")
  ```
  - `include("backend:shared-kernel")` adds the folder `backend/shared-kernel` as a sub-project.
    The `:` stands for a folder level.
  - `mavenCentral()` means "download libraries from Maven Central". It is set here once, for all
    sub-projects.
  - `FAIL_ON_PROJECT_REPOS` makes the build fail if a sub-project adds its own download place. This
    keeps all downloads in one known, trusted place.
- **Like in Maven:** the `<artifactId>`, `<modules>` and `<repositories>` parts of a parent `pom.xml`.

### `build.gradle.kts` (root)
- **What it does:** says which plugins the project uses and their versions, but does not turn them on.
- **Why it exists:** a plugin should be loaded once, at the top, and then shared by all
  sub-projects. Each sub-project turns on only the plugins it needs.
- **Key lines:**
  ```kotlin
  plugins {
      alias(libs.plugins.spring.boot) apply false
      alias(libs.plugins.spring.dependency.management) apply false
      alias(libs.plugins.spotless) apply false
  }
  ```
  - `alias(libs.plugins.spring.boot)` takes the plugin and its version from the version catalog
    (below). No version number is written here.
  - `apply false` means: download the plugin, but do not use it in the root. The root has no Java
    code. `shared-kernel` is a library, not an app, so it will never use the Spring Boot plugin.
    `core-app` (Task 6) will.
- **Like in Maven:** `<pluginManagement>` in a parent pom. It sets the version but does not run
  the plugin.

### `gradle/libs.versions.toml` (the version catalog)
- **What it does:** lists every library and plugin version in one place.
- **Why it exists:** if versions were written in many build files, they would drift apart, and
  upgrading would mean editing many files. Here, upgrading Spring Boot is a **one-line change**.
  The spec says versions live here and nowhere else.
- **Key lines:**
  ```toml
  [versions]
  spring-boot = "4.1.3"

  [libraries]
  spring-boot-dependencies = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }

  [plugins]
  spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
  ```
  - `[versions]` holds the numbers. `[libraries]` and `[plugins]` point at those numbers with
    `version.ref`.
  - In build files, Gradle turns the names into code: `spring-boot` becomes `libs.plugins.spring.boot`.
  - The comment at the top of the file holds the **source link for every version**, so anyone can
    check where each number came from.
- **Like in Maven:** `<properties>` with version numbers, plus `<dependencyManagement>` in a parent pom.

### `backend/shared-kernel/build.gradle.kts`
- **What it does:** says that `shared-kernel` is a Java library built with Java 21.
- **Why it exists:** every sub-project needs its own build file. `shared-kernel` will hold the types
  that all 4 apps share: ids, `Money`, time, the Kafka event envelope (Tasks 4 and 5).
- **Key lines:**
  ```kotlin
  plugins { `java-library` }
  java { toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get()) } }
  ```
  - `java-library` is Gradle's built-in plugin for a Java library: a jar that other projects use,
    not an app you run.
  - `toolchain` pins the Java version. The `21` comes from the catalog, so there is no number here.
- **Like in Maven:** a module `pom.xml` with `<packaging>jar</packaging>` and `maven.compiler.release`.

### `backend/shared-kernel/src/main/java/edu/campus/kernel/package-info.java`
- **What it does:** creates the Java package `edu.campus.kernel` and describes it.
- **Why it exists:** the project needs at least one Java file, so the build has something to
  compile. This proves Java compiling works before we write real code. A `package-info.java` is
  where Java keeps the comment (Javadoc) for a whole package.

### `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (the Gradle wrapper)
- **What it does:** a small starter program. When you run `./gradlew build`, it downloads the
  exact Gradle version the project needs (once), then runs it.
- **Why it exists:** nobody has to install Gradle by hand, and everyone, including CI, uses the
  **same** Gradle version. This computer has no Gradle installed, and the build still works.
- **Key lines** (`gradle-wrapper.properties`):
  ```properties
  distributionUrl=https\://services.gradle.org/distributions/gradle-9.9.2-bin.zip
  distributionSha256Sum=971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b
  ```
  - `distributionUrl` is the exact Gradle version (9.9.2).
  - `distributionSha256Sum` is a fingerprint of the download. If the file was changed or damaged,
    the fingerprint does not match, and the wrapper refuses to run it. This is a security check.
- `gradlew` is for Git Bash, Linux and macOS. `gradlew.bat` is for Windows PowerShell or cmd.
- **Like in Maven:** `mvnw`, `mvnw.cmd` and `.mvn/wrapper/`.

### `.gitignore`
- **What it does:** lists files that git must not save.
- **Why it exists:** build output (`build/`, `.gradle/`), IDE settings, `node_modules/` and secrets
  (`infra/.env`) do not belong in the repo. Build output is created again by every build, and
  secrets must never be pushed to GitHub.
- **Key line:** `!gradle/wrapper/gradle-wrapper.jar`. The `!` means "except this one". Jar files
  are usually build output, but the wrapper jar **must** be saved.

### `.editorconfig`
- **What it does:** tells every editor (VS Code, IntelliJ) how to format text: UTF-8, LF line
  endings, 4 spaces for Java/Kotlin, 2 spaces for TS/JSON/YAML.
- **Why it exists:** without it, each person's editor uses its own settings, and every commit fills
  up with spacing-only changes.

### `.gitattributes`
- **What it does:** tells git which line endings to use in each kind of file.
- **Why it exists:** on this Windows computer, git is set to switch files to Windows line endings
  (CRLF). A shell script with CRLF fails in Git Bash and Linux. So `gradlew` must stay LF, and
  `gradlew.bat` must stay CRLF.
- **Key lines:**
  ```
  * text=auto eol=lf
  *.bat text eol=crlf
  ```
- This file was not in the task list. It was added because the wrapper needs it.

### `tasks/plan.md`, `tasks/todo.md`, `tasks/platform/task1.md`
- **What changed:** T1 is ticked as done, and `task1.md` got a "Result" paragraph with the chosen
  versions.
- **Why:** these files track progress, so anyone can see what is done and what comes next.

## Decisions and why

| Decision | Options | Why we chose it |
|---|---|---|
| **Spring Boot 4.1.3** | 4.2.0 (newest, released 2026-11-19) or 4.1.3 | springdoc (makes the API docs) officially supports Boot only up to 4.1.x. The task says springdoc must be listed for our Boot version. We move to 4.2 when springdoc supports it. |
| Spring Cloud **2025.1.5** | 2026.0.0 (made for Boot 4.2) or 2025.1.x | spring.io lists 2025.1.x for Boot 4.0.x and 4.1.x (from 2025.1.2 on). |
| Spring Modulith **2.1.3** | 2.2.0 (built on Boot 4.2) or 2.1.3 | 2.1.3 is built on Boot 4.1.3, which matches exactly. |
| springdoc **3.2.0** | 3.1.x or 3.2.x | Both are listed for Boot 4.1. 3.2.0 is newer. |
| Testcontainers: **no entry** | pin it ourselves, or let Boot do it | The Boot 4.1.3 BOM already sets it (2.0.5). Two places with the same version could disagree. |
| Gradle **9.9.2** | any 8.14+ or 9.x | Newest Gradle, and Boot 4.1 supports "Gradle 8.14+ and 9.x". |
| Versions read from **Maven Central** | memory, blog posts | Maven Central is where the libraries are really published, so the data is exact. |

(A **BOM**, "bill of materials", is a list of library versions that are tested together. Importing
Spring Boot's BOM gives you hundreds of matching versions at once.)

## Problems we hit

1. **No Docker image for Gradle 9.9.2 yet.** Gradle 9.9.2 came out one day earlier. Fix: use the
   `gradle:9.9.1-jdk21` image to *create* a wrapper for 9.9.2. Any Gradle can create a wrapper for
   another version.
2. **"Directory does not contain a Gradle build".** Gradle refused to make a wrapper in a folder
   with no settings file. Fix: write `settings.gradle.kts` first, then create the wrapper.
3. **Line endings on Windows.** git warned that LF would become CRLF, which would break `gradlew`.
   Fix: add `.gitattributes`.
4. **`gradlew` not executable on Linux.** Windows does not save the "can run" flag. Fix:
   `git update-index --chmod=+x gradlew`. Now git saves it as mode `100755`.

## How we checked it works

| Check | Command | Result |
|---|---|---|
| Build in Git Bash | `./gradlew build` | `BUILD SUCCESSFUL` |
| Build in PowerShell | `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` |
| Space in the path is OK | both builds ran in `C:\Smart Campus` | passed |
| No versions in build files | `grep -rE '[0-9]+\.[0-9]+' --include='*.gradle.kts' .` | nothing found |
| Line endings | `git ls-files --eol gradlew gradlew.bat` | `gradlew` LF, `gradlew.bat` CRLF |
| Every library version exists | HTTP check on each Maven Central folder | all `200` (found) |

## Words to know

| Word | Meaning in simple words |
|---|---|
| Gradle | A tool that compiles, tests and packages code. It does the same job as Maven. |
| Multi-project build | One big Gradle project that holds several small ones (sub-projects). |
| Sub-project | One part of the big project, with its own build file. Maven calls it a module. |
| Kotlin DSL (`.kts`) | Gradle build files written in Kotlin code. It is only for building, not app code. |
| Plugin | An add-on that gives Gradle new tasks, for example `bootRun` from Spring Boot. |
| `apply false` | Download a plugin but do not turn it on in this project. |
| Version catalog | `libs.versions.toml`: the one file that holds all versions. |
| BOM | A tested list of matching library versions you can import at once. |
| Wrapper | `gradlew`: a script that downloads and runs the right Gradle version for you. |
| Toolchain | Gradle's setting for which Java version compiles the code. |
| LF / CRLF | Two ways to end a line of text: Unix/Linux use LF, Windows uses CRLF. |
| GA | "General Availability": the final, stable release (not a test version like M1 or RC1). |

## Test yourself

1. Why does the root `build.gradle.kts` use `apply false` for the Spring Boot plugin?
2. You want to upgrade Spring Boot. How many files do you change, and which ones?
3. Why did we pick Boot 4.1.3 when 4.2.0 is newer?
4. What happens if someone changes the Gradle zip on the download server?
5. Why must `gradle-wrapper.jar` be saved in git, when `.gitignore` usually skips jar files?

<details>
<summary>Answers</summary>

1. The root has no Java code and is not an app. Each sub-project turns the plugin on only if it is
   an app (like `core-app`). `shared-kernel` is a library, so it never needs it.
2. One file: `gradle/libs.versions.toml`, the `spring-boot = "..."` line. You also check that
   Spring Cloud, Modulith and springdoc still match the new version.
3. springdoc does not support Boot 4.2 yet. The task asks that every library officially supports
   the chosen Boot version.
4. Its SHA-256 fingerprint no longer matches `distributionSha256Sum`, so the wrapper stops and does
   not run it.
5. `gradlew` needs this jar to start. Without it, nobody can build the project without installing
   Gradle. That is why `.gitignore` has `!gradle/wrapper/gradle-wrapper.jar`.

</details>

## Learn more

- [Gradle: Structuring multi-project builds](https://docs.gradle.org/9.9.2/userguide/multi_project_builds.html):
  how `settings.gradle.kts` and `include` work.
- [Gradle: Version catalogs](https://docs.gradle.org/9.9.2/userguide/version_catalogs.html):
  the `libs.versions.toml` format.
- [Gradle: The Gradle Wrapper](https://docs.gradle.org/9.9.2/userguide/gradle_wrapper.html):
  how `gradlew` works, including the checksum.
- [Gradle: Toolchains for JVM projects](https://docs.gradle.org/9.9.2/userguide/toolchains.html):
  how the Java version is chosen.
- [Spring Boot 4.1: System requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html):
  which Java and Gradle versions Boot supports.
- [Spring Boot Gradle plugin](https://docs.spring.io/spring-boot/gradle-plugin/index.html):
  what the plugin adds (`bootRun`, `bootJar`).
- [EditorConfig](https://editorconfig.org/): the `.editorconfig` format.
- [git: gitattributes](https://git-scm.com/docs/gitattributes): line-ending settings.

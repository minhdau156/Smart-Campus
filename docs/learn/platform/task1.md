# Task 1: Maven parent pom, wrapper, pinned versions - learning note

> Task: [task1.md](../../../tasks/platform/task1.md) · Commit: `cf968b5` · Date: 2026-11-20
>
> This task was first done with Gradle (commit `7b8c84e`). The project then switched to Maven
> because Maven's config is easier to read (spec decision Q2). This note explains the Maven version.

## In one minute

Task 1 turned the folder into a **Maven multi-module project**: one parent project that holds
smaller projects called **modules**. Right now there is one module, `backend/shared-kernel`, and the
whole project builds with one command: `./mvnw verify`. We wrote down, **in one file** (the root
`pom.xml`), the exact version of every main library: Spring Boot, Spring Cloud, Modulith, and
others. Every later task adds code on top of this base, so it has to come first.

## The files

### `pom.xml` (root, the "parent pom")
- **What it does:** describes the whole project. It is the parent of every module.
- **Why it exists:** without it, each module would need its own copy of every version and rule.
  The copies would drift apart, and upgrading would mean editing many files.
- **Key parts:**

  1. **Spring Boot as the parent's parent**
     ```xml
     <parent>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-parent</artifactId>
         <version>4.1.3</version>
         <relativePath/>
     </parent>
     ```
     So the chain is: `spring-boot-starter-parent` → our root `pom.xml` → `shared-kernel/pom.xml`.
     From Spring Boot's parent we get, for free:
     - the versions of hundreds of libraries (JUnit, AssertJ, Testcontainers, Jackson…);
     - the versions of the Maven plugins (compiler, surefire, failsafe, jar…);
     - good default settings: UTF-8, Java version from `java.version`, and `-parameters`.

     `<relativePath/>` (empty) means: "don't look for this parent in a folder; download it".

  2. **The module list**
     ```xml
     <packaging>pom</packaging>
     <modules>
         <module>backend/shared-kernel</module>
     </modules>
     ```
     `packaging pom` means this project makes no jar itself; it only groups modules. Maven builds
     the modules in the right order. Later tasks add `core-app`, `gateway` and the others here.

  3. **Versions in `<properties>`**
     ```xml
     <properties>
         <java.version>21</java.version>
         <spring-cloud.version>2025.1.5</spring-cloud.version>
         <springdoc.version>3.2.0</springdoc.version>
         ...
     </properties>
     ```
     A property is a named value, used elsewhere as `${spring-cloud.version}`. `java.version` is a
     special name: Spring Boot's parent reads it to set the Java version (21). The big comment above
     it holds the **source link for every version**, so anyone can check where each number came from.

  4. **`<dependencyManagement>`: a version list, not a download list**
     ```xml
     <dependency>
         <groupId>org.springframework.cloud</groupId>
         <artifactId>spring-cloud-dependencies</artifactId>
         <version>${spring-cloud.version}</version>
         <type>pom</type>
         <scope>import</scope>
     </dependency>
     ```
     Things listed here are **not** added to any module. They only say "if a module uses this
     library, use this version". `<scope>import</scope>` with `<type>pom</type>` imports a whole
     **BOM** (a list of versions tested together). Spring Cloud's BOM sets the versions of the
     gateway and the other Cloud libraries. Modulith's BOM does the same for Modulith.

  5. **`<dependencies>`: test libraries for every module**
     ```xml
     <dependency>
         <groupId>org.junit.jupiter</groupId>
         <artifactId>junit-jupiter</artifactId>
         <scope>test</scope>
     </dependency>
     ```
     Unlike `<dependencyManagement>`, things in `<dependencies>` in a parent **are** added to every
     module. `scope test` means "only for tests"; it never goes into the real jar. There is no
     `<version>`: it comes from the Spring Boot parent.

### `backend/shared-kernel/pom.xml`
- **What it does:** describes the `shared-kernel` module: a plain Java library (`jar`).
- **Why it exists:** every module needs its own pom. `shared-kernel` will hold the types that all 4
  apps share: ids, `Money`, time, the Kafka event envelope (Tasks 4 and 5).
- **Key lines:**
  ```xml
  <parent>
      <groupId>edu.campus</groupId>
      <artifactId>smart-campus</artifactId>
      <version>0.1.0-SNAPSHOT</version>
      <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>shared-kernel</artifactId>
  <packaging>jar</packaging>
  ```
  - The `<parent>` block points to our root pom (two folders up). From it, the module gets Java 21,
    the test libraries, and all versions.
  - This is the **only** place a version is written in a child pom. Maven needs it to find the parent.
  - There is no Spring dependency: `shared-kernel` must stay framework-free.
- **`0.1.0-SNAPSHOT`:** the version of **our** project. `SNAPSHOT` means "work in progress, not a release".

### `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` (the Maven Wrapper)
- **What it does:** a small script. When you run `./mvnw verify`, it downloads the exact Maven
  version the project needs (once, into `~/.m2/wrapper/`), then runs it.
- **Why it exists:** nobody has to install Maven by hand, and everyone, including CI, uses the
  **same** Maven version. This computer has no Maven installed, and the build still works.
- **Key lines** (`maven-wrapper.properties`):
  ```properties
  wrapperVersion=3.3.4
  distributionType=only-script
  distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
  distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
  ```
  - `distributionUrl` is the exact Maven version (3.9.16).
  - `distributionSha256Sum` is a fingerprint of the download. If the file was changed or damaged,
    the fingerprint does not match, and `mvnw` refuses to run it. We tested this (see below).
  - `only-script` means the wrapper is just the two scripts: no `.jar` file in the repo.
- `mvnw` is for Git Bash, Linux and macOS. `mvnw.cmd` is for Windows PowerShell or cmd.

### `.gitignore` (changed)
- **What changed:** the Gradle folders are gone. Now it ignores `target/` (Maven's build output folder).
- **Why:** build output is made again by every build, so it does not belong in git.

### `.editorconfig` (changed)
- **What changed:** `[*.{java,kt,kts}]` became `[*.{java,xml}]`: Java and XML (the pom files) use 4 spaces.

### `.gitattributes` (changed)
- **What changed:** only the comment (it now says `mvnw`).
- **Why it matters:** on this Windows computer, git switches files to Windows line endings (CRLF).
  A shell script with CRLF fails in Git Bash and Linux. This file keeps `mvnw` in LF and `mvnw.cmd`
  in CRLF.

### Removed: all Gradle files
`settings.gradle.kts`, `build.gradle.kts`, `gradle/` (version catalog and wrapper), `gradlew`,
`gradlew.bat`, `build-logic/`, and `backend/shared-kernel/build.gradle.kts`. The Maven files above
do the same jobs. The Java files were kept.

| Gradle file (old) | Maven file (new) |
|---|---|
| `settings.gradle.kts` (project name, module list) | root `pom.xml` (`<artifactId>`, `<modules>`) |
| `gradle/libs.versions.toml` (versions) | root `pom.xml` (`<properties>`, `<dependencyManagement>`, Boot parent) |
| `backend/shared-kernel/build.gradle.kts` | `backend/shared-kernel/pom.xml` |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*` |
| `build/` | `target/` |

### `tasks/platform/task1.md`, `task2.md`, `tasks/todo.md`, `tasks/plan.md`
- T1 is ticked as done, with a "Result" paragraph.
- In `task2.md`, step 2.2 is ticked, because it was done early (see "Decisions").

## Decisions and why

| Decision | Options | Why we chose it |
|---|---|---|
| **Spring Boot 4.1.3** as the parent | 4.2.0 (newest) or 4.1.3 | springdoc (makes the API docs) supports Boot only up to 4.1.x. |
| Spring Cloud **2025.1.5**, Modulith **2.1.3**, springdoc **3.2.0** | newer lines made for Boot 4.2 | These are the versions officially listed for Boot 4.1. |
| **Maven 3.9.16** | Maven 4.0.0-rc-7 | Maven 4 is still a release candidate (RC), not final. Boot 4.1 needs "Maven 3.6.3 or later". |
| Inherit **`spring-boot-starter-parent`** | import Boot's BOM only | The parent gives versions **and** plugin settings (UTF-8, Java version, `-parameters`) with no extra config. It keeps our pom short. |
| **Wrapper scripts from the official zip** | the wrapper plugin in Docker | Docker Desktop was not running. The zip on Maven Central has the very same scripts the plugin writes, and we checked its SHA-1. |
| **`only-script` wrapper** | the jar type | No binary `.jar` file in git. The script downloads Maven itself. |
| **Test libraries added in T1**, not T2 | wait until T2.2 | The kept `KernelPlaceholderTest` would not compile without JUnit and AssertJ, and Task 1 must end with a green build. |

## Problems we hit

1. **Docker Desktop was not running**, so the `maven` Docker image could not start. Fix: download
   `maven-wrapper-distribution-3.3.4-only-script.zip` from Maven Central, check its SHA-1, and copy
   `mvnw` and `mvnw.cmd` from it.
2. **"MISMATCH" when comparing checksums.** This was a mistake in my script, not a bad file. The
   Windows temp path contains `\`, so `sha512sum` printed a `\` in front of the hash. Fix: read the
   file through `<` so no path is printed. Then the SHA-512 matched the official one.
3. **Many "cached from a remote repository ID" lines** in the Maven log. They are only info
   messages: this computer's Maven cache (`~/.m2`) already had some files from another project, so
   Maven checks them against Maven Central once. Not an error.

## How we checked it works

| Check | Command | Result |
|---|---|---|
| RED first | `./mvnw verify` (before the Maven files) | Failed: `./mvnw: No such file or directory` |
| Build in Git Bash | `./mvnw -B verify` | `BUILD SUCCESS` |
| Build in PowerShell | `.\mvnw.cmd -B clean verify` | `BUILD SUCCESS`, `KernelPlaceholderTest`: 1 test, 0 failures |
| Space in the path is OK | both builds ran in `C:\Smart Campus` | passed |
| Maven download is genuine | compare SHA-512 with the official `.sha512` file | match |
| Checksum is enforced | set a wrong `distributionSha256Sum`, then `./mvnw -v` | stopped: "your Maven distribution might be compromised". Restored → works |
| No Gradle left | `git ls-files` and `find` for gradle files | 0 and 0 |
| Child pom has no versions | `grep '<version>' backend/shared-kernel/pom.xml` | only the `<parent>` reference |
| Line endings, executable | `git ls-files --eol`, `git ls-files -s mvnw` | `mvnw` LF + mode `100755`, `mvnw.cmd` CRLF |

## Words to know

| Word | Meaning in simple words |
|---|---|
| Maven | A tool that compiles, tests and packages Java code, configured with `pom.xml` files |
| POM (`pom.xml`) | "Project Object Model": the XML file that describes a Maven project |
| Parent pom | A pom that other poms inherit settings and versions from |
| Module | One part of a multi-module project, with its own `pom.xml` |
| `<properties>` | Named values (like variables) used as `${name}` in the pom |
| `<dependencyManagement>` | A list of versions. It sets versions, but adds nothing by itself |
| `<dependencies>` | The libraries a project really uses |
| BOM | "Bill of materials": a list of library versions tested together |
| Scope `test` | The library is only used to compile and run tests |
| SNAPSHOT | A version that is still in progress, not a final release |
| Maven Wrapper (`mvnw`) | A script that downloads and runs the right Maven version for you |
| SHA-256 / SHA-512 | A "fingerprint" of a file, used to check the file was not changed |
| GA / RC | GA = final, stable release. RC = "release candidate", almost final, still being tested |
| `verify` | A Maven phase that compiles, runs the tests, packages the jar, and runs the checks |

## Test yourself

1. What is the difference between `<dependencyManagement>` and `<dependencies>` in the parent pom?
2. Why does `shared-kernel/pom.xml` have no `<version>` for JUnit?
3. Why did we pick Maven 3.9.16 and not Maven 4?
4. What happens if someone changes the Maven zip on the download server?
5. Why were the test libraries added in Task 1, when the plan had them in Task 2?

<details>
<summary>Answers</summary>

1. `<dependencyManagement>` only sets **versions** ("if you use X, use version Y"). It adds nothing.
   `<dependencies>` really **adds** the libraries, and in a parent pom it adds them to every module.
2. Its parent chain sets it: our root pom adds JUnit with no version, and the version comes from
   `spring-boot-starter-parent`, which manages JUnit's version.
3. Maven 4 is still a release candidate (`4.0.0-rc-7`), not a final release. Maven 3.9.16 is the
   newest final version, and Boot 4.1 supports Maven 3.6.3 or later.
4. Its SHA-256 no longer matches `distributionSha256Sum`, so `mvnw` stops with "your Maven
   distribution might be compromised" and does not run it.
5. `KernelPlaceholderTest` was kept from the Gradle version. It needs JUnit and AssertJ to compile,
   and Task 1 must end with a green `./mvnw verify`.

</details>

## Learn more

- [Maven: Introduction to the POM](https://maven.apache.org/guides/introduction/introduction-to-the-pom.html):
  parent poms, inheritance, and multi-module projects.
- [Maven: Introduction to the dependency mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html):
  `<dependencyManagement>`, scopes, and importing a BOM.
- [Maven: Introduction to the build lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html):
  phases like `test`, `package` and `verify`.
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/): how `mvnw` works, the `only-script`
  type, and `distributionSha256Sum`.
- [Spring Boot Maven plugin: Getting started](https://docs.spring.io/spring-boot/maven-plugin/getting-started.html):
  what `spring-boot-starter-parent` gives you.
- [Spring Boot 4.1: System requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html):
  which Java and Maven versions Boot supports.
- [git: gitattributes](https://git-scm.com/docs/gitattributes): line-ending settings.
- [EditorConfig](https://editorconfig.org/): the `.editorconfig` format.

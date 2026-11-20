# Task 2: Build conventions - learning note

> Task: [task2.md](../../../tasks/platform/task2.md) · Commit: `d615a1c` · Date: 2026-11-20

> **Outdated (2026-11-20):** this note explains the first, Gradle version of this task. The project
> switched to Maven (spec Q2), and this note will be rewritten when the task is redone.

## In one minute

The project will have many Java projects: `shared-kernel` now, and later 4 apps. They all need the
same rules: Java 21, the same code format, JUnit tests, a coverage report. Task 2 writes those
rules **once**, as three of our own Gradle plugins in a folder called `build-logic`. A project then
gets all the rules by adding one line, like `id("campus.java-conventions")`. We also split the
tests into two groups: fast **unit tests** and slower **integration tests**. Each group has its
own command. From now on, badly formatted code makes the build fail.

## The files

### `build-logic/settings.gradle.kts` and `build-logic/build.gradle.kts`
- **What they do:** make `build-logic` a small, separate Gradle build whose only job is to hold our
  plugins.
- **Why they exist:** without them, every project would copy the same 40 lines of build rules. With
  a copy in each project, the copies slowly become different. With one place, a change reaches
  every project at once.
- **Key lines** (`settings.gradle.kts`):
  ```kotlin
  versionCatalogs {
      create("libs") { from(files("../gradle/libs.versions.toml")) }
  }
  ```
  `build-logic` is a separate build, so it cannot see the root's version list by itself. This line
  points it at the **same** `libs.versions.toml`, so versions still live in one file.
- **Key lines** (`build.gradle.kts`):
  ```kotlin
  plugins { `kotlin-dsl` }
  fun pluginJar(plugin: Provider<PluginDependency>) =
      plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }
  dependencies {
      implementation(pluginJar(libs.plugins.spotless))
      implementation(pluginJar(libs.plugins.spring.boot))
  }
  ```
  - `kotlin-dsl` lets us write plugins as `.gradle.kts` files. These are called **precompiled
    script plugins**: Gradle compiles each file into a plugin, and the file name becomes the plugin
    id (`campus.java-conventions.gradle.kts` → `campus.java-conventions`).
  - Our plugins use the Spotless and Spring Boot plugins, so `build-logic` must download them.
    `pluginJar` turns a catalog entry into the download address of the plugin
    (`com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:8.11.0`). This is how we avoid
    writing a version number here.
- **Like in Maven:** a parent pom with `<build><pluginManagement>` and shared `<plugins>`, which
  every module inherits.

### `settings.gradle.kts` (root, changed)
- **What changed:** a new `pluginManagement { includeBuild("build-logic") }` block.
- **Why:** this tells the main build where to look when a project asks for `campus.*` plugins.
  Without it, you get exactly the error we saw in the RED step: "Plugin
  `campus.java-conventions` was not found".
- **Key idea:** `includeBuild` makes a **composite build**: two builds that work as one. Gradle
  builds `build-logic` first, then uses its plugins in the main build.

### `build-logic/src/main/kotlin/campus.java-conventions.gradle.kts`
- **What it does:** the shared rules for every Java project.
- **Why it exists:** one line in a project gives it all of these:

  | Rule | Code | Why |
  |---|---|---|
  | Java 21 | `toolchain { languageVersion = ... }` | Everyone compiles with the same Java |
  | UTF-8 | `options.encoding = "UTF-8"` | Vietnamese text (ố, ư, đ) is read correctly on every computer |
  | `-parameters` | `options.compilerArgs.add("-parameters")` | Keeps method parameter names in the compiled code. Spring needs them, for example to match `@PathVariable id` |
  | JUnit | `useJUnitPlatform()` | Gradle runs JUnit 5/6 tests |
  | Spotless | `palantirJavaFormat(...)` | One fixed code format. `spotlessCheck` fails the build on bad format |
  | JaCoCo | `tasks.test { finalizedBy(tasks.jacocoTestReport) }` | After the tests, write a coverage report (which lines were tested) |

- **Key lines:**
  ```kotlin
  val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
  testImplementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
  testImplementation("org.junit.jupiter:junit-jupiter")   // no version!
  ```
  - Inside a precompiled plugin, the short `libs.xxx` style does not work. So we get the catalog
    with `extensions.getByType<VersionCatalogsExtension>()` and read values by name.
  - `platform(...)` imports the Spring Boot **BOM** (a list of versions that are tested together).
    So `junit-jupiter` gets its version (6.0.3) from the BOM, and we write no number.
  - It is only on `testImplementation`, so the real `shared-kernel` code still has **no Spring**
    in it. A BOM only sets versions; it adds no code.
- **Like in Maven:** `maven-compiler-plugin` + `maven-surefire-plugin` + `jacoco-maven-plugin` +
  `spotless-maven-plugin` in a parent pom.

### `build-logic/src/main/kotlin/campus.integration-test.gradle.kts`
- **What it does:** adds a second test folder, `src/integrationTest/java`, and a task called
  `integrationTest` that runs it.
- **Why it exists:** later, integration tests (ITs) will start real Postgres and Kafka in Docker
  (Testcontainers). That takes many seconds. Unit tests take milliseconds. Keeping them apart means
  `./gradlew test` stays fast while you code, and `./gradlew build` (and CI) still runs everything.
- **Key lines:**
  ```kotlin
  val integrationTest = sourceSets.create("integrationTest") {
      compileClasspath += sourceSets.main.get().output   // ITs can use the main code
      runtimeClasspath += sourceSets.main.get().output
  }
  configurations[integrationTest.implementationConfigurationName]
      .extendsFrom(configurations.testImplementation.get())   // ...and the test libraries
  val integrationTestTask = tasks.register<Test>("integrationTest") {
      testClassesDirs = integrationTest.output.classesDirs
      classpath = integrationTest.runtimeClasspath
      shouldRunAfter(tasks.test)
  }
  tasks.check { dependsOn(integrationTestTask) }
  ```
  - A **source set** is a group of source files that are compiled together (`main`, `test`, and
    now `integrationTest`).
  - `extendsFrom` = "ITs get every library the unit tests get" (JUnit, AssertJ).
  - `shouldRunAfter(tasks.test)` = if both run, run the fast unit tests first, so you see their
    failures early.
  - `check` depends on it, and `build` runs `check`, so `build` runs both kinds of test.
- **Like in Maven:** the `maven-failsafe-plugin` (ITs) next to `maven-surefire-plugin` (unit tests).

### `build-logic/src/main/kotlin/campus.spring-app.gradle.kts`
- **What it does:** the rules for a runnable Spring Boot app. It turns on the Java conventions and
  the Spring Boot plugin, imports the Boot BOM, and makes a **layered** jar.
- **Why it exists:** the 4 apps (Tasks 6, 10, 11, 13) will each need exactly this. It is written
  now so all four start the same way. No project uses it yet; Task 6 is the first.
- **Key lines:**
  ```kotlin
  val bootBom = platform(libs.findLibrary("spring-boot-dependencies").get())
  implementation(bootBom)
  annotationProcessor(bootBom)
  tasks.named<BootJar>("bootJar") { layered { enabled = true } }
  ```
  - The BOM goes on `annotationProcessor` too, because tools like the configuration processor run
    while compiling and also need versions.
  - **Layered jar:** the jar is split into layers (libraries, Spring Boot loader, our code). In a
    Docker image, each layer is cached on its own. When only our code changes, Docker rebuilds only
    the small top layer, so image builds (Task 15) are fast.

### `backend/shared-kernel/build.gradle.kts` (changed)
- **What changed:** the old Java 21 block is gone. It now just applies our plugins:
  ```kotlin
  plugins {
      `java-library`
      id("campus.java-conventions")
      id("campus.integration-test")
  }
  ```
- **Why:** the Java version now comes from `campus.java-conventions`, so the rule lives in one
  place. Note that it does **not** use `campus.spring-app`: `shared-kernel` is a library, not an app.

### `backend/shared-kernel/src/test/.../KernelPlaceholderTest.java` and `src/integrationTest/.../KernelPlaceholderIT.java`
- **What they do:** one tiny test each. They only check the package name.
- **Why they exist:** to **prove** that each task runs the right tests. With no tests, we could not
  see that `test` runs only the unit test and `integrationTest` runs only the IT. Gradle 9 also
  fails a test task that finds no tests at all. They will be replaced by real tests (Task 4 for
  unit tests, Tasks 6–7 for ITs).
- **Name rule:** unit tests end with `Test`, integration tests end with `IT`.

### `tasks/platform/task2.md`, `tasks/todo.md`, `tasks/plan.md`
- **What changed:** T2 is ticked as done, and `task2.md` got a "Result" paragraph.

## Decisions and why

| Decision | Options | Why we chose it |
|---|---|---|
| Plugins in an **included build** (`build-logic`) | `buildSrc` folder, or included build | Both work. An included build is a normal, separate build that Gradle docs recommend for sharing build logic. A change in it does not force a rebuild of every project. |
| Test versions from the **Boot BOM** | Add JUnit and AssertJ versions to the catalog | The Boot BOM already has tested versions. A second copy in the catalog could disagree with Boot. |
| BOM with `platform(...)` | the `io.spring.dependency-management` plugin | `platform()` is built into Gradle and is simpler. Spring Boot docs say both are supported. |
| Our own source set + `Test` task | Gradle's newer `jvm-test-suite` plugin | `jvm-test-suite` adds its own JUnit version, which would fight with the BOM's. The classic way uses only our versions. |
| palantir-java-format | google-java-format | Already chosen in the spec. Same idea as google-java-format, but with 120-character lines and nicer lambdas. |

## Problems we hit

1. **RED on purpose:** before the plugins existed, the build failed with "Plugin
   `campus.java-conventions` was not found". This was expected and proved the test step was real.
2. **Deprecation warning:** the first version used `val integrationTest by sourceSets.creating`.
   Gradle 9.6+ marks this style as deprecated, and Gradle 10 will remove it. Fix: use
   `sourceSets.create("integrationTest") { ... }`. After that, `--warning-mode all` showed no warnings.
3. **"Unable to delete directory" in PowerShell:** `clean` could not delete
   `build/test-results`. The cause was my own Git Bash shell, which was still "standing" in that
   folder. Windows does not delete a folder a program is using. Fix: move the shell back to the
   project root, run again, and it passed. This was not a project problem.

## How we checked it works

| Check | Command | Result |
|---|---|---|
| RED first | `./gradlew test integrationTest` (before the plugins) | Failed: plugin not found |
| GREEN | `./gradlew test integrationTest` | `BUILD SUCCESSFUL` |
| Unit tests alone | `./gradlew test --rerun-tasks --info` | Only the `:test` task ran, `KernelPlaceholderTest`: 1 test, 0 failures |
| ITs alone | `./gradlew integrationTest --rerun-tasks --info` | Only `:integrationTest` ran, `KernelPlaceholderIT`: 1 test, 0 failures |
| Format is enforced | changed `    void runs…() {` to `void   runs…( ){`, then `./gradlew build` | **Failed** at `spotlessJavaCheck` and showed the bad line. After undoing it: **passed** |
| Coverage report | `ls backend/shared-kernel/build/reports/jacoco/test/` | `html/index.html` and `jacocoTestReport.xml` exist |
| PowerShell | `.\gradlew.bat clean build` | `BUILD SUCCESSFUL`. Order: test → integrationTest → spotlessCheck |
| No versions in build files | `grep` for numbers in `*.gradle.kts` | Nothing found |

## Words to know

| Word | Meaning in simple words |
|---|---|
| Convention plugin | Our own Gradle plugin that holds shared build rules |
| Precompiled script plugin | A `.gradle.kts` file that Gradle turns into a plugin; the file name is its id |
| Included / composite build | A second Gradle build joined to the main one (`includeBuild`) |
| Source set | A group of source files compiled together: `main`, `test`, `integrationTest` |
| Unit test | A tiny, fast test of one piece of code, with no database or network |
| Integration test (IT) | A slower test of several parts together, often with real Postgres or Kafka in Docker |
| Spotless | A Gradle plugin that checks (`spotlessCheck`) and fixes (`spotlessApply`) code format |
| palantir-java-format | The set of format rules Spotless uses for our Java code |
| JaCoCo | A tool that measures which lines of code the tests ran (coverage) |
| `-parameters` | A Java compiler option that keeps parameter names in the compiled class |
| Layered jar | A Spring Boot jar split into parts, so Docker can cache each part |
| `platform(...)` | Gradle's way to import a BOM (a list of matching versions) |
| RED / GREEN | Test-first steps: first see the test fail (RED), then make it pass (GREEN) |

## Test yourself

1. Why are integration tests in a separate folder and task, not mixed with unit tests?
2. Why does `shared-kernel` use `campus.java-conventions` but not `campus.spring-app`?
3. Where does JUnit's version (6.0.3) come from? There is no number in our build files.
4. What would happen if we removed `includeBuild("build-logic")` from the root settings?
5. In step 2.6, why did we break the formatting of a file on purpose?

<details>
<summary>Answers</summary>

1. ITs are slow (later they start Docker containers). Separating them keeps `./gradlew test` fast
   while coding. `./gradlew build` still runs both, because `check` depends on `integrationTest`.
2. `shared-kernel` is a library used by the apps, not an app you run. It must stay free of Spring
   code, and it does not need a runnable `bootJar`.
3. From the Spring Boot BOM, imported with `testImplementation(platform(...))`. The BOM's version
   (4.1.3) is in `libs.versions.toml`.
4. Gradle could not find any `campus.*` plugin, and the build would fail with "Plugin ... was not
   found", the same error as our RED step.
5. To prove the rule really works. A check that never fails could be broken without anyone
   noticing. We saw it fail, then pass after the undo.

</details>

## Learn more

- [Gradle: Sharing build logic between subprojects](https://docs.gradle.org/9.9.2/userguide/sharing_build_logic_between_subprojects.html):
  convention plugins, `buildSrc` vs included build.
- [Gradle: Composite builds](https://docs.gradle.org/9.9.2/userguide/composite_builds.html):
  how `includeBuild` works.
- [Gradle: Testing in Java & JVM projects](https://docs.gradle.org/9.9.2/userguide/java_testing.html):
  test tasks, JUnit Platform, and integration test source sets.
- [Gradle: The JaCoCo plugin](https://docs.gradle.org/9.9.2/userguide/jacoco_plugin.html):
  coverage reports and later coverage rules (Checkpoint A needs 90%).
- [Spotless Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle):
  `spotlessCheck`, `spotlessApply`, and format options.
- [palantir-java-format](https://github.com/palantir/palantir-java-format): what the format
  looks like and why.
- [Spring Boot Gradle plugin: Managing dependencies](https://docs.spring.io/spring-boot/gradle-plugin/managing-dependencies.html):
  the BOM with `platform()` vs the dependency-management plugin.
- [Spring Boot Gradle plugin: Packaging executable archives](https://docs.spring.io/spring-boot/gradle-plugin/packaging.html):
  `bootJar` and layered jars.

# Task 2: Build rules in the parent pom - learning note

> Task: [task2.md](../../../tasks/platform/task2.md) · Commit: `aebfd07` · Date: 2026-11-21
>
> This task was first done with Gradle convention plugins in `build-logic/` (commit `d615a1c`).
> After the switch to Maven (spec Q2), the same rules live in the parent `pom.xml`.

## In one minute

The project will have many Java modules: `shared-kernel` now, and later 4 apps. They all need the
same rules: the same code format, unit tests, integration tests, a coverage report. Task 2 writes
those rules **once**, in the `<build>` section of the root `pom.xml`. Every module gets them
automatically, because the root pom is its parent. We also split the tests into two groups: fast
**unit tests** (`*Test`) and slower **integration tests** (`*IT`), each run by its own Maven plugin.
From now on, badly formatted code makes the build fail.

## The files

### `pom.xml` (root): the new `<build>` section

First, an important idea: **`<plugins>` vs `<pluginManagement>`**.

| Where a plugin is listed | What happens |
|---|---|
| `<build><plugins>` in the parent | The plugin **runs** in the parent and in every module |
| `<build><pluginManagement>` in the parent | The plugin is only **set up** (version, settings). It runs only in a module that lists it in its own `<plugins>` |

It is the same idea as `<dependencies>` vs `<dependencyManagement>` from Task 1.

#### What we did NOT add (Spring Boot's parent already does it)
We checked with `./mvnw help:effective-pom`. This command shows the **full** pom after all parents
are merged in. It showed that `spring-boot-starter-parent` already sets:
- Java 21 (`maven.compiler.release`, taken from our `java.version`),
- UTF-8 (`project.build.sourceEncoding`), so Vietnamese text works,
- `<parameters>true</parameters>`: keeps method parameter names in the compiled code (Spring needs them),
- the versions of surefire, failsafe and other plugins,
- the failsafe goals (`integration-test` and `verify`).

Writing these again would only make the pom longer, so we left them out.

#### 1. Unit tests: `maven-surefire-plugin`
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <skipTests>${skipUTs}</skipTests>
    </configuration>
</plugin>
```
- Surefire runs in the `test` phase. By default it runs classes whose names end with `Test`
  (also `Tests`, `TestCase`, or start with `Test`).
- `skipTests` is linked to our own property `skipUTs` (default `false`, set in `<properties>`). So
  `./mvnw verify -DskipUTs` skips **only** the unit tests. Maven's normal `-DskipTests` would skip
  the ITs too.

#### 2. Integration tests: `maven-failsafe-plugin`
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
</plugin>
```
- This short block is enough: the Boot parent already set it up in `<pluginManagement>`. Listing
  it here **turns it on**.
- Failsafe runs classes whose names end with `IT`, in two steps:
  1. `integration-test` phase: run the ITs. If one fails, do **not** stop yet.
  2. `verify` phase: now fail the build if an IT failed.

  Why two steps? Later, ITs start Docker containers. The pause between the steps lets clean-up run
  before the build stops. That is where the name "fail-safe" comes from.
- ITs will be slow (they start Postgres and Kafka in Docker), so `./mvnw test` skips them and
  stays fast while you code.

#### 3. Code format: `spotless-maven-plugin`
```xml
<java>
    <palantirJavaFormat>
        <version>${palantir-java-format.version}</version>
    </palantirJavaFormat>
    <removeUnusedImports/>
    <trimTrailingWhitespace/>
    <endWithNewline/>
</java>
...
<goal>check</goal>
```
- **Spotless** checks that every Java file follows one fixed format: **palantir-java-format**
  (like google-java-format, but with 120-character lines and nicer lambdas).
- The `check` goal runs in the `verify` phase. A badly formatted file **fails the build**.
- To fix the format automatically, run `./mvnw spotless:apply`.
- Why: everyone's code looks the same, and commits never fill up with spacing-only changes.

#### 4. Test coverage: `jacoco-maven-plugin`
```xml
<execution><id>prepare-agent</id><goals><goal>prepare-agent</goal></goals></execution>
<execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
```
- **Coverage** = which lines of code the tests actually ran.
- `prepare-agent` attaches JaCoCo's "agent" (a small helper that watches the code) to the test run.
- `report` writes the result to `target/site/jacoco/` (open `index.html` in a browser). We put it in
  the `test` phase, so `./mvnw test` already makes the report.
- Checkpoint A later needs **90% coverage** for `shared-kernel`. This report is how we will measure it.

#### 5. Spring Boot apps: `spring-boot-maven-plugin` (in `<pluginManagement>`)
```xml
<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <layers><enabled>true</enabled></layers>
            </configuration>
        </plugin>
    </plugins>
</pluginManagement>
```
- This plugin turns a module into a runnable app jar, and adds `./mvnw spring-boot:run`.
- It is in `<pluginManagement>`, so it is **off** here. `shared-kernel` is a library, not an app,
  so it must not use it. In Task 6, `core-app` will turn it on with three lines in its own pom.
- **Layered jar:** the jar is split into layers (libraries, Spring Boot loader, our code). In a
  Docker image, each layer is cached on its own. When only our code changes, Docker rebuilds only
  the small top layer, so image builds (Task 15) are fast.

### `backend/shared-kernel/src/test/java/edu/campus/kernel/KernelPlaceholderTest.java` and `KernelPlaceholderIT.java`
- **What they do:** one tiny test each. They only check the package name.
- **What changed:** `KernelPlaceholderIT` moved from `src/integrationTest/java` (the Gradle layout)
  to `src/test/java`. In Maven, unit tests and ITs live in the **same folder**. The **class name
  ending** decides which plugin runs the test: `…Test` → surefire, `…IT` → failsafe.
- **Why they exist:** to **prove** each plugin runs the right tests. They will be replaced by real
  tests (Task 4 for unit tests, Tasks 6–7 for ITs).

### `tasks/platform/task2.md`, `tasks/todo.md`, `tasks/plan.md`
- T2 is ticked as done, with a "Result" paragraph.

## Decisions and why

| Decision | Options | Why we chose it |
|---|---|---|
| Rules in the **parent pom's `<build>`** | a separate "build" module, or copies in each module | One place, inherited by every module. It is the normal Maven way. |
| **Don't repeat** what the Boot parent sets | write compiler, encoding, failsafe config ourselves | Less to read. The effective pom proved Boot already sets them. |
| ITs in **`src/test/java`, named `*IT`** | keep a separate `src/integrationTest/java` folder | This is the standard Maven layout. A separate folder needs an extra plugin (`build-helper`) just to add it. |
| Our own **`skipUTs`** switch | Maven's `-DskipTests` | `-DskipTests` skips the ITs too. We need a way to run **only** the ITs. |
| JaCoCo report in the **`test` phase** | the default `verify` phase | `./mvnw test` (the fast command) already gives you the report. |
| Boot plugin in **`<pluginManagement>`** | in `<plugins>` | In `<plugins>`, it would try to make an app jar from `shared-kernel` too. |

## Problems we hit

None that needed a fix. One thing to know: in the Spotless error message, the spaces show up as `�`
on this Windows terminal. Spotless draws spaces as `·` dots, and the terminal cannot print that
character. It is only a display issue.

## How we checked it works

| Check | Command | Result |
|---|---|---|
| What Boot already sets | `./mvnw help:effective-pom` | Java 21, UTF-8, `parameters=true`, failsafe goals, plugin versions |
| RED first | `./mvnw verify` with the IT moved to `src/test/java`, before failsafe was on | Only `KernelPlaceholderTest` ran; 0 failsafe reports |
| GREEN | `./mvnw verify` | surefire → `KernelPlaceholderTest`, failsafe → `KernelPlaceholderIT`, Spotless: 3 files clean, `BUILD SUCCESS` |
| Unit tests only | `./mvnw clean test` | Only `KernelPlaceholderTest` ran |
| ITs only | `./mvnw clean verify -DskipUTs` | "Tests are skipped." (unit), then `KernelPlaceholderIT` ran |
| Coverage report | `ls backend/shared-kernel/target/site/jacoco/` | `index.html`, `jacoco.xml`, `jacoco.csv` |
| Format is enforced | changed `    void runs…() {` to `void   runs…( ){`, then `./mvnw verify` | **BUILD FAILURE** at `spotless:check`, with the bad line shown. After undoing it: **BUILD SUCCESS** |
| PowerShell | `.\mvnw.cmd -B clean verify` | Both tests ran, Spotless clean, `BUILD SUCCESS` |

## Words to know

| Word | Meaning in simple words |
|---|---|
| `<build>` | The part of a pom that says how to build: which plugins run, and their settings |
| `<plugins>` | Plugins that really run in this project and its modules |
| `<pluginManagement>` | Plugin settings that are ready but off, until a module lists the plugin |
| Effective pom | The full pom after all parents are merged in (`./mvnw help:effective-pom`) |
| Phase | A step in Maven's build: `compile` → `test` → `package` → `integration-test` → `verify` |
| Goal | One job a plugin can do, like `spotless:check` or `jacoco:report` |
| Surefire | The Maven plugin that runs unit tests (`*Test`) |
| Failsafe | The Maven plugin that runs integration tests (`*IT`) |
| Unit test | A tiny, fast test of one piece of code, with no database or network |
| Integration test (IT) | A slower test of several parts together, often with real Postgres or Kafka in Docker |
| Spotless | A plugin that checks (`spotless:check`) and fixes (`spotless:apply`) code format |
| JaCoCo | A tool that measures which lines of code the tests ran (coverage) |
| Layered jar | A Spring Boot jar split into parts, so Docker can cache each part |
| RED / GREEN | Test-first steps: first see the check fail (RED), then make it pass (GREEN) |

## Test yourself

1. Why does the root pom list `maven-failsafe-plugin` with no settings at all?
2. Unit tests and ITs are in the same folder. How does Maven know which is which?
3. Why do we need our own `-DskipUTs`, when Maven already has `-DskipTests`?
4. Why is `spring-boot-maven-plugin` in `<pluginManagement>` and not in `<plugins>`?
5. In step 2.7, why did we break the formatting of a file on purpose?

<details>
<summary>Answers</summary>

1. The Spring Boot parent already set it up in `<pluginManagement>` (version and the
   `integration-test` + `verify` goals). Listing it in `<plugins>` just turns it on.
2. By the class name: names ending in `Test` are run by surefire, names ending in `IT` by failsafe.
3. `-DskipTests` skips both unit tests and ITs. `-DskipUTs` only switches off surefire, so you can
   run just the ITs.
4. In `<plugins>`, every module would get it, including `shared-kernel`, which is a library, not an
   app. In `<pluginManagement>`, it stays off until an app (like `core-app` in Task 6) lists it.
5. To prove the rule really works. A check that never fails could be broken without anyone
   noticing. We saw it fail, then pass after the undo.

</details>

## Learn more

- [Maven: Introduction to the build lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html):
  phases (`test`, `integration-test`, `verify`) and goals.
- [Maven: Guide to configuring plug-ins](https://maven.apache.org/guides/mini/guide-configuring-plugins.html):
  `<plugins>` vs `<pluginManagement>`, and executions.
- [Maven Surefire plugin](https://maven.apache.org/surefire/maven-surefire-plugin/): running unit tests.
- [Maven Failsafe plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/): running
  integration tests, and why there are two goals.
- [Spotless Maven plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven):
  `spotless:check`, `spotless:apply`, and format options.
- [palantir-java-format](https://github.com/palantir/palantir-java-format): what the format looks like.
- [JaCoCo Maven plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html): `prepare-agent`,
  `report`, and coverage rules.
- [Spring Boot Maven plugin: Packaging executable archives](https://docs.spring.io/spring-boot/maven-plugin/packaging.html):
  the boot jar and layers.

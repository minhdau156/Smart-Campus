# Task 6: `core-app` skeleton - learning note

> Task: [task6.md](../../../tasks/platform/task6.md) · Commit: `fc6622c` · Date: 2026-11-22

## In one minute
This task built the first real application: `core-app`, a **modular monolith**. That is one app
(one process, one jar) that is split inside into **modules** (later: course, grade, timetable, …),
and each module must not touch the private parts of the others. The app starts on port 8081,
answers `GET /api/v1/platform/ping`, connects to PostgreSQL, and runs its first database
migration. Most important: the build now **fails** if one module uses another module's
`internal` package, and a test fixture kept in the repo proves it (spec P3). We need this before
writing any feature code, so the module walls are there from day one.

## The files

### `pom.xml` (root)
- **What changed:** one new line in `<modules>`: `<module>backend/core-app</module>`.
- **Why:** Maven builds only the modules listed there. Now `./mvnw verify` builds shared-kernel
  first, then core-app (because core-app depends on it).

### `backend/core-app/pom.xml`
- **What it does:** says what core-app needs to compile, run and test.
- **Why it exists:** every Maven module has its own pom. It inherits versions from the root pom,
  so no `<version>` is written for Spring libraries.
- **Key lines:**
  ```xml
  <artifactId>spring-boot-starter-webmvc</artifactId>   <!-- REST endpoints + Tomcat -->
  <artifactId>spring-boot-starter-actuator</artifactId> <!-- /actuator/health -->
  <artifactId>spring-boot-starter-jdbc</artifactId>     <!-- connection pool -->
  <artifactId>spring-boot-starter-flyway</artifactId>   <!-- migrations -->
  <artifactId>flyway-database-postgresql</artifactId>
  <artifactId>spring-modulith-starter-core</artifactId>
  <artifactId>spring-modulith-core</artifactId>          <!-- needed to compile CoreModuleDetection -->
  ```
  - A **starter** is a bundle of libraries plus auto-configuration for one feature.
  - Spring Boot 4 split things into smaller starters: the old `spring-boot-starter-web` is now
    `spring-boot-starter-webmvc`, and Flyway needs its own `spring-boot-starter-flyway`.
  - Test-only (`<scope>test</scope>`): `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`
    (for `@WebMvcTest`), `spring-modulith-starter-test`, `archunit-junit5`,
    `spring-boot-testcontainers` and `testcontainers-postgresql`.
  ```xml
  <plugin>
      <artifactId>spring-boot-maven-plugin</artifactId>
      <configuration><profiles><profile>dev</profile></profiles></configuration>
  </plugin>
  ```
  `spring-boot:run` turns on the `dev` profile. Tests do not get it, so they never touch your
  local database by accident.
- **Like in npm:** `dependencies` vs `devDependencies`, and `"scripts": {"start": "... --profile dev"}`.

### `backend/core-app/src/main/java/edu/campus/CoreApplication.java`
- **What it does:** the `main` method that starts Spring Boot.
- **Why:** `@SpringBootApplication` turns on auto-configuration and scans `edu.campus` and its
  sub-packages. Modulith also uses this class's package as the **root**: each package directly
  below it is one module.

### `backend/core-app/src/main/java/edu/campus/CoreModuleDetection.java`
- **What it does:** tells Modulith which packages are modules: "every package directly below
  `edu.campus`, **except** `edu.campus.kernel`".
- **Why it exists:** shared-kernel's classes also live under `edu.campus` (`edu.campus.kernel`).
  Modulith scans the whole classpath, so it thought `kernel` was a module. In Modulith, only a
  module's root package is public; its sub-packages are private. So `platform` using
  `edu.campus.kernel.event.Topics` was reported as a violation. But shared-kernel is a library
  that **every** module should use. See "Problems we hit".
- **Key lines:**
  ```java
  return defaults.getModuleBasePackages(rootPackage)
          .filter(pkg -> !pkg.getName().equals(SHARED_KERNEL));
  ```
  It reuses Modulith's default rule and only removes `edu.campus.kernel`. A new package (say
  `edu.campus.grade`) still becomes a module automatically, so it cannot slip past the checks.

### `backend/core-app/src/main/java/edu/campus/platform/package-info.java`
- **What it does:** marks `edu.campus.platform` as the first module, with `@ApplicationModule`.
- **Why:** gives the module a display name and a place to document its rules. A
  `package-info.java` is a special Java file that holds annotations and docs for a whole package.

### `backend/core-app/src/main/java/edu/campus/platform/ClockConfiguration.java`
- **What it does:** creates the one `Clock` bean (`Clock.systemUTC()`).
- **Why:** the code style rule is "never call `Instant.now()`, ask for a `Clock`". A **bean** is
  an object Spring creates and hands to any class that asks for it in its constructor. Tests can
  give a fixed clock instead, so the time is always the same.
- **Why a separate class (not in `CoreApplication`):** `@WebMvcTest` loads `CoreApplication` but
  not other `@Configuration` classes. So the controller test can give its own fixed clock without
  two clocks fighting.

### `backend/core-app/src/main/java/edu/campus/platform/PingController.java`
- **What it does:** `GET /api/v1/platform/ping` answers
  `{"app":"core-app","version":"0.1.0-SNAPSHOT","time":"2026-11-22T09:22:41Z"}`.
- **Why:** the simplest way to see "the app is alive and is the version I think". The gateway
  smoke test will use it later (spec: `curl localhost:8080/api/v1/platform/ping`).
- **Key lines:**
  ```java
  record Ping(String app, String version, Instant time) {}

  PingController(Clock clock,
                 @Value("${spring.application.name}") String app,
                 @Value("${campus.version}") String version) { ... }

  @GetMapping("/ping")
  Ping ping() { return new Ping(app, version, clock.instant()); }
  ```
  Spring turns the record into JSON (with Jackson 3). `@Value` reads a setting from
  `application.yml`. Constructor injection only, as the spec says.

### `backend/core-app/src/main/resources/application.yml`
- **What it does:** the app's settings, in 3 blocks (separated by `---`): always, `dev`, `test`.
- **Key lines:**
  ```yaml
  spring:
    datasource:
      username: core_app            # the DB user made in Task 3
    flyway:
      schemas: platform
      default-schema: platform      # history table goes to platform.flyway_schema_history
      locations: classpath:db/migration/platform
    modulith:
      detection-strategy: edu.campus.CoreModuleDetection
  server:
    port: 8081
  campus:
    version: "@project.version@"    # Maven replaces this with 0.1.0-SNAPSHOT
  ```
  - `@project.version@` works because the Spring Boot parent turns on **resource filtering** for
    `application*.yml`: Maven replaces `@...@` with values from the pom while copying the file.
  - The `dev` block:
    ```yaml
    spring.config.import: optional:file:../../infra/.env[.properties]
    datasource.password: ${CORE_APP_DB_PASSWORD}
    ```
    It reads the gitignored `infra/.env` as a properties file, so the password is never written in
    the repo. `optional:` means "do not fail if the file is missing".
  - The `test` block sets nothing for the database: Testcontainers gives URL, user and password.
  - `management.endpoint.health.show-details: always` (dev and test) shows the `db` part of the
    health answer.

### `backend/core-app/src/main/resources/db/migration/platform/V202611220000__baseline.sql`
- **What it does:** the first **migration** (a numbered SQL script Flyway runs once, in order). It
  only adds a comment to the `platform` schema.
- **Why:** running any migration makes Flyway create `platform.flyway_schema_history`, the table
  that remembers which scripts already ran. That proves the per-schema setup works before Task 7
  adds the real outbox table.
- **Naming:** `V<yyyyMMddHHmm>__<what>.sql` (spec). A date as version avoids two people picking
  the same number.

### `backend/core-app/src/test/java/edu/campus/ModularityTest.java`
- **What it does:** `ApplicationModules.of(CoreApplication.class).verify()`, plus checks that
  `platform` is a module and `kernel` is not.
- **Why:** this is the guard. `verify()` fails if a module uses another module's private types or
  if modules depend on each other in a circle. It runs in under a second, with no Spring and no
  Docker.

### `backend/core-app/src/test/java/fixture/boundary/...` (3 files)
- **What they do:** a tiny fake app, **wrong on purpose**: module `alpha` (`AlphaService`) uses
  `beta.internal.BetaSecret`. It also uses `beta.BetaApi`, which is allowed.
- **Why:** P3 says the proof must be kept in the repo. A guard that is never seen failing might be
  broken without anyone noticing. The fixture lives **outside** `edu.campus`, so it never mixes
  with the real app.

### `backend/core-app/src/test/java/edu/campus/architecture/BoundaryViolationTest.java`
- **What it does:** 3 tests: Modulith's `verify()` **throws** for the fixture; our ArchUnit rule
  **fails** for the fixture (and does not report the allowed `BetaApi`); the rule **passes** for
  the real core-app.
- **Key lines:**
  ```java
  private static final ImportOption INCLUDE_TEST_CLASSES = location -> true;
  ApplicationModules.of("fixture.boundary", INCLUDE_TEST_CLASSES);
  ```
  Modulith skips test classes by default, and the fixture is in test sources. So we pass an
  **import option** that says "include everything".

### `backend/core-app/src/test/java/edu/campus/architecture/InternalPackageRule.java` + `InternalPackageRuleTest.java`
- **What it does:** our own **ArchUnit** rule (ArchUnit = a library to write architecture rules
  as unit tests): for every class, look at each class it uses; if that class is in **another**
  module and in a package named `internal`, report it.
- **Why a second check next to Modulith:** the task asked for both. Ours is small, easy to read,
  and states the exact `internal` naming rule from the spec.
- **Key lines:**
  ```java
  boolean otherModule = !moduleOf(root, targetPackage).equals(ownModule);
  if (otherModule && isInternal(root, targetPackage)) { events.add(violated(...)); }
  ```
  `moduleOf("edu.campus", "edu.campus.grade.internal.jdbc")` → `"grade"`. The table test checks
  the edge cases (e.g. `internals` is not `internal`).

### `backend/core-app/src/test/java/edu/campus/platform/PingControllerTest.java`
- **What it does:** a **slice test**: `@WebMvcTest(PingController.class)` starts only the web
  layer (no database), calls `/ping` with `MockMvcTester`, and checks the JSON. A nested
  `@TestConfiguration` gives a fixed clock, so the test can expect `2026-09-07T08:00:00Z` exactly.

### `backend/core-app/src/test/java/edu/campus/TestcontainersConfiguration.java` + `CoreApplicationIT.java`
- **What they do:** an **integration test** (`*IT`, run by failsafe). Testcontainers starts a real
  `postgres:19.0-alpine3.24` in Docker (the same image as Compose). `@ServiceConnection` gives its
  address to Spring. The whole app starts on a random port, and the test calls it over real HTTP:
  - `/actuator/health` → `UP`, and `components.db.status` → `UP`;
  - `/api/v1/platform/ping` → 200 with JSON;
  - `platform.flyway_schema_history` has the `baseline` row with `success = true`.
- **Why:** unit tests cannot show that the pieces work together (yml + Flyway + driver + Tomcat).

### `backend/shared-kernel/src/test/java/edu/campus/kernel/KernelPlaceholderIT.java` (deleted)
- It only existed to prove failsafe runs ITs ("Replace in T6/T7"). `CoreApplicationIT` now does
  that for real.

### `tasks/platform/task6.md`, `tasks/todo.md`, `tasks/plan.md`, `SPEC-platform.md`
- Boxes ticked, a "Result" paragraph added, and spec criterion **P3** ticked.

## Decisions and why
| Decision | Options | Why |
|---|---|---|
| `CoreModuleDetection` (default minus kernel) | `explicitly-annotated` strategy; ignore kernel only in tests; an annotation inside shared-kernel | Keeps "every new package is a module" (safe default). Works at runtime and in all tests, because Modulith reads it from `application.yml`. No Spring annotation needed in shared-kernel (which must stay Spring-free). |
| Fixture package outside `edu.campus` | inside `edu.campus` | It can never be mistaken for a real module, whatever import option a test uses. |
| `dev` profile set in the Maven plugin | `spring.profiles.default: dev` in yml | Only `spring-boot:run` uses dev; tests and future containers do not pick it up by accident. |
| Password from `infra/.env` via `spring.config.import` | write it in yml; ask you to set env vars | No secret in git, and no extra setup step. |
| Version from Maven filtering | `build-info` goal + `BuildProperties` | One line in yml; no extra plugin goal. |
| Plain JDK `HttpClient` in the IT | `TestRestTemplate` / `RestTestClient` | Boot 4 moved those to separate modules; the JDK client needs nothing extra and is easy to read. |
| `Clock` in its own `@Configuration` | `@Bean` in `CoreApplication` | Lets `@WebMvcTest` use a test clock without a "two Clock beans" error. |

## Problems we hit
- **Modulith treated shared-kernel as a module.** A temporary probe class in `platform` that used
  `Topics` and `Ids` made `verify()` fail with "Module 'platform' depends on non-exposed type
  edu.campus.kernel.event.Topics within module 'kernel'". Task 7 would have hit this. Fixed with
  `CoreModuleDetection`, and the same probe then passed (probe deleted).
- **`spring-modulith-core` was not on the compile classpath.** The starter brings it only for
  runtime, so `CoreModuleDetection` did not compile. Fixed by adding it as a direct dependency.
- **My ArchUnit test expected "(1 times)", but got 2.** ArchUnit counts each use (the constructor
  call and the method call), not each class. The rule was right; the test now checks that the
  allowed `BetaApi` is **not** reported instead.
- **`dependency:build-classpath` failed** with "could not find smart-campus pom". A single module
  cannot find its parent unless the parent is installed or built in the same run. Fixed with `-am`
  ("also make" the modules it needs).
- **Warnings, not failures:** Flyway 12.4 says PostgreSQL 19 is newer than the latest version it
  was verified with (18); Mockito self-attaches its agent. Both are noted in the task Result.

## How we checked it works
- **RED:** tests first; the build failed with "cannot find symbol PingController". The new
  `sharedKernelIsALibraryNotAModule` test failed before `CoreModuleDetection` existed.
- **GREEN:** `./mvnw clean verify` → shared-kernel 81 tests, core-app 16 unit tests + 3 ITs,
  `BUILD SUCCESS`. Also green with `mvnw.cmd` in PowerShell.
- **Real app (6.8):** Compose up, `./mvnw -pl backend/core-app spring-boot:run`:
  - `curl localhost:8081/actuator/health` → `"status":"UP"` with `db` `UP`;
  - `curl localhost:8081/api/v1/platform/ping` → `200`, `application/json`,
    `{"app":"core-app","version":"0.1.0-SNAPSHOT",...}`;
  - psql as `core_app` (from another container, with the password): `platform.flyway_schema_history`
    → `202611220000 | baseline | core_app | t`.
- **P3 on real code:** added `edu.campus.demo.Intruder` using `edu.campus.platform.internal.Secret`
  → `./mvnw test` failed in `ModularityTest` ("Module 'demo' depends on non-exposed type …") and in
  the ArchUnit test. Then removed.

## Words to know
| Word | Meaning in simple words |
|---|---|
| Modular monolith | One app, split inside into modules with walls between them. |
| Module (Modulith) | A top-level package below `edu.campus`. Its root package is public; sub-packages are private. |
| `internal` package | A sub-package only its own module may use. |
| Starter | A Spring Boot bundle: libraries + automatic setup for one feature. |
| Bean | An object Spring creates and gives to classes that ask for it. |
| Profile | A named set of settings turned on only when needed (`dev`, `test`). |
| Migration | A numbered SQL script that Flyway runs once, in order. |
| Slice test | A Spring test that starts only one layer (here: web). |
| Testcontainers | A library that starts real services (PostgreSQL) in Docker for tests. |
| Fixture | Fixed test data or code, here a fake app that is wrong on purpose. |
| Resource filtering | Maven replacing `@...@` placeholders in resource files while copying them. |

## Test yourself
1. Why must the violation fixture be kept in the repo, not just tried once?
2. Why did Modulith think `kernel` was a module, and why was that a problem?
3. Why is the `dev` profile turned on in the Maven plugin and not in `application.yml`?
4. What does `PingControllerTest` prove that `CoreApplicationIT` does not, and the other way round?
5. Why does the first migration exist if it creates no table?

<details><summary>Answers</summary>

1. A guard can break silently (a config change, a library update). The fixture test fails if the
   guard stops catching violations, so we always know the walls still work (spec P3).
2. Modulith scans the whole classpath under `edu.campus`, and shared-kernel's classes are in
   `edu.campus.kernel`. As a module, only its root package was public, so using
   `kernel.event.Topics` from `platform` counted as a violation, but every module must use it.
3. Then only `spring-boot:run` uses it. Tests (and later Docker containers) never pick up the
   local database settings by accident.
4. `PingControllerTest` checks the exact JSON with a fixed clock, fast and without Docker.
   `CoreApplicationIT` checks that the real pieces work together: config, Flyway, database,
   Tomcat, health.
5. Running it makes Flyway create `platform.flyway_schema_history`, which proves the per-schema
   Flyway setup and the `core_app` permissions work before real tables come in Task 7.

</details>

## Learn more
- [Spring Modulith reference: fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html) - modules, internal packages, and detection strategies.
- [Spring Modulith reference: verification](https://docs.spring.io/spring-modulith/reference/verification.html) - what `ApplicationModules.verify()` checks.
- [Spring Boot: externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) - profiles, multi-document yml, and `spring.config.import`.
- [Spring Boot: testing](https://docs.spring.io/spring-boot/reference/testing/index.html) - `@WebMvcTest`, `@SpringBootTest`, and Testcontainers with `@ServiceConnection`.
- [Flyway documentation](https://documentation.red-gate.com/flyway) - migrations and the schema history table.
- [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html) - writing your own architecture rules.
- [Testcontainers for Java](https://java.testcontainers.org/) - running real services in Docker for tests.

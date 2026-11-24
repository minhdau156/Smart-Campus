# Task 10: `registration-service` skeleton - learning note

> Task: [task10.md](../../../tasks/platform/task10.md) · Commit: `f70db3d` · Date: 2026-11-24

## In one minute
Course registration gets a huge rush: thousands of students click "register" in the same minute.
So it runs as its **own app**, `registration-service`, separate from `core-app`, and can get more
copies during the rush. This task builds that app with nothing inside yet: a ping endpoint, a
health check, its own database schema, and the outbox table. The most important part is
**isolation**. The app logs in to PostgreSQL as `registration_svc`, a user that may only touch the
`registration` schema. A test proves that every other schema answers "permission denied" (spec
D3). If someone later writes a query on another module's table "just quickly", it fails at once.

## The big picture

```
                         PostgreSQL (database "campus")
                ┌──────────────────────────────────────────────┐
 core-app ──────┤ core_app        → platform, course, grade, … │
 (port 8081)    │                                              │
 registration-  │ registration_svc → registration  ONLY        │
 service ───────┤   SELECT platform.*     → permission denied  │
 (port 8082)    │   SELECT notification.* → permission denied  │
                └──────────────────────────────────────────────┘
   who creates the users and rights?  infra/postgres/init/01-schemas-and-users.sh
   (Compose runs it on first start; our tests now run the SAME script)
```

## The files

### `pom.xml` (root)
- **What changed:** one line, `<module>backend/registration-service</module>`.
- **Why:** Maven only builds modules in this list. Without it, `./mvnw verify` would skip the
  new app.
- **Like in npm:** adding a package to the `workspaces` list of a monorepo.

### `backend/registration-service/pom.xml`
- **What it does:** says what the new app needs. It is almost the same as core-app's pom.
- **Key parts:**
  - `shared-kernel` and `platform-starter`: our own shared code. The starter brings problem JSON
    errors, the outbox settings, the retry job and the OpenAPI `Problem` schema, with no extra setup.
  - web, actuator, jdbc, Flyway, the Postgres driver, springdoc: the same as core-app.
  - `spring-modulith-starter-jdbc` + `spring-modulith-events-kafka` + `spring-boot-starter-kafka`:
    the outbox (spec D1). Later, when registration confirms a seat, it saves the event in its own
    database table first and sends it to Kafka after the commit (see lesson
    [08](../../lessons/08-outbox-idempotency.html)).
  - **Not included:** Modulith's test library and ArchUnit. This app is one single module, so
    there are no module boundaries to check.
- **Why no `<version>` on most dependencies:** the parent pom and the Spring Boot BOM set them.

### `RegistrationApplication.java`
- **What it does:** the `main` method that starts the app.
- **Why it is in `edu.campus.registration`:** `@SpringBootApplication` scans its own package and
  the packages below it for `@RestController`, `@Configuration`, …

### `ClockConfiguration.java`
- **What it does:** creates one `Clock` bean (`Clock.systemUTC()`).
- **Why it exists:** the project rule is "ask for a `Clock`, never call `Instant.now()`", so tests
  can use a fixed time. core-app has its own copy. platform-starter has no `Clock` bean, so this
  app needs one too. Without it, `PingController` would fail at startup with "no bean of type Clock".

### `PingController.java`
- **What it does:** `GET /api/v1/registration/ping` answers
  `{"app":"registration-service","version":"0.1.0-SNAPSHOT","time":"…"}`.
- **Why:** a small "are you alive?" endpoint. Later the gateway (T13) and the smoke script will
  call it.
- **Key lines:**
  ```java
  record Ping(String app, String version, Instant time) {}   // a record = the JSON shape
  @Value("${campus.version}") String version                   // filled from pom.xml at build time
  ```

### `src/main/resources/application.yml`
- **What it does:** the settings of the app.
- **Key lines:**
  ```yaml
  datasource:
    username: registration_svc        # the restricted DB user
  flyway:
    schemas: registration             # Flyway works only here
    default-schema: registration      # tables without a schema name go here
    locations: classpath:db/migration/registration
  server:
    port: 8082
  ```
  - The **outbox table's schema is not written here.** platform-starter sets
    `spring.modulith.events.jdbc.schema` to "the Flyway default schema", so it becomes
    `registration` automatically.
  - The `dev` profile reads the password from `infra/.env` (gitignored), so no password is in git.
  - The `test` profile sets no database at all: the test configuration gives it (see below).

### `db/migration/registration/V202611240000__baseline.sql` and `V202611240100__event_publication.sql`
- **What they do:** the baseline only adds a comment to the schema. Running it makes Flyway create
  `registration.flyway_schema_history`, which proves migrations work. The second file creates the
  outbox table `registration.event_publication`, copied from the Modulith jar like core-app's.
- **Why Flyway and not Modulith creates the table:** the spec says Flyway owns every table. Also,
  `registration_svc` is not allowed to create schemas, which Modulith's own setup would try (the
  trap found in T7).
- **Name format:** `V<yyyyMMddHHmm>__<what>.sql`. The time stamp keeps files in order.

### Test: `TestcontainersConfiguration.java` (the most interesting file)
- **What it does:** starts a real PostgreSQL in Docker for the integration tests (ITs), and sets it
  up **exactly like Compose**.
- **Key lines:**
  ```java
  new PostgreSQLContainer("postgres:19.0-alpine3.24")
      .withDatabaseName("campus")
      .withCopyFileToContainer(MountableFile.forHostPath(INIT_SCRIPT, 0755),
              "/docker-entrypoint-initdb.d/01-schemas-and-users.sh")   // same init script as Compose
      .withEnv("REGISTRATION_SVC_DB_PASSWORD", REGISTRATION_SVC_PASSWORD) …

  @Bean DynamicPropertyRegistrar registrationSvcLogin(PostgreSQLContainer postgres) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl);
      registry.add("spring.datasource.username", () -> "registration_svc");   // NOT the superuser
      …
  ```
  - The Postgres image runs every script in `/docker-entrypoint-initdb.d/` once, on first start.
    So the test database gets the same schemas, users and rights as your Compose one.
  - core-app's tests use `@ServiceConnection`, which logs in as the container's **superuser** (a
    user that may do everything). Here we do not use it, on purpose. The app logs in as
    `registration_svc`, so every test runs with the same small rights as in real life.
- **Why it matters:** in T7 a problem stayed hidden because the tests ran as a superuser. This
  setup cannot hide that kind of problem.

### Test: `SchemaIsolationIT.java`
- **What it does:** proves spec D3 for this app (7 tests).
- **How:**
  1. Before each test, the **superuser** creates a table `isolation_probe` with one row in each of
     the 11 other schemas. So when a query fails, we know the table exists and the failure means
     "not allowed", not "not found".
  2. Then, as `registration_svc` (the app's `JdbcTemplate`), it runs SELECT, INSERT and CREATE TABLE
     on those schemas (and CREATE TABLE in `public`).
  3. Each must fail with PostgreSQL's error code **SQLState `42501`** ("insufficient privilege").
- **Key lines:**
  ```java
  assertThatThrownBy(() -> jdbc.execute(statement))
      .satisfies(error -> assertThat(NestedExceptionUtils.getMostSpecificCause(error))
          .isInstanceOfSatisfying(SQLException.class,
              sql -> assertThat(sql.getSQLState()).isEqualTo("42501")));
  ```
  Spring wraps the database error in its own exception. `getMostSpecificCause` digs out the
  original `SQLException`, which has the exact code.
- It also checks the positive cases: `current_user` is `registration_svc`, and the app can create
  and read a table in its own schema.

### Test: `RegistrationApplicationIT.java`
- **What it does:** starts the whole app on a random port and calls it over HTTP: health is `UP`
  (database too), ping answers, an unknown URL gives problem JSON `NOT_FOUND`, the Flyway history and
  the outbox table are in `registration`.

### Test: `PingControllerTest.java`
- **What it does:** a fast web-only test (`@WebMvcTest`, no database, no Docker) with a fixed
  clock. It also checks that a wrong HTTP method gives problem JSON `METHOD_NOT_ALLOWED`.

### Test: `OpenApiExportIT.java` and `docs/api/registration-service.json`
- **What it does:** the same idea as core-app's export (T9). With `-Pexport-openapi` it writes the
  API description to `docs/api/registration-service.json`. In a normal build it compares, and fails
  if the file is out of date.
- **Difference to core-app:** core-app has one document per Modulith module
  (`/v3/api-docs/platform`). This app is one module, so it has one document (`/v3/api-docs`).
- **Why the Maven profile needed no change:** the `export-openapi` profile runs every test *named*
  `OpenApiExportIT`, in every module. A new app with a test of that name is included automatically.

### `tasks/platform/task10.md`, `tasks/todo.md`, `tasks/plan.md`, `SPEC-platform.md`
- Ticked T10 and wrote its Result. Spec I2 stays open with a note: the `registration_svc` half is
  proven, and `notification_svc` comes in T11.

## Decisions and why
| Decision | Options | Why |
|---|---|---|
| Tests run the real init script and log in as `registration_svc` | (a) `@ServiceConnection` superuser plus a separate check, (b) the real user for the whole app | (b) tests what really runs in Compose. Flyway, the outbox and the endpoints are all proven with the small rights |
| Create a probe table in other schemas before checking | Only query a missing table | Without a real table, a failure could just mean "table not found" (`42P01`), and the test would prove nothing |
| Check every other schema, not only notification and course | Only the two the task names | It costs almost nothing, and a future grant mistake on any schema is caught |
| App's own `ClockConfiguration` | Move a Clock bean into platform-starter | Smallest change for this task. Moving it would change core-app too (a separate decision) |
| One OpenAPI document named `registration-service.json` | Fake a "registration" group | The app has no modules, so one document is the honest shape |

## Problems we hit
- **Docker Desktop was not running**, so it was started before the ITs.
- **Spotless** (the code formatter check) failed on the new test files. Fixed with
  `./mvnw -pl backend/registration-service spotless:apply`.
- **A core-app test failed once:** `OutboxIT.o3_longOutage…` timed out in the first full build,
  just after Docker started. It passed when run alone and in the next full build, so it is a
  timing-sensitive (flaky) test, not something this task broke. It is noted in the task Result.
- **Port 5432 was taken** by another project's container (`postgres_container`). With your OK it was
  stopped for the Compose check and started again afterwards.

## How we checked it works
- **RED first:** `PingControllerTest` did not compile before `PingController` existed.
  `OpenApiExportIT` failed with "registration-service.json is missing" before the export.
- **Proving the isolation test can fail:** the login was switched to the superuser for one run, and
  6 of 7 tests failed ("expected registration_svc but was test"). Then the switch was undone.
- `./mvnw verify` (all modules): green. registration-service has 2 unit tests and 14 ITs.
- Against Compose: `java -jar target/registration-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev`
  → "Successfully applied 2 migrations to schema registration", `/actuator/health` `UP`,
  `/api/v1/registration/ping` 200, `/v3/api-docs` 200.
- psql as `registration_svc` in Compose: `registration.event_publication` → readable;
  `platform.event_publication` → `permission denied for schema platform`.

## Words to know
| Word | Meaning in simple words |
|---|---|
| Schema | A named folder of tables inside one database (`registration`, `course`, …) |
| Privilege / GRANT | A right a DB user has, like "may read this schema" |
| Superuser | A DB user that may do everything. Tests that use it can hide rights problems |
| SQLState `42501` | PostgreSQL's standard code for "permission denied" |
| Init script | A script the Postgres image runs once, on first start, to create users and schemas |
| Testcontainers | A library that starts real services (Postgres, Kafka) in Docker for tests |
| `DynamicPropertyRegistrar` | A Spring test bean that sets properties, like the DB URL, after a container starts |
| Isolation | Each service can only see its own data |

## Test yourself
1. Why does the test create a table in `notification` before checking that `registration_svc`
   cannot read it?
2. Why did we NOT use `@ServiceConnection` in this app's tests?
3. A teammate adds `GRANT USAGE ON SCHEMA course TO registration_svc` to the init script. Which
   test fails, and why is that good?
4. Why does the outbox table end up in `registration` even though `application.yml` never says so?
5. Why is registration a separate app and not a module inside core-app?

<details><summary>Answers</summary>

1. Without the table, the query could fail with "table does not exist" (`42P01`) even if the
   rights were wrong. With a real table, only missing rights can make it fail with `42501`.
2. `@ServiceConnection` logs in as the container's superuser, who may do everything. Then the
   tests could not show rights problems, like the one found in T7. We want the tests to use the
   same small rights as Compose.
3. `SchemaIsolationIT` (SELECT on `course`). The tests run the same init script as Compose, so a
   bad grant is caught in the build before it reaches anyone.
4. platform-starter sets `spring.modulith.events.jdbc.schema` to the Flyway default schema, and
   this app's Flyway default schema is `registration`.
5. During the registration rush it needs many more copies than the rest of the app, and its load
   must not slow down the rest. A separate app can scale and fail on its own (Architecture doc).
</details>

## Learn more
- [PostgreSQL: Privileges](https://www.postgresql.org/docs/current/ddl-priv.html): what GRANT, USAGE and CREATE on a schema mean.
- [PostgreSQL: Error codes](https://www.postgresql.org/docs/current/errcodes-appendix.html): the SQLState list (`42501`, `42P01`, …).
- [Docker Hub: postgres image, "Initialization scripts"](https://hub.docker.com/_/postgres): how `/docker-entrypoint-initdb.d/` works.
- [Spring Boot: Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html): container beans, `@ServiceConnection` and dynamic properties.
- [Spring Modulith: Event Publication Registry](https://docs.spring.io/spring-modulith/reference/events.html): the outbox table and its settings.
- Lessons in this repo: [04 PostgreSQL, schemas and Flyway](../../lessons/04-postgres-flyway.html), [10 Testing](../../lessons/10-testing.html).

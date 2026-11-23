# Task 8: Problem JSON handler (`platform-starter`) - learning note

> Task: [task8.md](../../../tasks/platform/task8.md) · Commit: `bfa7dea` · Date: 2026-11-23

## In one minute
This task built a new shared library, `platform-starter`. Any app that adds it as a dependency gets
three things automatically:
1. **The same error JSON for every error.** This is RFC 7807 "problem details", plus our own
   `code` and `traceId` fields. Stack traces are never shown.
2. **A trace id for every request.** It goes in a response header and in the logs, so one error can
   be found in the logs.
3. **The outbox setup from Task 7.** The settings and the retry job moved here from `core-app`.

We need this now because three more apps are coming (registration, notification, gateway). They
must all answer errors the same way, and use the outbox the same way, without copying code.

## The big picture

```
 a request ─► TraceIdFilter (first!)  makes traceId "4bf9…", puts it in the log context
              and in the header X-Trace-Id
                 │
                 ▼
            controller ── throws ──► CampusProblemHandler ─► ProblemBody as application/problem+json
                                        CampusProblem      → its own code (e.g. 409 CONFLICT)
                                        @Valid failed      → 400 VALIDATION_FAILED + errors[]
                                        no such URL        → 404 NOT_FOUND
                                        other Spring error → Spring's status (e.g. 405)
                                        anything else      → 500 INTERNAL_ERROR (full error only in the log)
```

Example body:

```json
{"type":"https://campus.edu/problems/conflict","title":"Conflict","status":409,
 "detail":"Section S1 has no seats left","code":"CONFLICT","traceId":"4bf92f35..."}
```

## How a "starter" switches itself on
A Spring Boot **starter** is a jar that configures itself. The app writes no annotation and no
import; it just lists the jar in its `pom.xml`. Spring Boot finds the jar's setup through special
files inside it:

| File in the jar | Who reads it | What it says |
|---|---|---|
| `META-INF/spring/…AutoConfiguration.imports` | Spring Boot, at startup | "Here are my auto-configuration classes" |
| `META-INF/spring/…AutoConfigureWebMvc.imports` | `@WebMvcTest` slice tests | "Load the problem handling in controller tests too" |
| `META-INF/spring.factories` | Spring Boot and Spring Modulith | "Here is my settings loader and my module-detection rule" |

## The files

### `pom.xml` (root)
- **What changed:** a new line in `<modules>`: `backend/platform-starter`, between shared-kernel and core-app.
- **Why:** Maven must build the starter before core-app, because core-app now depends on it.

### `backend/platform-starter/pom.xml`
- **What it does:** lists what the starter needs.
- **Key lines:**
  ```xml
  <artifactId>spring-boot-starter-webmvc</artifactId>
  <optional>true</optional>
  ```
- **Why `optional`:** the starter compiles against Spring MVC and Modulith, but it does **not**
  force them on an app. The gateway (Task 13) uses WebFlux, not Spring MVC, so it must not get
  Tomcat. Each part of the starter switches on only if the app has those libraries itself.
- **Like in npm:** similar to `peerDependencies`: "I work with this library, but you bring it."

### `src/main/java/edu/campus/starter/trace/TraceIdFilter.java`
- **What it does:** runs first for every request. It makes a new 32-hex-character id and:
  - puts it in the **MDC**, so log lines can show it;
  - puts it in the `X-Trace-Id` response header.
- **Key lines:**
  ```java
  MDC.put(MDC_KEY, traceId);
  response.setHeader(HEADER, traceId);   // before the controller: headers can't change after the body
  try { chain.doFilter(request, response); }
  finally { MDC.remove(MDC_KEY); }       // the thread is reused for other requests
  ```
- **Why `finally`:** web servers reuse threads. Without `remove`, the next request on the same thread
  could log the old id.

### `trace/TraceIdProvider.java` and `trace/MdcTraceIdProvider.java`
- **What they do:** `TraceIdProvider` is a small interface: "give me the current trace id".
  `MdcTraceIdProvider` reads the id from the MDC, and makes a new one if there is none (for
  example in a scheduled job).
- **Why an interface:** this id is **temporary**. In Task 16, OpenTelemetry brings real trace ids.
  Task 16 then only has to register another `TraceIdProvider` bean; the handler does not change.

### `problem/ProblemBody.java`
- **What it does:** a record with the exact fields of every error body:
  `type, title, status, detail, code, traceId`, plus `errors` for validation.
- **Key line:** `@JsonInclude(NON_NULL)`. If `errors` is null, Jackson leaves the field out. That is
  why only validation errors have it.
- **Why a record of our own, and not Spring's `ProblemDetail`:** the field list and order are fixed
  and easy to read in one place.

### `problem/CampusProblemHandler.java`
- **What it does:** turns every exception that leaves a controller into a `ProblemBody`.
- **How:** it extends Spring's `ResponseEntityExceptionHandler`. That class already knows the
  correct status for each Spring MVC error (404, 405, 415, bad JSON …). We only replace the
  **body** it writes.

  | Method | Catches | Result |
  |---|---|---|
  | `handleCampusProblem` | our `CampusProblem` | status, title, type and code from its `ProblemCode` |
  | `handleMethodArgumentNotValid` | `@Valid @RequestBody` failed | 400 + one `{field, message}` per bad field |
  | `handleHandlerMethodValidationException` | constraints on parameters (e.g. `@Min` on a query parameter) | 400 + field list |
  | `handleNoResourceFoundException` | no endpoint for the URL | 404 "No endpoint GET /api/…" |
  | `handleExceptionInternal` | all other Spring errors | Spring's status; code = a `ProblemCode` if one matches, else the status name (`METHOD_NOT_ALLOWED`) |
  | `handleUnexpected` | everything else | 500, generic text, full error in the log |
- **The safety rule (500):**
  ```java
  log.error("Unhandled exception, traceId={}", traceId, exception); // full error: log only
  "An unexpected error occurred. Quote traceId " + traceId + " when you report it."  // client
  ```
  The exception message can contain secrets (the test uses "db password is hunter2"). So the client
  gets only the trace id, and support uses it to find the full error in the log.

### `problem/ProblemHandlingAutoConfiguration.java`
- **What it does:** creates the beans:
  - the `TraceIdProvider`;
  - the filter, registered with `Ordered.HIGHEST_PRECEDENCE` so it runs first;
  - the handler.
- **Key annotations:**
  - `@AutoConfiguration`: "this is a starter configuration".
  - `@ConditionalOnWebApplication(type = SERVLET)`: only in Spring MVC apps.
  - `@ConditionalOnMissingBean`: "only if the app doesn't already have one". This lets Task 16 bring
    its own provider.

### `config/PlatformDefaultsEnvironmentPostProcessor.java` + `campus-platform-defaults.yml`
- **What they do:** the yml file holds the shared settings that moved from core-app:
  - the outbox schema;
  - schema creation off;
  - republish on restart, and the staleness check;
  - the Kafka producer serializers and `acks: all`;
  - the retry interval.

  The post-processor adds this file to every app's settings at the **lowest priority**
  (`addLast`).
- **Why lowest priority:** an app can still override any value. core-app's `test` profile sets
  `retry-interval: 2s` and short timeouts, and those win.
- **A clever line:**
  ```yaml
  schema: ${spring.flyway.default-schema:public}
  ```
  The outbox table goes into the app's own Flyway schema: `platform` for core-app, and later
  `registration` for registration-service. One shared file, still correct for every app.
- **Like in Maven:** a parent pom gives default plugin versions that a child pom may override. This
  does the same for application settings.

### `outbox/OutboxRetryJob.java` + `outbox/OutboxAutoConfiguration.java`
- **What changed:** the retry job from Task 7 moved here (git shows it as a rename). A small
  auto-configuration now creates it.
- **Key annotations:**
  ```java
  @AutoConfiguration(afterName = "...EventPublicationAutoConfiguration")
  @ConditionalOnClass(FailedEventPublications.class)  // Modulith's events library is present
  @ConditionalOnBean(FailedEventPublications.class)   // ...and Modulith really created the registry
  @EnableScheduling
  ```
  `afterName` makes Spring check our condition *after* Modulith has made its beans. Otherwise the
  bean check could run too early and always say "no".

### `modulith/CampusModuleDetection.java`
- **What it does:** the rule "every package directly below `edu.campus` is a module, **except**
  `edu.campus.kernel` and `edu.campus.starter`". These two are shared libraries, not modules.
- **Why it moved:** it was core-app's `CoreModuleDetection`. Now that the starter also lives under
  `edu.campus`, the rule must know about it. Every app gets the same rule.
- **How Modulith finds it:** through `META-INF/spring.factories`. If an app does not set
  `spring.modulith.detection-strategy`, Modulith looks for a rule there. So core-app needs no
  setting at all.

### `META-INF/spring.factories` and the two `.imports` files
See the table in "How a starter switches itself on". These are plain text lists of class names.

### `backend/core-app/pom.xml`
- **What changed:**
  - new dependency `platform-starter`;
  - `spring-modulith-core` removed, because core-app no longer has its own detection class.

### `backend/core-app/src/main/resources/application.yml`
- **What changed:** all `spring.modulith.*`, `spring.kafka.producer.*` and `campus.outbox.*` lines
  are gone from the normal and `dev` parts. A comment now says they come from the starter. The
  `test` profile keeps its short timeouts and 2 s retry.

### Deleted: `core-app/…/CoreModuleDetection.java`, `core-app/…/platform/internal/OutboxRetryJob.java`
Both moved into the starter (see above).

### Tests in the starter
- **`testapp/TestApp.java`:** a tiny app, only for tests, with endpoints that fail on purpose:
  - `/test/conflict` throws a `CampusProblem`;
  - `/test/courses` has `@Valid` with `@NotBlank` and `@Max(30)`;
  - `/test/boom` throws a "secret" exception.
- **`problem/ProblemJsonIT.java`:** runs the test app on a real port and calls it over HTTP. It covers:
  - the 4 cases from the spec;
  - a 405 (wrong HTTP method);
  - the trace id header on every response.

  For each case it checks:
  - the status and `application/problem+json`;
  - the **exact** set of fields;
  - that the body's `traceId` equals the header;
  - that no secret, class name or stack trace appears;
  - that the full error **is** in the log next to the same traceId (using `OutputCaptureExtension`,
    which captures what the app printed).
- **`trace/TraceIdFilterTest.java`:** the id is set during the request and in the header, and the
  MDC is clean afterwards.
- **`config/PlatformDefaultsTest.java`:**
  - the defaults are loaded;
  - the schema follows `spring.flyway.default-schema`;
  - the app's own value wins.

### Tests in core-app
- **`NoOwnPlatformConfigTest.java`** (new) reads core-app's `application.yml`. It fails if any
  outbox, Kafka-producer or Modulith key is set outside the `test` profile. It also checks that the
  old classes are gone. This guards "core-app has no outbox config of its own".
- **`ModularityTest.java`:** new test that `starter` is not a module.
- **`CoreApplicationIT.java`:** new test that an unknown URL in core-app gives 404 problem JSON with
  a traceId. This proves the starter is really active in the app.
- **`platform/PingControllerTest.java`:** new test that a wrong method in a `@WebMvcTest` gives 405
  problem JSON.

### `SPEC-platform.md`, `tasks/platform/task8.md`, `tasks/todo.md`, `tasks/plan.md`
- E1 is ticked.
- The D1 notes say where the outbox settings live now.
- The task is ticked and has a Result paragraph.

## Decisions and why
| Decision | Options | Why |
|---|---|---|
| A separate `platform-starter` module | Copy the setup into each app | One place to change, and the same behavior in all apps (spec: "the same in all apps") |
| Starter dependencies `optional`, parts `@Conditional…` | Normal dependencies | The gateway (WebFlux) must not get Spring MVC and Tomcat |
| Extend `ResponseEntityExceptionHandler` | Write every Spring error case ourselves | Spring already knows the right status for ~15 MVC errors; we only change the body |
| Our own `ProblemBody` record | Spring's `ProblemDetail` + extra properties | The exact shape and field order are clear in one small file |
| Codes not in `ProblemCode` use the status name (`METHOD_NOT_ALLOWED`) | Add many new `ProblemCode`s to shared-kernel | Every error still has a `code`, and shared-kernel stays small |
| Defaults through an `EnvironmentPostProcessor` at the lowest priority | Put the yml in each app | Apps can still override, for example the test timeouts |
| Detection rule through `spring.factories` | A property in each app | Apps need zero Modulith settings |
| Temporary random trace id behind an interface | Wait for OpenTelemetry (T16) | Errors need a traceId now; T16 only swaps one bean |

## Problems we hit
1. **`@WebMvcTest` failed with "No bean TraceIdProvider".** core-app's main class is in
   `edu.campus`, so component scan also scans `edu.campus.starter`, and it found the handler.
   But a slice test does not load normal auto-configurations, so the provider was missing.
   **Fix:** list `ProblemHandlingAutoConfiguration` in `…AutoConfigureWebMvc.imports`. Now
   controller tests get the same error JSON as the real app, which is good anyway. In the full app,
   `@ConditionalOnMissingBean` prevents a second handler.
2. **The starter's own test app failed with "No bean FailedEventPublications".** The Modulith API is
   on the starter's test classpath (optional dependencies are visible to the module's own tests),
   but there is no registry, so the retry job had nothing to inject. **Fix:** add
   `@ConditionalOnBean(FailedEventPublications.class)`, checked after Modulith's auto-configuration.
3. **Docker Desktop was stopped** (the computer restarted), so the Testcontainers ITs could not
   start. I started Docker Desktop, and the ITs passed.
4. **The manual check against Compose was skipped.** Another project's container
   (`postgres_container`, postgres:14) restarted with Docker and holds port 5432, so the Compose
   Postgres cannot start. It was left alone, because it belongs to another project.

## How we checked it works
- RED first:
  - `ProblemJsonIT` failed 5 of 5 before the handler existed;
  - `PlatformDefaultsTest` did not compile before the post-processor existed.
- `./mvnw -pl backend/platform-starter,backend/core-app -am verify`: green.
  - starter: 6 unit tests + 6 ITs;
  - core-app: 24 unit tests + 10 ITs, including all 4 Task 7 `OutboxIT` tests, now running on the
    starter's settings.
- `./mvnw verify` (all modules): **BUILD SUCCESS**.
- Manual check you can do once port 5432 is free (stop the other project's container first):
  ```bash
  docker compose -f infra/docker-compose.yml up -d --wait
  cd backend/core-app && ../../mvnw spring-boot:run
  curl -i localhost:8081/api/v1/nope        # 404, application/problem+json, X-Trace-Id header
  curl -i -X POST localhost:8081/api/v1/platform/ping   # 405, code METHOD_NOT_ALLOWED
  ```

## Words to know
| Word | Meaning in simple words |
|---|---|
| RFC 7807 / problem details | A standard JSON shape for HTTP errors: `type`, `title`, `status`, `detail` |
| `application/problem+json` | The content type that says "this body is a problem details error" |
| Starter | A jar that configures itself in any Spring Boot app that adds it |
| Auto-configuration | A configuration class that Spring Boot loads by itself, often only when a condition is true |
| `@Conditional…` | "Create this bean only if…" (a class exists, a bean exists, it's a web app, …) |
| Trace id | One id per request, used to find everything about that request (logs, errors, traces) |
| MDC | Mapped Diagnostic Context: a per-thread map the logger can print with every line |
| Filter | Code that runs around every HTTP request, before and after the controller |
| `@ControllerAdvice` | A class whose `@ExceptionHandler` methods apply to all controllers |
| Slice test (`@WebMvcTest`) | A test that starts only the web layer, not the whole app |
| Component scan | Spring searching packages for classes marked `@Component` (and similar) |
| `EnvironmentPostProcessor` | A hook that may add or change settings very early at startup |

## Test yourself
1. Why does a 500 response show only a generic message and a trace id, and not the exception message?
2. Why are the starter's Spring MVC and Modulith dependencies `optional`?
3. Why are the shared defaults added with the **lowest** priority?
4. Why is `TraceIdProvider` an interface, when there is only one implementation today?
5. Why did `@WebMvcTest` need its own `.imports` file?

<details>
<summary>Answers</summary>

1. Exception messages can contain secrets or internal details (SQL, passwords, class names). The
   client gets the trace id, and support finds the full error in the log with it.
2. So the starter does not force them on every app. The gateway uses WebFlux and must not get
   Spring MVC. Each part switches on only when the app has the library (`@ConditionalOnClass`).
3. So any app can still override a value in its own `application.yml`, for example the test
   profile's 2 s retry interval and short Kafka timeouts.
4. The id is temporary. In Task 16, OpenTelemetry gives the real trace id. Then only a new
   `TraceIdProvider` bean is needed (`@ConditionalOnMissingBean` lets it replace ours), and the
   handler stays the same.
5. A slice test loads only a small list of web auto-configurations, not all of them. Adding ours to
   that list means controller tests show the same error JSON as the running app.
</details>

## Learn more
- [Spring Boot: creating your own auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html) - starters, `.imports` files, and `@Conditional…` annotations.
- [Spring Framework: error responses (RFC 9457 / 7807)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) - `ProblemDetail` and `ResponseEntityExceptionHandler`.
- [Spring Framework: controller advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html) - how `@ControllerAdvice` and `@ExceptionHandler` work.
- [Spring Boot: customizing the environment](https://docs.spring.io/spring-boot/how-to/application.html#howto.application.customize-the-environment-or-application-context) - `EnvironmentPostProcessor` and `spring.factories`.
- [RFC 7807: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc7807) - the standard error shape itself.
- [SLF4J manual: MDC](https://www.slf4j.org/manual.html#mdc) - putting values like a trace id into every log line.

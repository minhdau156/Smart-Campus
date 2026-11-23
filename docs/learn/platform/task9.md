# Task 9: OpenAPI docs (backend part) - learning note

> Task: [task9.md](../../../tasks/platform/task9.md) · Commit: `4963de5` · Date: 2026-11-23

## In one minute
Our backend now **describes its own API**. Each app serves a machine-readable document (OpenAPI
JSON) that lists every endpoint, its inputs, its answers and its errors. It also serves a web page
(Swagger UI) where people can read and try the endpoints. In `core-app` there is **one document
per module** (`platform` today; `course`, `grade` … later), and new modules get theirs
automatically. A test exports these documents to `docs/api/*.json` in a fixed, sorted format, and
the files are committed to git. Later, the website (the frontend part of A1) will generate its
TypeScript types from them, so frontend and backend cannot silently disagree.

## The big picture

```
  @RestController code ──► springdoc reads it at startup ──► /v3/api-docs/platform  (OpenAPI JSON)
                                     ▲                       /swagger-ui.html       (web page)
   platform-starter adds:            │
     - Problem schema, "default" error on every endpoint
     - server "/" (no random port), JSON as default type
   core-app adds:
     - one group per Modulith module (ModuleApiGroups)

  OpenApiExportIT ──► GET /v3/api-docs/platform ──► sort + pretty print
        ├─ with -Pexport-openapi: WRITE  docs/api/core-app-platform.json   (you commit it)
        └─ normal build:          COMPARE with the committed file → fail if out of date
                                         │
                       later: npm run gen:api → TypeScript types for the website
```

## The files

### `pom.xml` (root)
- **What changed (1): a version for `springdoc-openapi-starter-common`.** This is springdoc's core
  without the UI. The starter needs only this part.
- **What changed (2): a new Maven profile `export-openapi`.**
  ```xml
  <profile>
      <id>export-openapi</id>
      <properties>
          <skipUTs>true</skipUTs>                    <!-- no unit tests -->
          <it.test>OpenApiExportIT</it.test>          <!-- only this IT -->
          <failsafe.failIfNoSpecifiedTests>false</failsafe.failIfNoSpecifiedTests>
      </properties>
      ... <systemPropertyVariables><openapi.export>true</openapi.export> ...
  ```
  `./mvnw verify -Pexport-openapi` turns on this profile.
  - It runs only the export test, so it's fast.
  - It gives that test the flag `openapi.export=true`, meaning "write the files".
  - `failIfNoSpecifiedTests=false` lets modules without this test (shared-kernel, the starter) pass.
- **Like in npm:** a script in `package.json`, such as `"export:api": "..."`, that runs one job.

### `backend/platform-starter/pom.xml`
- **What changed:** an `optional` dependency on `springdoc-openapi-starter-common`.
- **Why optional:** as in Task 8, the starter does not force springdoc on every app. Each app picks
  its own flavor: `core-app` uses `webmvc-ui`, and the gateway will use `webflux-ui`.

### `starter/openapi/ProblemSchemaCustomizer.java`
- **What it does:** it changes the generated document in two ways.
  1. It adds two **schemas**: `Problem` (our error JSON from Task 8) and `FieldError`.
  2. On **every** endpoint, it adds a `default` response: "an error answers with a `Problem`".
- **Key lines:**
  ```java
  operation.getResponses().computeIfAbsent("default", key -> new ApiResponse()
          .content(new Content().addMediaType("application/problem+json",
                  new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problem")))));
  ```
  - In OpenAPI, `default` means "any status not listed". So `200` keeps its own description, and
    all errors point to `Problem`.
  - `computeIfAbsent` = "only add it if the controller didn't write its own".
- **Why `GlobalOpenApiCustomizer`:** a normal customizer runs only for the default document. The
  "global" kind runs for **every group** (every module).
- **Why it matters for the website:** one `Problem` type in TypeScript, used for every error.

### `starter/openapi/OpenApiAutoConfiguration.java`
- **What it does:** it creates the customizer, plus an `OpenAPI` bean with a title, a version, and
  the server `"/"`.
- **Why the server `"/"`:** without it, springdoc writes the address of the request, such as
  `http://localhost:54321`. In tests the port is random, so every export would differ. Behind the
  gateway, the address would also be wrong. `"/"` means "the same host this document came from".
- **`@ConditionalOnClass(GlobalOpenApiCustomizer.class)`:** it switches on only in apps that have
  springdoc.

### `starter/…/AutoConfiguration.imports`
- **What changed:** one new line, `edu.campus.starter.openapi.OpenApiAutoConfiguration`, so Spring
  Boot finds the new configuration.

### `starter/…/campus-platform-defaults.yml`
- **What changed:**
  ```yaml
  springdoc:
    default-produces-media-type: application/json
  ```
- **Why:** our controllers don't say what they return, so springdoc wrote `"*/*"` (anything). With
  `application/json`, the generated TypeScript types are exact.

### `core-app/pom.xml`
- **What changed (1): `springdoc-openapi-starter-webmvc-ui` was added.** This gives `/v3/api-docs`
  and `/swagger-ui.html`.
- **What changed (2): `spring-modulith-core` is back.** `ModuleApiGroups` reads the module list at
  compile time. Task 8 had removed this dependency, because nothing in core-app needed it then.

### `core-app/…/platform/ModuleApiGroups.java`
- **What it does:** it makes one OpenAPI **group** per Modulith module.
- **Key lines:**
  ```java
  @Bean
  List<GroupedOpenApi> openApiGroupPerModule() {
      return groupsFor(ApplicationModules.of(CoreApplication.class));
  }
  // per module:
  GroupedOpenApi.builder()
          .group(module.getIdentifier().toString())       // "platform"  → /v3/api-docs/platform
          .displayName(module.getDisplayName())           // "Platform"  → shown in Swagger UI
          .packagesToScan(module.getBasePackage().getName()) // only edu.campus.platform's endpoints
          .build();
  ```
- **Why one bean that is a list:** we can't write one `@Bean` method per module, because we don't
  know the modules when writing the code. springdoc accepts a single bean that holds a
  `List<GroupedOpenApi>`.
- **Why from `ApplicationModules`:** it's the same list `ModularityTest` checks. When a later module adds
  `course`, `/v3/api-docs/course` appears with no change here.

### `core-app/…/platform/internal/OutboxProbeController.java`
- **What changed:** a new annotation, `@Hidden`.
- **Why:** the probe endpoint is a dev tool from Task 7, not part of the real API. Without
  `@Hidden`, it would be in the committed docs, and the website would get a type for it.

### `core-app/src/test/java/edu/campus/OpenApiExportIT.java`
- **What it does:** it starts the real app (with Testcontainers), then runs 4 tests:
  1. Swagger UI answers 200, and `swagger-config` lists exactly one group URL per module.
  2. `Problem` has `code` and `traceId`, and ping's `default` response points to `Problem`.
  3. The dev probe is **not** in the docs.
  4. Export or compare:
     ```java
     if (EXPORT) Files.writeString(file, json);                  // -Pexport-openapi
     else assertThat(Files.readString(file)).isEqualTo(json);    // normal build
     ```
- **Deterministic output** ("the same input always gives the same file"):
  ```java
  JsonMapper.builder()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)  // sorted keys
          .enable(SerializationFeature.INDENT_OUTPUT)              // pretty print
  ... .replace("\r\n", "\n") + "\n";                              // same line ends on Windows/Linux
  ```
- **Why compare in a normal build:** say someone changes an endpoint but forgets to export. The
  build fails with "…is out of date. Run ./mvnw verify -Pexport-openapi and commit the diff." The
  docs can't drift away from the code.

### `core-app/…/platform/ModuleApiGroupsTest.java` and `starter/…/openapi/ProblemSchemaCustomizerTest.java`
- **`ModuleApiGroupsTest`** (fast, no Spring) checks:
  - there is exactly one group, `platform`;
  - it scans `edu.campus.platform`;
  - there are no groups for `kernel` or `starter`.
- **`ProblemSchemaCustomizerTest`** checks:
  - the schemas and their fields;
  - the `default` response points to `Problem`, and `200` is kept;
  - a controller's own `default` is not replaced.

### `docs/api/core-app-platform.json`
- **What it is:** the exported document, committed to git. Open it to see what the website will
  get: the `Ping` and `Problem` schemas, and the `/api/v1/platform/ping` path with its `200` and
  `default` responses.

### `SPEC-platform.md`, `tasks/platform/task9.md`, `tasks/todo.md`, `tasks/plan.md`
- A1 has a note "backend part done". It is **not** ticked, because the frontend part
  (`npm run gen:api`) is still to come.
- The task is ticked, with a Result paragraph.

## Decisions and why
| Decision | Options | Why |
|---|---|---|
| One group per Modulith module, from `ApplicationModules` | One big document; hand-written groups | It matches the architecture, and new modules get docs automatically |
| `List<GroupedOpenApi>` bean | A bean registrar (tried first); `springdoc.group-configs` properties | It works with springdoc's startup checks and is the simplest code |
| Export by a test, not a build plugin | A Maven plugin that starts the app | The test reuses the Testcontainers setup, and the same test also guards against out-of-date files |
| Compare in normal builds, write only with the profile | Always write | A normal build never changes your files by surprise; it only tells you |
| Server `"/"` | springdoc's default (the request URL) | Same file every run; correct behind the gateway |
| `default` response = `Problem` | List 400/404/500 on each endpoint | One rule for all endpoints, matching Task 8's "every error has this shape" |
| `@Hidden` on the probe | Docs show dev endpoints | Committed docs = the real API only |

## Problems we hit
1. **The groups gave 404.** My first version used a `BeanDefinitionRegistryPostProcessor`: code
   that registers one bean per module at startup. But springdoc decides very early whether groups
   exist (`@ConditionalOnBean(GroupedOpenApi)`), before that code ran, so no groups were made.
   **Fix:** springdoc also accepts one `List<GroupedOpenApi>` bean, which is visible early. It's
   simpler, too.
2. **Bean name clash.** The class `ModuleApiGroups` is itself a bean named `moduleApiGroups`, and
   the `@Bean` method had the same name. **Fix:** the method is now `openApiGroupPerModule`.
3. **"Unresolved compilation problem".** VS Code's Java extension compiles into the same `target/`
   folder. While files were half-edited, it wrote broken classes there, and Maven reused them.
   **Fix:** `./mvnw clean verify`. If you ever see this message from a Maven run, run with `clean`.
4. **Small test mistakes:**
   - The swagger library sorts `required` alphabetically, so the test now ignores order.
   - Swagger UI's `name` is the display name ("Platform"), so the test now checks the group URL.
5. **No browser check on :8081.** Compose's Postgres can't start while another project's container
   holds port 5432. The IT checks Swagger UI (page 200, one group per module) instead.

## How we checked it works
- RED first: the new tests didn't compile, then failed (404 and missing file), before the code existed.
- `./mvnw clean verify -Pexport-openapi` was run **twice**, and the two files were byte-identical
  (`cmp` printed "IDENTICAL").
- **Guard seen failing:** I changed the committed file's title to `"STALE"`. A normal build then
  failed with "out of date. Run ./mvnw verify -Pexport-openapi…". The file was restored afterwards.
- `./mvnw verify` (everything): BUILD SUCCESS.
  - kernel: 81 tests
  - starter: 9 unit tests + 6 ITs
  - core-app: 27 unit tests + 14 ITs
- Try it yourself, once port 5432 is free:
  ```bash
  docker compose -f infra/docker-compose.yml up -d --wait
  cd backend/core-app && ../../mvnw spring-boot:run
  # browser: http://localhost:8081/swagger-ui.html  → drop-down "Platform"
  curl localhost:8081/v3/api-docs/platform
  ```

## Words to know
| Word | Meaning in simple words |
|---|---|
| OpenAPI | A standard JSON (or YAML) format that describes a REST API: paths, inputs, answers |
| springdoc | A library that reads Spring controllers and writes the OpenAPI document for you |
| Swagger UI | A web page that shows an OpenAPI document and lets you try the endpoints |
| Schema | The description of one JSON shape, such as `Problem` with its fields and types |
| `$ref` | "See this other schema", e.g. `#/components/schemas/Problem` |
| `default` response | OpenAPI's "any other status": used here for all errors |
| Group (`GroupedOpenApi`) | One separate document for part of the API, here one per module |
| Deterministic | The same input always gives exactly the same output |
| Maven profile | A named set of extra build settings, turned on with `-P<name>` |
| Code generation | Making code (here, TypeScript types) automatically from a description |

## Test yourself
1. Why does a normal build *compare* the docs instead of writing them?
2. Why must the server URL be `"/"` and not the real address?
3. Why does a new module get its own API group without any code change?
4. Why are all errors described as one `default` response, and not as 400, 404, 500 on each endpoint?
5. Why is the outbox probe `@Hidden`?

<details>
<summary>Answers</summary>

1. So a build never changes your files by surprise, and so forgetting to export is caught: the
   build fails and tells you the command to run.
2. springdoc would write the request's address, which includes the random test port. Every export
   would then differ, and the address would be wrong behind the gateway. `"/"` is stable.
3. `ModuleApiGroups` builds the groups from `ApplicationModules`, Modulith's list of modules. A new
   package under `edu.campus` is a new module, so it gets a group.
4. Every error has the same shape (Task 8), so one rule describes them all. The website then needs
   only one `Problem` type.
5. It's a dev tool, not part of the real API. The committed docs, and the website types made from
   them, should contain only real endpoints.
</details>

## Learn more
- [springdoc-openapi documentation](https://springdoc.org/) - groups (`GroupedOpenApi`), customizers, `@Hidden`, and properties.
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html) - the format itself: paths, responses, `default`, components and `$ref`.
- [Swagger UI](https://swagger.io/tools/swagger-ui/) - the web page that shows the docs.
- [Maven: introduction to build profiles](https://maven.apache.org/guides/introduction/introduction-to-profiles.html) - how `-Pexport-openapi` works.
- [Maven Failsafe: running a single test](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html) - the `it.test` property.
- [openapi-typescript](https://openapi-ts.dev/) - the tool the frontend will use to turn these files into TypeScript types.

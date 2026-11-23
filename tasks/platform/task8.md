# T8: Problem JSON handler (`platform-starter`)

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T7](task7.md) · [T9 →](task9.md)

`Scope M · Depends on: T7 · Closes: E1`

**Goal:** every error from every app looks the same: RFC 7807 with `code` and `traceId`, and no stack traces.

**Subtasks**
- [x] **8.1** Create the `backend/platform-starter` library (auto-configuration), with a `META-INF/spring/…AutoConfiguration.imports` file.
- [x] **8.2** Map `CampusProblem` → `application/problem+json` with `type, title, status, detail, code, traceId`.
- [x] **8.3** Map bean-validation failures → 400 `VALIDATION_FAILED`, including a list of field errors.
- [x] **8.4** Map an unknown route → 404 `NOT_FOUND`, and any unhandled exception → 500
  `INTERNAL_ERROR` with a generic detail. The full error is logged with the `traceId`.
- [x] **8.5** Add a temporary `traceId` provider (generated per request, echoed in a response header). T16 replaces it with the real trace id.
- [x] **8.6** Move the outbox and Modulith settings from `core-app` into the starter, and make `core-app` depend on the starter.
- [x] **8.7** Write `ProblemJsonIT` covering the 4 cases. Re-run the T7 ITs.

**Done when**
- [x] All 4 error kinds return the exact problem shape. No stack trace ever appears in a response
- [x] `traceId` is always present
- [x] `core-app` has no outbox config of its own, and the T7 ITs still pass

**Verify**
- [x] `./mvnw -pl backend/platform-starter,backend/core-app -am verify` passes

**Result (2026-11-23):** new module `backend/platform-starter` (package `edu.campus.starter`).
Problem JSON: `CampusProblemHandler` (extends Spring's `ResponseEntityExceptionHandler`) writes a
`ProblemBody` record as `application/problem+json`: `CampusProblem` → its code; `@Valid` failure →
400 `VALIDATION_FAILED` + `errors[{field,message}]`; unknown route (`NoResourceFoundException`) →
404 `NOT_FOUND`; other Spring errors keep Spring's status and get a code from it (405 →
`METHOD_NOT_ALLOWED`); anything else → 500 `INTERNAL_ERROR`, generic detail with the traceId, full
error logged as `Unhandled exception, traceId=…`. TraceId: `TraceIdFilter` (first filter) makes a
32-hex id per request, puts it in MDC `traceId` and header `X-Trace-Id`; `TraceIdProvider` bean
(`@ConditionalOnMissingBean`, T16 replaces it). Outbox/Modulith settings moved out of core-app into
`campus-platform-defaults.yml` (added at lowest priority by `PlatformDefaultsEnvironmentPostProcessor`);
`OutboxRetryJob` → `OutboxAutoConfiguration` (`@ConditionalOnClass` + `@ConditionalOnBean`
`FailedEventPublications`); `CoreModuleDetection` → `CampusModuleDetection` (excludes
`edu.campus.kernel` and `edu.campus.starter`), registered in `META-INF/spring.factories`, so core-app
sets no `spring.modulith.*` key. Tests: starter 6 unit + 6 IT (`ProblemJsonIT`: the 4 cases + 405 +
traceId header); core-app 24 unit (+ `NoOwnPlatformConfigTest`, starter-not-a-module, 405 in
`@WebMvcTest`) + 10 IT (+ 404 problem JSON; T7 `OutboxIT` still green). **Found:** `@WebMvcTest`
found the handler by component scan (`edu.campus.starter` is below `edu.campus`) but not the
TraceIdProvider → the auto-config is also listed in `…AutoConfigureWebMvc.imports`, so slice tests
get the same error JSON. **Found:** the starter's own test app has the Modulith API but no registry
→ the retry job needs `@ConditionalOnBean` too. Manual run against Compose skipped: another
project's `postgres_container` (postgres:14) holds port 5432 after the Docker restart.

**Files:** `backend/platform-starter/pom.xml`, `ProblemHandlingAutoConfiguration.java`, `CampusProblemHandler.java`, `ProblemBody.java`, `TraceIdFilter.java`, `TraceIdProvider.java`, `MdcTraceIdProvider.java`, `OutboxAutoConfiguration.java`, `OutboxRetryJob.java`, `CampusModuleDetection.java`, `PlatformDefaultsEnvironmentPostProcessor.java`, `campus-platform-defaults.yml`, `…AutoConfiguration.imports`, `…AutoConfigureWebMvc.imports`, `spring.factories`, `ProblemJsonIT.java`; core-app `pom.xml`, `application.yml`, tests

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

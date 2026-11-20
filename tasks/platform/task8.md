# T8: Problem JSON handler (`platform-starter`)

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T7](task7.md) · [T9 →](task9.md)

`Scope M · Depends on: T7 · Closes: E1`

**Goal:** every error from every app looks the same: RFC 7807 with `code` and `traceId`, and no stack traces.

**Subtasks**
- [ ] **8.1** Create the `backend/platform-starter` library (auto-configuration), with a `META-INF/spring/…AutoConfiguration.imports` file.
- [ ] **8.2** Map `CampusProblem` → `application/problem+json` with `type, title, status, detail, code, traceId`.
- [ ] **8.3** Map bean-validation failures → 400 `VALIDATION_FAILED`, including a list of field errors.
- [ ] **8.4** Map an unknown route → 404 `NOT_FOUND`, and any unhandled exception → 500
  `INTERNAL_ERROR` with a generic detail. The full error is logged with the `traceId`.
- [ ] **8.5** Add a temporary `traceId` provider (generated per request, echoed in a response header). T16 replaces it with the real trace id.
- [ ] **8.6** Move the outbox and Modulith settings from `core-app` into the starter, and make `core-app` depend on the starter.
- [ ] **8.7** Write `ProblemJsonIT` covering the 4 cases. Re-run the T7 ITs.

**Done when**
- [ ] All 4 error kinds return the exact problem shape. No stack trace ever appears in a response
- [ ] `traceId` is always present
- [ ] `core-app` has no outbox config of its own, and the T7 ITs still pass

**Verify**
- [ ] `./mvnw -pl backend/platform-starter,backend/core-app -am verify` passes

**Files:** `backend/platform-starter/pom.xml`, `ProblemHandlingAutoConfiguration.java`, `CampusProblemHandler.java`, `…AutoConfiguration.imports`, `ProblemJsonIT.java`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

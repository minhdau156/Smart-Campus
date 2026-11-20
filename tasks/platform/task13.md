# T13: Gateway routes + smoke script

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T12](task12.md) · [T14 →](task14.md)

`Scope M · Depends on: T12 · Closes: I3`

**Goal:** one entry point on :8080 sends each request to the right app and returns problem JSON
when it can't.

**Subtasks**
- [ ] **13.1** Create `backend/gateway` (Spring Cloud Gateway server-webflux) on port 8080.
- [ ] **13.2** Configure routes in `application.yml`, with backend URIs from env so containers can override them:
  - `/api/v1/registration/**` → :8082;
  - `/api/v1/notification/**` and `/ws/**` (WebSocket) → :8083;
  - all other `/api/v1/**` → :8081.
- [ ] **13.3** Write a reactive problem handler: no route → 404, backend down → 503, backend timeout → 504. All return problem JSON, never an HTML error page.
- [ ] **13.4** Write `GatewayRoutingIT` with stub backends (e.g. MockWebServer). Cover each route plus the 404, 503 and 504 cases.
- [ ] **13.5** Write `infra/smoke.sh` with a configurable `BASE_URL`. It checks the 3 pings through the gateway, then app health:
  - in `bootRun` mode, through each app's port;
  - in container mode, through `docker compose ps` health status.

  It exits non-zero on any failure.
- [ ] **13.6** Manual: start everything → `bash infra/smoke.sh` exits 0. Stop one app → it exits non-zero.

**Done when**
- [ ] The 3 pings answer through :8080
- [ ] Unknown path → 404 problem JSON. App down → 503 problem JSON
- [ ] `smoke.sh` exits 0 when everything is up and non-zero otherwise

**Verify**
- [ ] `./gradlew :backend:gateway:build` passes
- [ ] Step 13.6 was observed

**Files:** `backend/gateway/build.gradle.kts`, `GatewayApplication.java`, `application.yml`, `GatewayProblemHandler.java`, `GatewayRoutingIT.java`, `infra/smoke.sh`

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

# T19: Web scaffold + generated API types

> Module `platform` · Phase 5: Web scaffold, CI, handover · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T18](task18.md) · [T20 →](task20.md)

`Scope M · Depends on: T15 · Closes: P2, A1 (frontend part)`

**Goal:** an empty web app that builds and talks to the gateway, using API types generated from the backend docs.

**Subtasks**
- [ ] **19.1** Scaffold `frontend/web` with Vite + React + TypeScript (pinned versions, `strict: true`).
- [ ] **19.2** Add ESLint + Prettier and the `lint` and `format` scripts.
- [ ] **19.3** Add Vitest + Testing Library (jsdom) and the `test` script.
- [ ] **19.4** Add the Vite dev proxy `/api` → `http://localhost:8080`, and create the empty folders `src/shell`, `src/modules` and `src/shared`.
- [ ] **19.5** Add a `gen:api` script that runs `openapi-typescript` over `docs/api/*.json` into
  `src/shared/api/generated/`. Exclude that folder from linting, and mark it "generated, do not edit".
- [ ] **19.6** Write `src/shared/api/http.ts`: a fetch client typed by the generated paths that
  turns problem responses into a `ProblemError` with `code`, `traceId` and, for 429, `retryAfter`.
  Unit-test a 409 and a 429.
- [ ] **19.7** Make the home page call `/api/v1/platform/ping` and show the result. Check it in the browser.

**Done when**
- [ ] `lint`, `test` and `build` all pass, and the ping call is type-checked against the generated types
- [ ] `ProblemError` carries `code`, `traceId` and `retryAfter` (tested)
- [ ] With the backend running, the page shows the ping result

**Verify**
- [ ] `npm --prefix frontend/web run gen:api && npm --prefix frontend/web run lint && npm --prefix frontend/web run test && npm --prefix frontend/web run build`
- [ ] Manual check at http://localhost:5173

**Files:** `frontend/web/package.json`, `vite.config.ts`, `src/main.tsx`, `src/shared/api/http.ts`, `src/shared/api/http.test.ts` (+ generated config and types)

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

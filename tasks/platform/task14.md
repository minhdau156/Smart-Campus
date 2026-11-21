# T14: Gateway per-IP rate limiting

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T13](task13.md) · [T15 →](task15.md)

`Scope M · Depends on: T13 · Closes: R1`

**Goal:** no single IP can flood the platform. Callers get a clear 429 that says when to retry.

**Subtasks**
- [ ] **14.1** Add a Redis-backed `RedisRateLimiter`, with replenish rate and burst read from config (defaults 20/s and 40).
- [ ] **14.2** Write `TrustedProxyIpKeyResolver`. It uses the remote address, and reads `X-Forwarded-For` only when the request comes from a configured trusted-proxy range.
- [ ] **14.3** Apply the limiter as a default filter on all `/api/v1/**` routes. Document how a
  route can override it, since `registration` will add a per-user limit later.
- [ ] **14.4** Write `RateLimitedProblemFilter`, which turns a 429 into problem JSON with
  `code: RATE_LIMITED` and a `Retry-After` header (in seconds, at least 1).
- [ ] **14.5** Write `RateLimitIT` (Testcontainers Redis) covering four cases:
  - a burst from IP A → 429 with the right shape;
  - IP B → unaffected;
  - a spoofed `X-Forwarded-For` from an untrusted source → ignored;
  - two gateway instances → they share one budget.

**Done when**
- [ ] Going over the limit gives 429 + `Retry-After` + problem JSON, and other IPs are unaffected
- [ ] A spoofed `X-Forwarded-For` doesn't change the key
- [ ] The limit is shared across gateway instances through Redis

**Verify**
- [ ] `./mvnw -pl backend/gateway -am verify -DskipUTs` passes

**Files:** `gateway/…/RateLimitConfig.java`, `gateway/…/TrustedProxyIpKeyResolver.java`, `gateway/…/RateLimitedProblemFilter.java`, `application.yml`, `RateLimitIT.java`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

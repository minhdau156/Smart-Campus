# T15: App container images + Compose `apps` profile

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T14](task14.md) · [T16 →](task16.md)

`Scope S · Depends on: T14 · Closes: K1`

**Goal:** all 4 apps also run as containers, matching the Arch §8 dev setup.

**Subtasks**
- [ ] **15.1** Write `infra/docker/spring-app.Dockerfile`, a multi-stage build:
  - stage 1 extracts the `bootJar` layers;
  - stage 2 is a pinned Temurin 21 JRE that copies the layers (dependencies first, app code last) and runs as a non-root user;
  - the jar path comes in as a build argument.
- [ ] **15.2** Write `.dockerignore`, so only the built jars are sent to Docker.
- [ ] **15.3** Add the 4 apps to Compose under profile `apps`:
  - build args point at each jar;
  - env points at the in-network hosts (`postgres`, `kafka:29092`, `redis`);
  - each app waits for healthy infra (`depends_on`);
  - healthchecks on `/actuator/health`;
  - only the gateway's port 8080 is published;
  - memory limits plus `JAVA_TOOL_OPTIONS` with `-XX:MaxRAMPercentage`.
- [ ] **15.4** Run `./gradlew bootJar`, then `docker compose -f infra/docker-compose.yml --profile apps up -d --build --wait`, then `smoke.sh` in container mode.
- [ ] **15.5** Check `docker compose exec gateway id -u` is not 0. Change one line of code, rebuild, and confirm the dependency layers come from the cache.
- [ ] **15.6** Note the memory used by the `apps` profile, for the README.

**Done when**
- [ ] All 4 app containers are healthy, and `smoke.sh` passes against them
- [ ] The containers don't run as root
- [ ] A code-only rebuild reuses the cached dependency layers

**Verify**
- [ ] Steps 15.4 and 15.5 were observed

**Files:** `infra/docker/spring-app.Dockerfile`, `.dockerignore`, `infra/docker-compose.yml`, `infra/.env.example`

---

## ◆ Checkpoint C: Platform runs end to end
- [ ] `./gradlew build` is green
- [ ] `smoke.sh` passes with apps started by `bootRun` **and** with the `apps` profile
- [ ] Spec criteria P1, P3, I1–I3, O1–O6, E1, A1 (backend), R1 and K1 are ticked
- [ ] 🙋 **Human review**

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

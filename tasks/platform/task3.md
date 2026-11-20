# T3: Compose core infra, schemas, DB users *(can run in parallel with T1–T2)*

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T2](task2.md) · [T4 →](task4.md)

`Scope S · Depends on: — · Closes: I1, I2 (infra part)`

**Goal:** one command starts every piece of infrastructure, healthy, with the database already
split into schemas and per-service users.

**Subtasks**
- [ ] **3.1** Create `infra/.env.example` (dev-only users, passwords, ports). Compose reads `infra/.env`, which is gitignored.
- [ ] **3.2** Add **Postgres** (pinned tag, named volume, `pg_isready` healthcheck, mounts `infra/postgres/init/`).
- [ ] **3.3** Write `01-schemas-and-users.sql`:
  - create schemas `identity, course, timetable, registration, grade, announcement, club, marketplace, lostfound, notification, payment, platform`;
  - user `core_app` owns every schema except `registration` and `notification`;
  - `registration_svc` gets `registration` only, and `notification_svc` gets `notification` only;
  - create a separate `keycloak` database and user;
  - revoke default `public` privileges.
- [ ] **3.4** Add **Redis** (pinned, `redis-cli ping` healthcheck).
- [ ] **3.5** Add **Kafka** in KRaft mode, single broker:
  - two listeners: host `localhost:9092` and in-network `kafka:29092`;
  - `auto.create.topics.enable=false`;
  - a capped log size;
  - a healthcheck.
- [ ] **3.6** Add **Keycloak** in dev mode on :8180, using the `keycloak` DB, with admin user from `.env` and a health endpoint enabled.
- [ ] **3.7** Add **OpenSearch** (single node, security off, heap 512m), **MinIO** (:9000, console :9001) and **Mailpit** (:8025).
- [ ] **3.8** Run `docker compose -f infra/docker-compose.yml up -d --wait`, time it, note peak
  memory from `docker stats`, and run the psql permission check.

**Done when**
- [ ] `up -d --wait` returns with every service healthy in under 3 minutes (warm image cache)
- [ ] `\dn` lists all 12 schemas. As `registration_svc`, selecting from a `notification` table gives "permission denied"
- [ ] No image uses `latest` or a tag without a version

**Verify**
- [ ] `docker compose -f infra/docker-compose.yml ps` shows every service `healthy`
- [ ] Peak memory is written down (it goes into the README in T21)

**Files:** `infra/docker-compose.yml`, `infra/.env.example`, `infra/postgres/init/01-schemas-and-users.sql`, `.gitignore`

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

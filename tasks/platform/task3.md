# T3: Compose core infra, schemas, DB users *(can run in parallel with T1–T2)*

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T2](task2.md) · [T4 →](task4.md)

`Scope S · Depends on: — · Closes: I1, I2 (infra part)`

**Goal:** one command starts every piece of infrastructure, healthy, with the database already
split into schemas and per-service users.

**Subtasks**
- [x] **3.1** Create `infra/.env.example` (dev-only users, passwords, ports). Compose reads `infra/.env`, which is gitignored.
- [x] **3.2** Add **Postgres** (pinned tag, named volume, `pg_isready` healthcheck, mounts `infra/postgres/init/`).
- [x] **3.3** Write `01-schemas-and-users.sh` (runs the SQL with `psql`, so passwords come from `infra/.env`):
  - create schemas `identity, course, timetable, registration, grade, announcement, club, marketplace, lostfound, notification, payment, platform`;
  - user `core_app` owns every schema except `registration` and `notification`;
  - `registration_svc` gets `registration` only, and `notification_svc` gets `notification` only;
  - create a separate `keycloak` database and user;
  - revoke default `public` privileges.
- [x] **3.4** Add **Redis** (pinned, `redis-cli ping` healthcheck).
- [x] **3.5** Add **Kafka** in KRaft mode, single broker:
  - two listeners: host `localhost:9092` and in-network `kafka:29092`;
  - `auto.create.topics.enable=false`;
  - a capped log size;
  - a healthcheck.
- [x] **3.6** Add **Keycloak** in dev mode on :8180, using the `keycloak` DB, with admin user from `.env` and a health endpoint enabled.
- [x] **3.7** Add **OpenSearch** (single node, security off, heap 512m), **RustFS** (S3-compatible, replaces MinIO: :9000, console :9001) and **Mailpit** (:8025).
- [x] **3.8** Run `docker compose -f infra/docker-compose.yml up -d --wait`, time it, note peak
  memory from `docker stats`, and run the psql permission check.

**Done when**
- [x] `up -d --wait` returns with every service healthy in under 3 minutes (warm image cache)
- [x] `\dn` lists all 12 schemas. As `registration_svc`, selecting from a `notification` table gives "permission denied"
- [x] No image uses `latest` or a tag without a version

**Verify**
- [x] `docker compose -f infra/docker-compose.yml ps` shows every service `healthy`
- [x] Peak memory is written down (it goes into the README in T21)

**Result (2026-11-21):** Images: postgres 19.0-alpine3.24 · redis 8.8.4-alpine3.23 · apache/kafka
4.4.0 · keycloak 26.8.2 · opensearch 3.10.0 · rustfs 1.0.6 (replaces MinIO, spec Q6) · mailpit v1.34.0.
First `up --wait`: **68 s** (includes DB init). Restart with data: **57 s**. **Peak memory: about 2.2 GB**
(OpenSearch ~1 GB, Keycloak ~720 MB, Kafka ~320 MB; the rest under 100 MB each), for the T21 README.
psql checks (as another container on the Compose network, with passwords): `\dn` lists the 12
schemas; `registration_svc` and `core_app` get "permission denied for schema notification";
`registration_svc` is denied in `platform`; a wrong password is refused (`scram-sha-256`); the
`keycloak` user cannot connect to `campus`. Inside the Postgres container, loopback logins are
`trust` (image default), so password tests must come from outside it. Spec I1 ticked. I2 stays open
until its IT (`SchemaIsolationIT`, T10). Kafka refuses writes to unknown topics.

**Files:** `infra/docker-compose.yml`, `infra/.env.example`, `infra/postgres/init/01-schemas-and-users.sh` (`.gitignore` already had `infra/.env`)

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

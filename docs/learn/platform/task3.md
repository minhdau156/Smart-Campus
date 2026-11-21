# Task 3: Compose core infra, schemas, DB users - learning note

> Task: [task3.md](../../../tasks/platform/task3.md) · Commit: `35d433b` · Date: 2026-11-21

## In one minute

Our apps need other programs to run: a database (Postgres), a cache (Redis), a message broker
(Kafka), a login server (Keycloak), a search engine (OpenSearch), a file store (RustFS) and a fake
mail server (Mailpit). Task 3 starts **all seven** with one command, using **Docker Compose**:
`docker compose -f infra/docker-compose.yml up -d --wait`. The database also gets set up on the
first start: one **schema** (a folder for tables) per module, and a separate database **user** per
app, so an app can only touch its own data. All seven are healthy in about one minute.

## The files

### `infra/docker-compose.yml`
- **What it does:** describes seven containers: which image (program and version) to run, its
  settings, its ports, where it keeps data, and how to check that it is healthy.
- **Why it exists:** without it, every developer would install and configure seven programs by
  hand, each a bit differently. With it, everyone gets the same setup with one command, and can
  delete it all just as easily.
- **Key parts (using Postgres as the example):**
  ```yaml
  postgres:
    image: postgres:19.0-alpine3.24
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?copy infra/.env.example to infra/.env}
    ports:
      - "127.0.0.1:5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql
      - ./postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$${POSTGRES_USER}\" -d \"$${POSTGRES_DB}\""]
  ```
  - **`image`**: the exact version. `19.0-alpine3.24` = Postgres 19.0 on Alpine Linux 3.24. We never
    use `latest`, because `latest` changes over time, and then the same setup gives different results.
  - **`${POSTGRES_PASSWORD:?...}`**: read the value from `infra/.env`. If it is missing, Compose
    stops with the message after `?`. This makes a forgotten `.env` easy to spot.
  - **`127.0.0.1:5432:5432`**: "computer port : container port". `127.0.0.1` means only **this**
    computer can connect, not other computers on your Wi-Fi.
  - **`volumes`**: `postgres-data` is a **named volume**: a storage area that Docker keeps when the
    container is removed, so your data survives a restart. The second line shares our
    `postgres/init` folder into the container (`:ro` = read-only).
  - **`healthcheck`**: a command Docker runs again and again (`pg_isready` asks "can I connect?").
    `up --wait` waits until **every** health check passes. `$$` means "a real `$`" inside the
    container, not a Compose variable.

- **The other six services:**

  | Service | Image | Port(s) | What it is for | Health check |
  |---|---|---|---|---|
  | Redis | `redis:8.8.4-alpine3.23` | 6379 | Fast in-memory store (cache, seat counters later) | `redis-cli ping` → `PONG` |
  | Kafka | `apache/kafka:4.4.0` | 9092 | Message broker: apps send each other events | `kafka-broker-api-versions.sh` |
  | Keycloak | `quay.io/keycloak/keycloak:26.8.2` | 8180 | Login server (empty now; set up in `identity`) | `/health/ready` on port 9000, asked with plain bash |
  | OpenSearch | `opensearchproject/opensearch:3.10.0` | 9200 | Search engine | `_cluster/health` is yellow or green |
  | RustFS | `rustfs/rustfs:1.0.6` | 9000, 9001 | File store with the Amazon S3 protocol (photos later) | `curl /health/ready` |
  | Mailpit | `axllent/mailpit:v1.34.0` | 1025 (SMTP), 8025 (web) | Fake mail server: catches every email so you can read it | `mailpit readyz` |

- **Kafka settings worth knowing:**
  - **KRaft mode**: Kafka manages itself. Older Kafka needed a second program (ZooKeeper).
    `KAFKA_PROCESS_ROLES: broker,controller` means this one node does both jobs.
  - **Two listeners** (two "doors" into Kafka): `localhost:9092` for apps running on your computer
    (`spring-boot:run`), and `kafka:29092` for apps running as containers (Task 15). Each kind of app
    must use the address that works from where it runs.
  - **`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`**: a typo in a topic name must fail, not silently
    create a new, wrong topic. Topics are created on purpose in Task 12.
  - **`KAFKA_LOG_RETENTION_BYTES`**: event topics will keep data forever by time (`retention.ms=-1`,
    spec D5). This size cap (~100 MB per partition) stops the dev disk from filling up.
  - **`CLUSTER_ID`**: Kafka needs a 16-byte id written in base64 (22 characters).

- **Keycloak's health check** looks strange:
  ```bash
  exec 3<>/dev/tcp/localhost/9000 && printf 'GET /health/ready HTTP/1.1...' >&3 && grep -q '"status": "UP"' <&3
  ```
  The Keycloak image has no `curl`. Bash can open a network connection itself (`/dev/tcp/host/port`),
  so we send a tiny HTTP request by hand and look for `"status": "UP"` in the answer.

### `infra/.env.example` (and your own `infra/.env`)
- **What it does:** lists every setting Compose needs: database name, passwords, admin users.
- **Why it exists:** passwords should not be written inside `docker-compose.yml`. The `.example`
  file is a template that goes into git. Each developer copies it to `infra/.env`, which is
  **gitignored**, so real values never reach GitHub. Here they are dev-only values anyway.
- **How:** `cp infra/.env.example infra/.env`. Compose reads `.env` from the folder of the compose file.

### `infra/postgres/init/01-schemas-and-users.sh`
- **What it does:** creates the database structure on the **first** start: 3 app users, 12 schemas,
  their owners, and a separate `keycloak` database.
- **Why it exists:** spec decision **D3** says each split-out service may only touch its own schema.
  This script is where that rule becomes real.
- **When it runs:** the Postgres image runs every file in `/docker-entrypoint-initdb.d/` **only
  when the data volume is empty**. To run it again: `docker compose -f infra/docker-compose.yml down -v`
  (`-v` deletes the volumes, so **all local data**).
- **Why `.sh` and not `.sql`:** a `.sql` file cannot read environment variables, so the passwords
  would have to be typed here **and** in `.env`. The script runs the same SQL with `psql` and puts in
  `$CORE_APP_DB_PASSWORD` and the others from the environment.
- **Key lines:**
  ```sql
  REVOKE ALL ON DATABASE "campus" FROM PUBLIC;
  CREATE ROLE registration_svc LOGIN PASSWORD '...';
  CREATE SCHEMA platform     AUTHORIZATION core_app;
  CREATE SCHEMA registration AUTHORIZATION registration_svc;
  ALTER ROLE registration_svc SET search_path = registration;
  ```
  - `REVOKE ... FROM PUBLIC`: `PUBLIC` means "every user". By default, every user may connect. We
    remove that, then give `CONNECT` only to our three app users.
  - `CREATE ROLE ... LOGIN`: a database user that can log in.
  - `AUTHORIZATION core_app`: the schema's **owner**. An owner can do everything in its schema.
    Nobody else gets any rights there, because we never `GRANT` any.
  - `search_path`: the schema Postgres looks in when a query does not name one. So
    `SELECT * FROM seat` by `registration_svc` means `registration.seat`.

  | Schema(s) | Owner | Who can use it |
  |---|---|---|
  | identity, course, timetable, grade, announcement, club, marketplace, lostfound, payment, platform | `core_app` | only `core_app` |
  | registration | `registration_svc` | only `registration-service` |
  | notification | `notification_svc` | only `notification-service` |

### Changed docs: `SPEC-platform.md`, `CAPABILITY-MAP.md`, `tasks/platform/task3.md`, `tasks/todo.md`, `tasks/plan.md`
- Spec decision **Q6**: RustFS replaces MinIO (see "Decisions"). Spec **I1** is ticked.
- T3 is ticked, with a "Result" paragraph that holds the times and memory numbers.

## Decisions and why

| Decision | Options | Why we chose it |
|---|---|---|
| **RustFS** instead of MinIO (spec Q6) | RustFS, SeaweedFS, or no file store yet | MinIO stopped publishing Docker images, so it cannot be pinned. RustFS is built as a MinIO replacement (same ports 9000/9001, S3 API, Apache-2.0). Our code will use the plain S3 API, so only this container changes. You chose RustFS. |
| **Newest GA image** for each service | older "long-term" versions | The spec asks for the newest final (GA) release. We skipped `-rc` (release candidate) tags and floating tags (`latest`, `3`, `v1`). |
| Ports on **127.0.0.1** only | `0.0.0.0` (all network cards) | Dev passwords are simple, so nothing should be reachable from other computers. |
| Init script as **`.sh`** | `.sql` as the task said | Passwords are written once, in `.env`. |
| **Separate `keycloak` database** | Keycloak tables in `campus` | Keycloak's ~90 tables never mix with ours, and its user cannot even connect to `campus`. |
| **Kafka size cap** in dev | no cap | Event topics keep data forever (spec D5). The cap protects the dev disk (plan risk table). |

## Problems we hit

1. **MinIO has no image anymore.** Docker Hub says "object not found", and Quay needs a login. It
   needed a decision, so I asked, and you picked RustFS. The spec was updated first (Q6).
2. **RustFS's help text was empty**, so I could not read its health URL. Fix: start a throwaway
   RustFS container and try the likely URLs. `/health/ready` answered `200`.
3. **Kafka cluster id too long.** My first `CLUSTER_ID` was 25 characters. Kafka needs a base64
   16-byte id (22 characters). Fix: the base64 of the 16-character text `SmartCampusDev11`.
4. **A wrong password was accepted** in my first check. Not a security hole: I ran `psql` **inside**
   the Postgres container, and the official image trusts its own loopback (`pg_hba.conf` lines with
   `trust` for `127.0.0.1` and `::1`). Every other connection uses `scram-sha-256`. Fix: test from
   **another container** on the Compose network, the way the apps will connect. There, the wrong
   password was refused.

## How we checked it works

| Check | Command | Result |
|---|---|---|
| RED first | `docker compose -f infra/docker-compose.yml up -d --wait` (before the file existed) | Failed: file not found |
| All healthy, timed | same command | All 7 `Healthy`, **68 s** (first start, with DB init) |
| Restart with data | `down`, then `up -d --wait` | **57 s** |
| 12 schemas | `psql -c '\dn'` | 12 schemas + `public`, owners as in the table above |
| Own schema works | as `notification_svc` / `registration_svc` / `core_app`: create, insert, select | OK |
| Other schema denied | as `registration_svc`: `SELECT * FROM notification.probe` | `permission denied for schema notification` |
| | as `core_app`: same | `permission denied for schema notification` |
| | as `registration_svc`: `CREATE TABLE platform.x` | `permission denied for schema platform` |
| Passwords enforced | from another container, wrong password | `password authentication failed` |
| Keycloak isolated | as `keycloak`: connect to `campus` | `permission denied for database "campus"` |
| Ports from this computer | `curl` to 9200, 8180, 9000, 8025, 9001 | 200 / 302 (redirect) / 200 / 200 / 200 |
| No auto-create | write to `no.such.topic` | `UNKNOWN_TOPIC_OR_PARTITION`, topic not created |
| Peak memory | `docker stats`, sampled every 3 s while starting | about **2.2 GB** in total |
| Pinned tags | grep `image:` lines | 7 exact versions, no `latest` |
| Java build still green | `./mvnw verify` | passed |

## Words to know

| Word | Meaning in simple words |
|---|---|
| Docker image | A packaged program with everything it needs, at one exact version |
| Container | A running copy of an image, isolated from your computer |
| Docker Compose | A tool that starts several containers together from one YAML file |
| Volume | Storage that Docker keeps even when the container is removed |
| Health check | A command Docker repeats to see if a container works |
| `up -d --wait` | Start in the background (`-d`) and wait until all health checks pass |
| Schema | A named folder for tables inside one database |
| Role / user | A database login. The owner of a schema can do everything in it |
| `PUBLIC` | In Postgres: "every user". We take its default rights away |
| `pg_hba.conf` | Postgres's file of login rules: who may connect, and how they prove it |
| `scram-sha-256` | A secure password check (the password is never sent in plain text) |
| KRaft | Kafka's built-in mode for managing itself, without ZooKeeper |
| Listener | An address and port where Kafka accepts connections |
| S3-compatible | Speaks the same file-storage protocol as Amazon S3 |
| GA / RC | GA = final release. RC = "release candidate", still being tested |

## Test yourself

1. Why does every port start with `127.0.0.1:`?
2. You changed a password in `infra/.env`, but Postgres still uses the old one. Why, and what do
   you do?
3. Why does Kafka have two client addresses, `localhost:9092` and `kafka:29092`?
4. Why was a wrong password accepted in the first test, and why is that not a problem?
5. What would go wrong if Kafka could create topics automatically?

<details>
<summary>Answers</summary>

1. So only this computer can connect. Other computers on the network cannot reach our dev
   services, which use simple passwords.
2. The init script runs only once, when the data volume is empty. Run
   `docker compose -f infra/docker-compose.yml down -v` (this deletes all local data) and start again.
3. "localhost" means "this machine" from wherever you are. For an app on your computer, Kafka is at
   `localhost:9092`. For an app inside another container, `localhost` would be that container
   itself, so it must use the service name `kafka:29092`.
4. The test ran inside the Postgres container, and the image trusts its own loopback address. Apps
   always connect from outside (your computer or another container), where `scram-sha-256` checks
   the password. We proved that from another container.
5. A typo in a topic name (`enrolment` vs `enrollment`) would silently create a new topic. Events
   would go there and nobody would read them. With auto-create off, the typo fails right away.

</details>

## Learn more

- [Docker Compose file reference: services](https://docs.docker.com/reference/compose-file/services/):
  `image`, `ports`, `volumes`, `healthcheck`, `depends_on`.
- [Docker Compose: interpolation](https://docs.docker.com/reference/compose-file/interpolation/):
  `${VAR}`, `${VAR:?error}` and `$$`.
- [Postgres Docker image](https://hub.docker.com/_/postgres): init scripts in
  `/docker-entrypoint-initdb.d` and the environment variables.
- [PostgreSQL: Schemas](https://www.postgresql.org/docs/current/ddl-schemas.html): schemas,
  owners, `search_path`.
- [PostgreSQL: Privileges](https://www.postgresql.org/docs/current/ddl-priv.html): `GRANT`,
  `REVOKE`, and `PUBLIC`.
- [PostgreSQL: pg_hba.conf](https://www.postgresql.org/docs/current/auth-pg-hba-conf.html): how
  logins are checked (`trust`, `scram-sha-256`).
- [Apache Kafka Docker image](https://hub.docker.com/r/apache/kafka): KRaft settings and listeners.
- [Keycloak: Health checks](https://www.keycloak.org/observability/health): `/health/ready` on
  the management port.
- [RustFS on GitHub](https://github.com/rustfs/rustfs): settings and the S3 API.

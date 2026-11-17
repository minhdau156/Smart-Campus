# Capability Map: Smart Campus Super-App

> **Status:** APPROVED 2026-11-17. Module boundaries, build order, the proposed resolutions C1–C11,
> and assumptions 1–6 were accepted as written.
> **Sources:** `docs/Smart Campus Super-App — Implementation Plan.pdf` (the "Plan") and
> `docs/Smart Campus Super-App — Architecture.pdf` (the "Arch"), both dated 2026-11-17.
> **Index rule:** This map lists every spec. Each module gets one `SPEC-<module-id>.md` at the repo root.

## Why decompose

The Plan has about 60 user stories across 9 areas, with 13 independently deployable or testable
capabilities, separate schemas, and separate event contracts. A single spec could not be reviewed,
so each module gets its own Specify → Plan → Tasks → Implement cycle, in dependency order.

## Modules

Module ids are the names the Arch already uses for packages, schemas, manifest ids, and API
prefixes (`/api/v1/{id}`). They will not be renamed. Three ids are new (marked ★) because the Arch
needs them but never names them.

| # | Module id | Runs in | Responsibility | Depends on | Stories |
|---|---|---|---|---|---|
| 1 | `platform` ★ | all | Monorepo skeleton, `shared-kernel` (ids, money, time, event envelope, RFC 7807 errors), transactional outbox → Kafka, topic keys and retention, Docker Compose stack (infra + the 4 apps as containers), gateway routing + per-IP rate limiting, OpenAPI docs + generated frontend types, CI (build, unit, Modulith `verify()`, Testcontainers, image scan), OTel/Prometheus/Loki baseline | — | NFR-4 (baseline). NFR-6 deferred to the shipping phase |
| 2 | `identity` | Core + Keycloak | Keycloak realm and roles, OIDC Code + PKCE login, JWT validation at gateway and `@PreAuthorize` in services, user profile, SIS account import, role admin, global logout | platform | AUTH-1,2,3,4,6 |
| 3 | `shell` ★ | Core + gateway + web | Java package `edu.campus.shell` (Arch §9 calls it `modules/`; renamed so id = package). `CampusModule` manifest registry, `GET /api/v1/modules`, per-university toggles, gateway 404 filter for disabled modules, React shell (menu, `React.lazy` routes, vi/en i18n, responsive layout) | identity | AUTH-5, NFR-7, NFR-8 |
| 4 | `course` | Core | Courses, prerequisites, semesters, sections (capacity), section schedules, rooms without clashes, lecturer cancel/reschedule → `SectionChanged`, `ScheduleChanged` | shell | REG-7, REG-9, TT-4, TT-5 |
| 5 | `registration` | Registration svc | Registration windows, waiting room, Redis Lua seat gate, Kafka request queue + worker, enrollments, drop, waitlist promotion, admin override, live dashboard, k6 rush test | course | REG-1..6, REG-8, REG-10..12, NFR-1, NFR-2, NFR-5, NFR-3 (enrollments) |
| 6 | `timetable` | Core | Read model built from enrollment and schedule events: student week view, lecturer schedule, iCal export | course, registration | TT-1, TT-3, TT-6 |
| 7 | `grade` | Core | Grade entry (form and spreadsheet), publish, GPA, review requests, locking, audit trail | registration | GR-1,2,3,5,6,7, NFR-3 (grades) |
| 8 | `notification` | Notification svc | Inbox, per-type channel preferences, WebSocket/STOMP `/ws`, FCM push, SMTP email, retries, device tokens, handlers for user-facing events | identity | NOTI-1,2,3, TT-2, GR-4, ANN-4 |
| 9 | `announcement` | Core | Targeted posts (all/faculty/cohort/section), scheduled publish, relevance feed, read counts | course | ANN-1,2,3,5,6 |
| 10 | `payment` ★ | Core | Payment-gateway checkout and webhook (VNPay/MoMo), idempotency, refunds → `PaymentConfirmed`, `PaymentRefunded` | identity | CLUB-4 (paid), CLUB-7 |
| 11 | `club` | Core | Clubs, membership and club roles, club approval, events with quota and price, QR tickets, door scanning, attendee list | identity, payment | CLUB-1..8 |
| 12 | `marketplace` | Core | Book listings with MinIO photos, edit and mark sold, buyer-seller messages, reports, moderation and bans | course | MKT-1,3,4,5,6 |
| 13 | `lostfound` | Core | Lost/found reports with photos, matching, claims with proof, security-desk handling, 60-day archive job | identity | LF-1..6 |
| 14 | `search` | Core | OpenSearch read model over sections, listings, and lost-found items. It can be dropped and replayed at any time. | course, marketplace, lostfound | MKT-2 (+ cross-module search) |

### Dependency rules

- **Arrows point one way.** "Depends on" means "uses this module's public API or consumes its
  events". The provider's spec owns the contract.
- **Notification is a sink.** It depends only on `identity` and the `platform` envelope. Each later
  publisher's spec includes the notification handler for its own events. This prevents
  `notification` from depending on every module.
- **No cross-schema foreign keys** (ADR-6). A reference to another module's row is a bare UUID, and
  consumers keep a local projection.
- **Cross-module events are always consumed from Kafka**, even between two modules inside
  `core-app` (Arch §3). Event topics keep their full history, so read models (`timetable`,
  `search`) can be dropped and rebuilt by replaying (Arch §4). See C16.
- **`course` does not consume `registration` events** (C17). Without this rule, `course` and
  `registration` would depend on each other.

## Build order

```
platform → identity → shell → course → registration ─┬→ timetable
                                                     ├→ grade
                                                     └→ announcement
             identity ───────────────→ notification    (parallel track, after identity)
             identity → payment → club
             course → marketplace, lostfound → search
```

Recommended sequence (one team):
**platform → identity → shell → course → registration → timetable → grade → notification →
announcement → payment → club → marketplace → lostfound → search**

Differences from the Plan's §6 order:
1. `timetable` moves after `registration` because the Arch builds it from enrollment events.
   Before that, it would have no data to show.
2. `payment` is placed explicitly before `club`.
3. `notification` can start in parallel any time after `identity` if a second person is available.

## Conflicts and gaps found between the two documents

Each item needs a decision before or inside the named module's spec. My proposed resolution is in
*italics*.

| # | Issue | Affects | Proposed resolution |
|---|---|---|---|
| C1 | Plan: `GET /api/modules`. Arch: `GET /api/v1/modules` | shell | *Use `/api/v1/modules`, which matches the Arch API conventions* |
| C2 | Plan: "Kafka (or RabbitMQ)". ADR-4: Kafka | platform | *Kafka only* |
| C3 | Plan names 4 realm roles (STUDENT, LECTURER, ADMIN, CLUB_MANAGER). The stories use 7 personas (faculty staff, academic admin, security office, system admin…) | identity | *Realm roles: STUDENT, LECTURER, FACULTY_STAFF, ACADEMIC_ADMIN, SECURITY_STAFF, SYSTEM_ADMIN, CLUB_MANAGER. Per-club permissions stay in `club_members.role`* |
| C4 | Plan schema has cross-module FKs (`grades.enrollment_id`, `sections.lecturer_id`, …). This breaks ADR-6, and `grades → enrollments` even crosses service boundaries | all | *FKs only within a schema; other references are UUIDs plus local projections* |
| C5 | Plan §5.4 relies on the optimistic `version` on `course.sections` as the final guard. The Registration service cannot write the course schema | course, registration | *Registration owns its own `section_seats` (capacity, enrolled, version), fed by `SectionChanged`. `course.sections` has no `enrolled_count` (see C17)* |
| C6 | Plan lists one `outbox_events` table under Notifications. The Arch requires an outbox write in each module's own transaction, and the table lacks type/aggregate/topic columns | platform | *Revised 2026-11-17: use Spring Modulith's event-publication registry + Kafka externalization (SPEC-platform D1). That gives one publication table per app, owned by `platform`, written in the same transaction as the business change. It is not one table per schema. Events carry the full envelope* |
| C7 | `club` consumes `PaymentConfirmed`, but no module produces it | club | *New `payment` module* |
| C8 | Registration (step 3) pushes results over WebSocket, but the WebSocket host (`notification`) arrives at step 5. STOMP across 20 replicas also needs a broker relay | registration, notification | *Registration ships with a polling status endpoint (202 + `GET …/requests/{id}`). WebSocket push is added when `notification` lands* |
| C9 | `users.role` is a single column, but Keycloak users have several roles | identity | *Roles live only in Keycloak; the profile table does not store them* |
| C10 | Stories without tables: credit limit (REG-3), rooms (TT-5), grade reviews (GR-5/6), announcement reads (ANN-6), notification prefs + device tokens (NOTI-2), marketplace messages and reports (MKT-3/5/6), audit log (NFR-3), club approval (CLUB-8), waiting-room tokens (REG-5) | per module | *Design in each module's spec* |
| C11 | The Arch's open question (LDAP/AD federation or one-off SIS import) is still unanswered | identity | *Assume CSV/SIS import into Keycloak until answered* |

Second review against both PDFs (2026-11-17, all approved):

| # | Issue | Affects | Resolution |
|---|---|---|---|
| C12 | Arch §8: in dev, each app runs as a Compose container, and CI pushes images | platform | *App container images + a Compose `apps` profile. Image push is deferred to the shipping phase* |
| C13 | Arch §7: OpenAPI 3 via springdoc, one group per module, frontend types generated from it | platform | *springdoc in `platform-starter`, one group per Modulith module, committed OpenAPI JSON, `openapi-typescript` in web* |
| C14 | Arch §7 and Plan §2: the gateway rate-limits, returning 429 + Retry-After. Per user for registration, per IP for everything else | platform, registration | *Per-IP limit at the gateway in `platform`. Per-user registration limit in `registration`* |
| C15 | Arch §6 topic keys are not always the `aggregateId` (`registration.events` and `grade.events` are keyed by studentId) | platform | *The key is defined per topic in `shared-kernel` `Topics`* |
| C16 | Arch §4: read models "can be dropped and replayed at any time". Arch §3: all cross-module changes flow through Kafka | platform, timetable, search | *Cross-module consumption only via Kafka (also inside `core-app`). Event topics have unlimited retention. A replay resets the consumer's state and offsets* |
| C17 | Arch §4: `course` consumes `EnrollmentConfirmed` to update `enrolled_count`. Together with `registration` consuming `SectionChanged`, that is a dependency cycle | course, registration | *Deviation from Arch §4: drop `course.sections.enrolled_count`. Live seat counts come from `registration` (REG-1)* |
| C18 | Plan NFR-6 (deploy without downtime) needs rolling deploys on Kubernetes | — | *Deferred to the shipping phase, not claimed by `platform`* |

## Assumptions (correct any that are wrong)

1. **Greenfield.** `C:\Smart Campus` holds only `docs/` and is not yet a git repo. The code will
   follow the Arch §9 monorepo layout at the repo root.
2. **Local-first target.** Docker Compose is the "done" environment for every module. Kubernetes,
   Helm, HPA, image push and zero-downtime deploys (NFR-6) are a later shipping phase. The k6 rush
   test runs against Compose first.
3. **Mobile (Expo) is out of scope.** NFR-7 is met by a responsive web app.
4. **External systems are stubbed.** SIS uses a CSV fixture, the payment gateway uses sandbox or a
   mock webhook, FCM and SMTP use Mailpit plus a fake push sender.
5. **MVP = every "Must" story.** "Should" and "Could" stories are scoped per module spec.
6. **Stack** is fixed by the docs: Java 21, Spring Boot 3, Spring Modulith, Spring Cloud Gateway,
   React + TS + Vite + TanStack Query. The build tool (Gradle or Maven) is chosen in `SPEC-platform.md`.

## Spec index

| Module id | Spec | Status |
|---|---|---|
| `platform` | [SPEC-platform.md](SPEC-platform.md) | Approved; plan approved, implementing ([tasks/todo.md](tasks/todo.md)) |
| all others | — | Not started |

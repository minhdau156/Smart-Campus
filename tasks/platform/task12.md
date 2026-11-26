# T12: Topics, keys, retention, Kafka-only rule, replay

> Module `platform` · Phase 3: Services, errors, events, routing, containers · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T11](task11.md) · [T13 →](task13.md)

`Scope M · Depends on: T10, T11 · Closes: O5, O6`

**Goal:** the real topics exist with the right keys and full history; modules can only talk via
Kafka; read models can be rebuilt.

**Subtasks**
- [x] **12.1** Declare the topics as `NewTopic` beans built from `Topics`, with `retention.ms=-1`:
  - `core-app`: `course.events`, `grade.events`, `campus.events`;
  - `registration-service`: `registration.requests`, `registration.events`.
- [x] **12.2** Externalized events take their key from `Topics` (using the approach recorded in
  7.3), and publishing without a key throws. Add an IT per topic checking the record key.
- [x] **12.3** Add an ArchUnit rule (D5): in `core-app`, an in-process listener may only accept
  event types from its own module. Add a violation fixture and a test that proves it fails.
- [x] **12.4** Add a consumer registry that marks each consumer as replayable or not (the probe consumer is replayable).
- [x] **12.5** Write `infra/replay.sh <group> <tables>`. It:
  - refuses non-replayable consumers;
  - requires the consumer to be stopped;
  - truncates the listed tables and that consumer's `processed_events` rows;
  - resets the group to earliest with `kafka-consumer-groups`;
  - prints "restart the app".
- [x] **12.6** Write `TopicsIT`: describe all 5 topics and assert partitions and `retention.ms=-1`.
- [x] **12.7** Manual replay: emit 10 probes → stop the consumer → `replay.sh` → restart → still exactly 10 rows.

**Done when**
- [x] The 5 topics exist with the right partitions and unlimited retention (O5)
- [x] Every record key matches D6, and a missing key fails publication
- [x] Replay rebuilds the same 10 rows, and the D5 fixture fails the build (O6)

**Verify**
- [x] `./mvnw verify` passes
- [x] Step 12.7 was observed

**Files:** `core-app/…/platform/TopicConfig.java`, `registration-service/…/TopicConfig.java`, `core-app/…/KafkaOnlyCrossModuleRuleTest.java`, `TopicsIT.java`, `infra/replay.sh`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

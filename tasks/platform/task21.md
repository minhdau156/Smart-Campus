# T21: README + ADRs

> Module `platform` · Phase 5: Web scaffold, CI, handover · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T20](task20.md) · (last task) →

`Scope M (docs only) · Depends on: Checkpoint D (can be drafted earlier) · Closes: H1, H2`

**Goal:** a new developer can get from clone to a running platform using only the README, and every big decision is written down.

**Subtasks**
- [ ] **21.1** README: prerequisites (JDK 21, Node 24, Docker Desktop) and the memory each Compose profile needs (from 3.8, 15.6, Checkpoint D).
- [ ] **21.2** README: commands, ports, the two run modes, the replay procedure, and the troubleshooting notes collected during T1–T20. Link to the map and specs.
- [ ] **21.3** Write an ADR template (`docs/adr/template.md`): context, decision, consequences, status.
- [ ] **21.4** ADRs 0001–0008: copy Arch ADR-1…8.
- [ ] **21.5** ADRs 0009–0014 for spec D1–D6, and ADR 0015 for Spring Boot 4 instead of 3.
- [ ] **21.6** Clone the repo into a temp folder, follow only the README, and confirm `smoke.sh` passes.

**Done when**
- [ ] Following only the README reaches the Checkpoint C state (H1)
- [ ] `docs/adr/` contains 15 ADRs in the same template (H2)

**Verify**
- [ ] Step 21.6 was observed

**Files:** `README.md`, `docs/adr/template.md`, `docs/adr/0001-…md` … `docs/adr/0015-…md`

---

## ◆ Checkpoint E: `platform` done
- [ ] Every success criterion in SPEC-platform.md is ticked (C1 may wait for the first push)
- [ ] A fresh-clone P1 run is green
- [ ] Spec status set to DONE, map index updated, `tasks/` archived to `tasks/archive/platform/`
- [ ] 🙋 **Human sign-off**, then start `SPEC-identity.md`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

# T4: `shared-kernel`: ids, money, time, problem codes

> Module `platform` · Phase 1: Foundation · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T3](task3.md) · [T5 →](task5.md)

`Scope M · Depends on: T2 · Closes: —`

**Goal:** the small, framework-free value types that every module reuses (Arch §7: "ids, money, time").

**Subtasks**
- [x] **4.1** Add a guard to `shared-kernel`'s build that fails if any `org.springframework` artifact is on its classpath.
- [x] **4.2** **Ids:** the typed-id pattern (`record StudentId(UUID value)` style, null-checked) plus an `Ids.newId()` helper, with tests.
- [x] **4.3** **Money:** amount (`BigDecimal`) + ISO currency, scale checked against the currency (VND = 0), `plus`/`minus`, scale-insensitive `equals`. Tests cover a null currency, mixed currencies and a bad scale.
- [x] **4.4** **Time:** `TimeRange(start, end)` with `start < end` and `overlaps()`. Tests cover touching (no overlap), nested, disjoint and invalid ranges. Add a `Clocks` helper (fixed and offset clocks) in a test-jar (`maven-jar-plugin` `test-jar` goal) so other modules can use it as a `test-jar` dependency.
- [x] **4.5** **Errors:** a `ProblemCode` enum (`INTERNAL_ERROR`, `VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`), each with an HTTP status, a title and a `type` URI. Add `CampusProblem` (exception carrying a code and a detail), with tests.
- [x] **4.6** Turn on the JaCoCo gate: `shared-kernel` line coverage ≥ 90%, or the build fails.

**Done when**
- [x] The Money rules in 4.3 and the `TimeRange.overlaps` cases in 4.4 are all covered by passing tests
- [x] Each `ProblemCode` gives a status and a kebab-case `type` URI (`https://campus.edu/problems/<code>`)
- [x] `shared-kernel` has no Spring dependency, and the guard from 4.1 enforces it

**Verify**
- [x] `./mvnw -pl backend/shared-kernel -am verify` passes, and the coverage report shows ≥ 90%

**Result (2026-11-21):** 46 unit tests, **100% line coverage** (53/53); gate at 90% seen failing
(85% with an untested class). Spring guard = `maven-enforcer-plugin` `bannedDependencies`
(`org.springframework*`), seen failing with `spring-core`. Choices: `TypedId` interface + `Ids.requireId`
for the null check; `Money` rejects extra decimals instead of rounding and stores the currency
scale, so plain record `equals` is scale-insensitive; currencies with no decimal rule (e.g. XAU)
are rejected; amounts may be negative (refunds). `TimeRange` is half-open `[start, end)`.
`VALIDATION_FAILED` = 400. `Clocks` lives in test code (`edu.campus.kernel.testing`) and is the
only class in the test-jar. `KernelPlaceholderTest` was removed (replaced by real tests).

**Files:** `kernel/id/Ids.java`, `kernel/money/Money.java`, `kernel/time/TimeRange.java`, `kernel/error/ProblemCode.java`, `kernel/error/CampusProblem.java`, `kernel/id/TypedId.java`, tests + `src/test/java/edu/campus/kernel/testing/Clocks.java` (shipped in the test-jar), `backend/shared-kernel/pom.xml`

---

## Definition of Done (applies to every task)

`./mvnw verify` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).

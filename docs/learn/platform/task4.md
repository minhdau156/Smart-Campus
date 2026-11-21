# Task 4: shared-kernel ids, money, time, problem codes - learning note

> Task: [task4.md](../../../tasks/platform/task4.md) · Commit: `f723241` · Date: 2026-11-21

## In one minute

This is the first real Java code in the project. `shared-kernel` now has small **value types**
that every module will reuse: typed ids, `Money`, `TimeRange`, and the error codes `ProblemCode`
and `CampusProblem`. A value type is a small object that is defined only by its values (two
`Money` objects with "1000 VND" are the same money). Each type checks its own rules when it is
created, so an invalid one can never exist. Two build guards protect the module: no Spring library
may get in, and the tests must cover at least 90% of the lines. Right now they cover 100%.

## The files

### `kernel/id/TypedId.java` and `kernel/id/Ids.java`
- **What they do:** `TypedId` is the shape every id follows (it has a `UUID value()`). `Ids` has
  two helpers: `newId()` makes a new random UUID, and `requireId()` is the null check.
- **Why they exist:** with plain `UUID`s, nothing stops you from passing a course id where a
  student id is expected. Both are just `UUID`, so the compiler cannot help. With one small type
  per id, the mistake does not compile.
- **The pattern each module will write:**
  ```java
  public record StudentId(UUID value) implements TypedId {
      public StudentId {
          Ids.requireId(value, "StudentId");   // a null id is never allowed
      }
  }
  ```
  - A **record** is a short Java class for data. Java writes the constructor, `value()`,
    `equals`, `hashCode` and `toString` for you.
  - `public StudentId { ... }` is a **compact constructor**: code that runs before the fields are
    set. This is where records check their rules.
- **Why `shared-kernel` has no `StudentId` itself:** ids belong to their module (`StudentId` in
  `identity`). The kernel only gives the pattern. The test has a `SampleId` that shows it.

### `kernel/money/Money.java`
- **What it does:** an amount plus a currency, e.g. `1500000 VND` or `10.50 USD`, with `plus` and `minus`.
- **Why it exists:** money with `double` gives rounding errors (`0.1 + 0.2 = 0.30000000000000004`),
  and an amount with no currency is a bug waiting to happen. `Money` uses `BigDecimal` (exact
  decimal numbers) and always carries its currency.
- **Key lines:**
  ```java
  public record Money(BigDecimal amount, Currency currency) {
      public Money {
          int decimals = currency.getDefaultFractionDigits();      // VND → 0, USD → 2
          amount = amount.setScale(decimals, RoundingMode.UNNECESSARY);
      }
  ```
  - **Scale** = the number of digits after the decimal point. `getDefaultFractionDigits()` gives
    the ISO rule for each currency: VND has 0, USD has 2.
  - `setScale(..., RoundingMode.UNNECESSARY)` means "change the scale, but **never round**". So
    `1000.00 VND` becomes `1000 VND` (only zeros are removed). `1000.5 VND` throws an error, and we
    turn it into `IllegalArgumentException`: half a đồng does not exist.
  - **Scale-insensitive `equals`:** in `BigDecimal`, `10.5` and `10.50` are **not** `equals`.
    Because `Money` always stores the currency's scale, both become `10.50`, and the record's
    normal `equals` works.
  - `plus` and `minus` refuse mixed currencies: adding USD to VND needs an exchange rate, which is
    a business decision, not math.
  - Gold (`XAU`) is an ISO code with no decimal rule (`-1`), so it is rejected as money.

### `kernel/time/TimeRange.java`
- **What it does:** a time span from `start` to `end`, and `overlaps(other)`.
- **Why it exists:** timetables and registration need "do these two class slots clash?" many
  times. One correct version in the kernel avoids many slightly different ones.
- **Key lines:**
  ```java
  if (!start.isBefore(end)) throw new IllegalArgumentException(...);   // start must be before end
  return start.isBefore(other.end) && other.start.isBefore(end);        // overlap test
  ```
  - The range is **half-open**: `[start, end)`. The start is inside, the end is not. So 08:00–10:00
    and 10:00–12:00 **touch but do not overlap**. That is right for back-to-back classes.
  - The overlap rule: two ranges overlap when each one starts before the other one ends. This one
    line covers partial, nested and identical ranges.

### `kernel/error/ProblemCode.java` and `kernel/error/CampusProblem.java`
- **What they do:** `ProblemCode` is the list of error kinds. Each has an HTTP status, a title and
  a `type` URI. `CampusProblem` is the exception you throw with a code and a message.
- **Why they exist:** every API error must have the same shape (RFC 7807, the standard JSON format
  for HTTP errors), with a **stable code** that the website can switch on. Text messages change;
  codes don't.
- **The five codes:**

  | Code | Status | Title | `type` |
  |---|---|---|---|
  | `INTERNAL_ERROR` | 500 | Internal error | `https://campus.edu/problems/internal-error` |
  | `VALIDATION_FAILED` | 400 | Validation failed | `…/validation-failed` |
  | `NOT_FOUND` | 404 | Not found | `…/not-found` |
  | `CONFLICT` | 409 | Conflict | `…/conflict` |
  | `RATE_LIMITED` | 429 | Rate limited | `…/rate-limited` |

  The `type` is made from the name: lower case, `_` → `-` (**kebab-case**). `Locale.ROOT` makes
  the lower-casing the same on every computer. Some languages (Turkish) lower-case `I` differently.
- **Using it:**
  ```java
  throw new CampusProblem(ProblemCode.NOT_FOUND, "Course %s does not exist".formatted(id));
  ```
  It is a `RuntimeException` (unchecked), so you can throw it anywhere without `throws`. In Task 8,
  the web layer turns it into the JSON error response.

### `src/test/java/edu/campus/kernel/testing/Clocks.java` (shared in the **test-jar**)
- **What it does:** gives test clocks: `Clocks.fixedAt("2026-09-07T08:00:00Z")` is always at that
  moment, and `Clocks.offset(base, Duration.ofHours(2))` is always 2 hours ahead.
- **Why it exists:** the spec says production code takes a `java.time.Clock` and never calls
  `Instant.now()`. Then a test can pass a fixed clock and get the same time on every run. Tests
  that depend on the real time pass today and fail tomorrow.
- **Why in test code, shared by a test-jar:** `Clocks` is only for tests, so it must not be in the
  real jar. A **test-jar** is a second jar made from test classes. Other modules add it with
  `<type>test-jar</type>` and `<scope>test</scope>`. We put **only** the `edu.campus.kernel.testing`
  package in it (we checked: it holds just `Clocks.class`).

### The tests (`IdsTest`, `MoneyTest`, `TimeRangeTest`, `ProblemCodeTest`, `CampusProblemTest`, `ClocksTest`)
- **46 tests.** Each has a name that reads like a rule, for example `vndWithDecimalsIsRejected`,
  `touchingRangesDoNotOverlap`, `plusWithMixedCurrenciesIsRejected`.
- `ProblemCodeTest` uses **parameterized tests**: `@CsvSource` runs one test method once per row
  (5 codes = 5 runs), and `@EnumSource` runs it once per enum value. This way no code is missed.
- `KernelPlaceholderTest` was deleted: it only existed until real tests arrived.

### `backend/shared-kernel/pom.xml` (three new plugins)
1. **The Spring guard** (`maven-enforcer-plugin`, rule `bannedDependencies`):
   ```xml
   <exclude>org.springframework*</exclude>
   ```
   Fails the build if any library whose group starts with `org.springframework` is on the
   classpath, even one pulled in by another library ("transitive").
2. **The test-jar** (`maven-jar-plugin`, goal `test-jar`, `<include>edu/campus/kernel/testing/**</include>`).
3. **The coverage gate** (`jacoco-maven-plugin`, goal `check`): `LINE` / `COVEREDRATIO` /
   `minimum 0.90`. It adds a `check` step to the JaCoCo setup inherited from the root pom.

## Decisions and why

| Decision | Options | Why we chose it |
|---|---|---|
| `Money` **rejects** extra decimals | round them | Rounding money without being asked hides bugs. The caller must decide how to round. |
| Store the currency's scale | keep the scale as given, write our own `equals` | The record's normal `equals` then works, and `10.5 USD` prints as `10.50 USD`. |
| Negative money allowed | only zero or more | Refunds and `minus` need it. A module can add its own "no negative" rule. |
| `TimeRange` is `[start, end)` | both ends inside | Back-to-back classes must not count as a clash. |
| `VALIDATION_FAILED` → **400** | 422 | 400 is the most common choice, and the spec does not require 422. |
| `UUID.randomUUID()` (version 4) | time-ordered UUID v7 | Java 21 has v4 built in. v7 can come later if database index speed needs it. |
| `TypedId` interface | only records, no interface | Gives a name to the pattern, and lets later code read `value()` from any id. |
| `Clocks` in a **test-jar** | in the main code | Test helpers must not ship in the real jar. |
| Value errors throw `IllegalArgumentException` / `NullPointerException` | throw `CampusProblem` | Value types stay plain Java. Task 8 decides how they map to HTTP. |

## Problems we hit

1. **One failing test was the test's fault.** `zeroHasTheCurrencyScale` used AssertJ's
   `hasFieldOrPropertyWithValue("scale", 2)`. AssertJ looks for a field or a `getScale()` method,
   but `BigDecimal` only has `scale()`. Fix: `assertThat(zero.amount().scale()).isEqualTo(2)`. The
   `Money` code was right. Lesson: when a test fails, first check whether the **test** is wrong.
2. **Spotless reformatted my test files** (it joined some lines). This is expected:
   `./mvnw spotless:apply` makes the code match the format, so `spotless:check` passes.

## How we checked it works

| Check | What we did | Result |
|---|---|---|
| Guard: RED | added `spring-core` before the guard existed | build **passed** (nothing stopped it) |
| Guard: GREEN | same, with the guard | **BUILD FAILURE**: `spring-core ... banned`. Removed → passes |
| Value types: RED | wrote the tests before the classes | compile errors: classes not found |
| Value types: GREEN | wrote the classes | 43/43 pass (after fixing the one test bug) |
| `Clocks`: RED → GREEN | test first, then `Clocks` | 46/46 pass |
| Test-jar content | `unzip -l ...-tests.jar` | only `edu/campus/kernel/testing/Clocks.class` |
| Coverage | `jacoco.csv` | **100%** (53/53 lines) |
| Gate works | added an untested class | **BUILD FAILURE**: "lines covered ratio is 0.85, but expected minimum is 0.90". Removed → passes |
| No `Instant.now()` | `grep` in main code | none |
| Full build | `./mvnw clean verify` in Git Bash and PowerShell | `BUILD SUCCESS` in both |

## Words to know

| Word | Meaning in simple words |
|---|---|
| Value type / value object | A small object defined only by its values; two with the same values are equal |
| Record | A short Java class for data; Java writes the constructor, getters, `equals`, `hashCode`, `toString` |
| Compact constructor | The `Name { ... }` block in a record, where you check the values |
| Typed id | A small type around a UUID, so different kinds of ids cannot be mixed up |
| `BigDecimal` | Exact decimal numbers, safe for money (unlike `double`) |
| Scale | Number of digits after the decimal point |
| ISO 4217 | The standard list of currency codes (VND, USD) and their decimal rules |
| Half-open range | `[start, end)`: the start is inside, the end is not |
| RFC 7807 | The standard JSON format for HTTP error responses ("problem details") |
| Kebab-case | Words in lower case joined by `-`, like `not-found` |
| Unchecked exception | An exception (a `RuntimeException`) you can throw without declaring `throws` |
| Parameterized test | One test method that runs once per row of data |
| Test-jar | A jar made from test classes, so other modules can reuse test helpers |
| Enforcer | A Maven plugin that fails the build when a rule is broken |
| Coverage gate | A rule that fails the build if tests cover too little of the code |

## Test yourself

1. Why does `Money.of("1000.5", "VND")` fail instead of becoming `1001 VND`?
2. In plain `BigDecimal`, `10.5` is not `equals` to `10.50`. Why is it equal in `Money`?
3. Do the ranges 08:00–10:00 and 10:00–12:00 overlap? Why is that the right answer?
4. Why is `Clocks` in test code and shipped in a test-jar, not in the main jar?
5. How do you know the coverage gate really works?

<details>
<summary>Answers</summary>

1. VND has 0 decimal places, and `Money` never rounds on its own (`RoundingMode.UNNECESSARY`).
   Rounding money silently hides mistakes; the caller must choose how to round.
2. The compact constructor sets every amount to the currency's scale (USD → 2), so both become
   `10.50`, and the record's `equals` compares equal values.
3. No. `TimeRange` is half-open: the end (10:00) is not inside the first range. A class that ends
   at 10:00 and one that starts at 10:00 do not clash.
4. It is only for tests. Main code must use a real `Clock`. The test-jar shares it with other
   modules' tests without putting it in the production jar.
5. We saw it fail: with an untested class, coverage fell to 85% and the build stopped with
   "expected minimum is 0.90". Then it passed again after we removed the class.

</details>

## Learn more

- [Java records (JEP 395)](https://openjdk.org/jeps/395): records and compact constructors.
- [BigDecimal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html):
  `setScale`, `RoundingMode`, and why `equals` looks at the scale.
- [Currency API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Currency.html):
  `getDefaultFractionDigits()` (ISO 4217).
- [java.time.Clock API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Clock.html):
  `Clock.fixed` and `Clock.offset`.
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457): the error
  format (the newer version of RFC 7807).
- [JUnit 5: parameterized tests](https://docs.junit.org/current/user-guide/#writing-tests-parameterized-tests):
  `@CsvSource` and `@EnumSource`.
- [Maven Enforcer: bannedDependencies](https://maven.apache.org/enforcer/enforcer-rules/bannedDependencies.html):
  the Spring guard.
- [Maven JAR plugin: test-jar](https://maven.apache.org/plugins/maven-jar-plugin/examples/create-test-jar.html):
  sharing test classes.
- [JaCoCo check goal](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html): coverage rules.

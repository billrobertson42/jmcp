# CLAUDE.md — Testing Standards for jmcp

This file defines what a *good* test is in this repository and how to tell a good
test from **testing theater** (tests that look like diligence but verify nothing).
It applies to every module (`jmcp-core`, `jmcp-jdbc`, `jmcp-server`, `jmcp-client`,
`jmcp-transport-stdio`) and to both humans and AI assistants writing or reviewing
tests here.

Stack: **JUnit 5** (Jupiter), **Mockito**, **Jackson 3** (`tools.jackson.*`),
**H2** in-memory DB for JDBC tests. Tests live under `<module>/src/test/java/test/...`.

**Sections 0–3 apply every time you write or touch a test** — a single new test
for a bug fix, a small feature, anything. **Section 4 is a different, occasional
mode**: a structured pass over an *existing* suite (an audit, a cleanup task, "go
find weak tests"). Don't reach for §4's machinery while writing one test; do reach
for it when asked to review or rework a suite.

---

## 0. The Prime Directive

> **For every test, you must be able to name a concrete change to production code
> that would make it fail. If you cannot, the test is worthless — do not write it,
> and delete it if it exists.**

This is the single rule that subsumes all the others. It is "mutation testing"
stated in English: a test earns its place only if it would fail on some
plausible bug in the code under test. Before committing a test, finish this
sentence:

> "This test would fail if someone changed `___` in the production code to `___`."

If the blank can only be filled with "if the JDK broke" or "if the constructor
returned null" (constructors never return null), the test is theater.

---

## 1. The Rubric

Apply these seven checks to every test you write. They are ordered by importance.

### 1.1 Does it assert the thing it claims?
- Assert **values**, not mere presence. `assertNotNull(x)` and `json.has("field")`
  are weak; they pass even when the value is wrong.
- The test **name** must match what is asserted. A test called `testRejectDelete`
  must actually assert that a DELETE is rejected.
- No unreachable assertions (nothing after a `return`/`throw`), no swallowed
  exceptions (`try { ... } catch (Exception e) {}` around the thing under test).

```java
// THEATER — passes even if the tool returns the wrong rows
Object result = tool.execute(input, ctx);
assertNotNull(result);

// GOOD — pins the actual contract
CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
        tool.execute(input, ctx));
assertEquals(10, result.count());
assertEquals(4, result.columns().size());
```

### 1.2 Would it fail if the code were wrong? (mutation thinking)
- Name the concrete change that would break it (see §0). If you can't, cut the test.
- Beware tests that **pass for the wrong reason** — most commonly by relying on a
  Mockito mock's *default* return value instead of an explicit stub.

```java
// TRAP: isSchemaVisible returns primitive boolean, so an unstubbed mock returns
// false and silently hides everything — the test may pass or fail for reasons
// unrelated to the code under test.
when(ctx.isSchemaVisible(anyString())).thenReturn(true);   // stub it explicitly
```

### 1.3 Negative, boundary, and error coverage — but only where the code branches
- Cover the unhappy paths: null/empty/malformed input, unknown ids, values at
  `0`, `1`, `MAX`, `MAX+1`, and error responses (assert the *specific* exception
  type / JSON-RPC error code / message, not just "something threw").
- **Boundary tests are only valuable where the implementation actually branches on
  the boundary.** Testing empty/single-element input against a one-line
  `stream().sorted().toList()` is theater — that pipeline is structurally
  incapable of mishandling those inputs. Testing empty/single against a
  hand-written loop with `list.get(0)` / `size - 1` index math is valuable,
  because that code *can* get the boundary wrong.

```java
// For a validator, assert WHY it rejected, not just that it threw:
IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> ReadOnlySqlValidator.validateReadOnly(sql));
assertTrue(ex.getMessage().contains("DELETE"));
```

### 1.4 Determinism & isolation
- No dependence on map/set iteration order, wall-clock time, or test execution
  order. If output order matters, assert it explicitly; if it doesn't, don't
  assume it.
- Each test runs independently. Clean up fixtures in `@AfterEach` (close H2
  connections, `connectionManager.closeAll()`). Use `@TempDir` for filesystem work.

### 1.5 Mock hygiene
- Don't mock value objects you can just construct. Mock collaborators, not data.
- Prefer a hand-written fake over a mock for small, in-repo, side-effect-free
  interfaces (`Tool`, `Resource`, `ResourceProvider`, `McpProtocolHandler` — a
  handful of methods, no I/O). A real implementation is no more code than the
  mock's stubbing, can't silently return a wrong default for a call you forgot
  to stub, and can express "record what I was called with" or "always throw"
  directly as fields instead of `verify()`/`ArgumentCaptor`. See
  `RecordingTool`/`ThrowingTool` in `ToolsHandlerTest`, `FakeResource` in
  `ResourceProxyToolTest`, `FakeProtocolHandler` in `McpServerTest`. Reserve
  Mockito for collaborators with real complexity/side effects, e.g.
  `ConnectionContext`/`ConnectionManager` (a live HikariCP pool behind
  driver-classloader reflection) — hand-faking those would mean reimplementing
  the thing you're trying to avoid depending on.
- Stub only what the test relies on. Don't over-`verify()` interaction counts when
  the observable output is what matters — it makes tests brittle to harmless
  refactors.

### 1.6 Clarity
- Arrange–Act–Assert, one logical behavior per test.
- Give assertions a failure message that localizes the problem
  (`assertEquals(exp, act, "exposed URL must be sanitized")`), especially for bare
  booleans where the default message is just "expected true but was false".
- No logic (loops/conditionals) in tests unless it is itself trivial and obvious.

### 1.7 Suite-level signal
- No duplicate tests asserting the same path.
- No `@Disabled` without a reason string pointing at a tracked issue.
- A high test count is not coverage. 183 tests that only check "something threw"
  can still miss the bug that matters.

---

## 2. Testing Theater — Patterns to Never Write

"Testing theater" = a test that increases the green count and the apparent
coverage without the ability to catch a real defect. Don't write these when
adding a new test; if you spot one while touching a file for any reason, fix or
remove it on sight rather than adding to it.

### 2.1 The zero-assertion test
Calls the code (or doesn't even do that) and asserts nothing. Worst when the name
implies coverage that does not exist.

```java
// THEATER — never calls the validator; cannot fail; falsely implies "\c" is handled
@Test
void testRejectClientCommand() {
    String sql = "\\c other_database";
    // Client commands are handled at client level
    // Included for reference
}
```
A test that must not throw can be a legitimate *smoke test* — but only if it
exercises a real path end to end (e.g. register connection → open → DDL → query).
"Does not throw" over a trivial call is theater.

### 2.2 The structurally-cannot-fail boundary test
Has real assertions, but on an input the operation cannot mishandle. See §1.3.

```java
// THEATER — stream().sorted().toList() cannot mishandle 0 or 1 element
@Test void testSortToolsEmptyList()     { assertTrue(service.sortTools(List.of()).isEmpty()); }
@Test void testSortToolsSingleElement() { assertEquals(1, service.sortTools(List.of(t)).size()); }
```
Note: these have `assertTrue`/`assertEquals`, so a grep for weak assertions will
**not** find them. Recognizing this class requires knowing the implementation —
which is why mutation testing (§4.2) is the only rigorous detector when auditing
a whole suite for it.

### 2.3 Asserting the JDK / a constructor / a library
- `assertNotNull(new Foo(...))` — constructors never return null.
- `assertNotNull(manager)` right after `manager = new Manager()` in `@BeforeEach`.
- A whole test that exercises a **third-party library's** behavior rather than our
  code (it can only fail if the library changes). If you didn't write the code
  under test, the test belongs to whoever did.

```java
// THEATER — @BeforeEach already constructed it; a null here is impossible
@Test void testManagerInitialization() { assertNotNull(driverManager); }
```

### 2.4 Presence-only assertions on real output
`assertNotNull(result)` / `json.has("rows")` when you could assert the value.
This is the most common form. The call being made is worth keeping — just make
the assertion real instead of adding a second, weaker test alongside it.

### 2.5 Passing for the wrong reason
Green because of a mock default, an over-broad `assertThrows(Exception.class)` that
catches a parse error instead of the intended rejection, or an `||` assertion that
succeeds if *either* half is present. Tighten to the specific expectation.

```java
// WEAK — passes if only one of the two is logged
assertTrue(log.contains("error") || log.contains("Invalid Request"));
// GOOD — both must be present
assertTrue(log.contains("Invalid Request"));
assertTrue(log.contains("-32600"));
```

### 2.6 Security theater (a special, dangerous case of the above)
For security-critical code — the SQL read-only firewall (`ReadOnlySqlValidator`)
and credential handling (`JdbcUrlSanitizer`) — theater is not just wasteful, it is
**actively harmful**: it manufactures false confidence in a control that protects
the user. Extra rules here, which apply to any security test you write:

- A "rejects X" test **must** assert rejection (`assertThrows` + ideally the
  reason). A rejection test that asserts nothing (§2.1) is the worst defect in the
  suite because the security count looks strong while a bypass sails through.
- A "masks the secret" test must assert the secret is **absent** *and* the safe
  parts remain — not merely that output is non-null.
  ```java
  assertFalse(sanitized.contains("topsecret"), "password must be masked");
  assertTrue(sanitized.contains("localhost:5432/mydb"), "host/db must survive");
  ```
- When adding coverage for a security control, cover the **bypass classes**, not
  just the obvious case: comment-hidden DML, stacked statements, CTE-wrapped
  writes, keywords inside string literals, dialect-specific write syntax, and —
  critically — **false positives** (valid read-only SELECTs that must NOT be
  rejected). Over-rejection is a real bug too, just a fail-safe one.
- If a correct security test currently fails because the code has a real hole,
  **do not weaken the test to make it pass and do not fix production code silently.**
  Keep the test asserting the correct behavior, mark it `@Disabled("BUG: … — see …")`,
  and log the defect (see §3).

---

## 3. When a New (Correct) Test Fails Because the Code Is Wrong

If writing the *correct* assertion makes a test fail because production code has a
genuine bug:

1. **Do not** change production code as a side effect of a test task, and **do not**
   weaken the test to make it pass.
2. Keep the test asserting the correct behavior; annotate it
   `@Disabled("BUG: <one line> — see <log>")`.
3. Record the defect (file:line, failing input, expected vs actual, why it's a code
   bug not a test bug) so the suite stays green while the reproduction is preserved.

This keeps the build green *and* honest: the bug is documented and re-runnable, not
papered over. This applies whether you're adding one test or auditing a whole file.

---

## 4. Auditing or Reworking an Existing Test Suite

Everything above is what makes a test good the moment it's written. This section
is a different task: you (or an AI assistant) have been asked to review, clean up,
or rework tests that already exist — e.g. "find and remove testing theater in this
module," "audit this suite for quality." Don't run this machinery as a reflex on
every small test change; use it when that's the actual task.

### 4.1 Delete vs. Rewrite vs. Keep

When you find a weak test, choose deliberately:

| Situation | Action |
|-----------|--------|
| Cannot name a concrete change it would catch (§0); structurally cannot fail; zero-assertion; asserts the JDK/constructor | **Delete** |
| Makes a worthwhile call but asserts only presence (§2.4) | **Rewrite** to assert values |
| Weak but *can* fail on a real, plausible bug (e.g. `assertNotNull(getContext(id))` after register — a no-op registration would fail it) | **Keep** (optionally strengthen). Do not delete working regression protection just because it is modest. |
| Two tests exercise the identical path | **Consolidate** |

When in doubt between delete and keep, ask again: *what would make it fail?* If
the answer is a real one, keep it.

### 4.2 The Rigorous Backstop: Mutation Testing

Grep and eyeballing catch the obvious theater (zero-assertion, `assertNotNull`-only,
construct-then-assert). They **cannot** catch the structurally-cannot-fail class
(§2.2), because those tests have real assertions. The only reliable detector is
**mutation testing**: it mutates production bytecode (flip `<`→`<=`, negate
conditionals, make methods return null/empty) and reports which of those changes
your tests fail to catch. A test that catches none of them is theater, proven
empirically.

For Java, use **PITest** (`pitest-maven`). It is slow and noisy over a whole suite,
so scope each run to whatever code is under review in that task — for a security
audit that means the validators/sanitizers; for a different module, scope it there
instead. Treat a surviving mutation as a coverage gap to be closed with a real test.

---

## 5. Quick Checklist

### Every test you write
- [ ] I can name a concrete production mutation this test would catch.
- [ ] Assertions check **values**, not just non-null / key presence.
- [ ] The test name matches what is actually asserted.
- [ ] Error paths assert the specific exception type / error code / message.
- [ ] Boundary cases target inputs the code actually branches on.
- [ ] Mocks are stubbed explicitly; no reliance on default returns.
- [ ] No order/time/execution-order dependence; fixtures cleaned up.
- [ ] If this is a security test, it asserts rejection *reasons* and, where relevant, covers bypass + false-positive classes.
- [ ] A failing-but-correct test is `@Disabled` with a logged bug, not weakened.

### Additional, when auditing an existing suite (§4)
- [ ] Every test has been triaged as Delete / Rewrite / Keep / Consolidate — not just skimmed.
- [ ] No zero-assertion tests, no `assertNotNull(new X())`, no library-only tests remain.
- [ ] For anything security-critical, consider a scoped PITest run rather than relying on eyeballing alone.

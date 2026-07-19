# Code issues surfaced by the test-quality review

Six production bugs were found while strengthening the unit tests. Per the
review protocol, **no production code was changed.** Each bug has a
corresponding test that asserts the *correct* (desired) behavior and is marked
`@Disabled` with a pointer here, so the reproduction is preserved and the suite
stays green. Remove the `@Disabled` once the code is fixed.

All six were verified by hand against the production source (regex/heuristic
analysis), not just taken from the agents' claims.

Severity legend: **HIGH** = unsafe direction (secret disclosure);
**LOW** = fail-safe direction (over-rejects legitimate read-only queries — a
usability issue for a security validator, not a security hole).

---

## 1. HIGH — JDBC URL sanitizer leaks passwords in the `user:password@host` userinfo form

- **Production:** `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/JdbcUrlSanitizer.java:39` (`ORACLE_PASSWORD_PATTERN`) and `:30` (`SENSITIVE_PARAM_PATTERN`).
- **Repro:** `sanitizeUrl("jdbc:postgresql://admin:secretpw@localhost:5432/mydb")`
- **Expected:** `secretpw` masked. **Actual:** returned verbatim — password leaks.
- **Why:** `ORACLE_PASSWORD_PATTERN` `(//)([^/@]+)/([^@]+)(@)` only matches the slash form `user/pass@`, not the standard URI colon form `user:pass@`. `SENSITIVE_PARAM_PATTERN` requires a `?`/`&`/`;` delimiter, which userinfo has not. So no rule fires.
- **Reproducing test:** `JdbcUrlSanitizerTest.testSanitizeUrlUserInfoColonFormMasksPassword` (`@Disabled`).
- **Fix sketch:** add a userinfo pattern, e.g. `://([^/:@]+):([^@]+)@` → `://$1:****@`.
- **RESOLVED (2026-07-19):** rather than fix the masking regex, the `exposeUrls`
  feature and `JdbcUrlSanitizer` were removed entirely — the JDBC URL is never
  shown to MCP clients under any configuration (always a fixed `"****"`). The
  class this bug lived in, and its reproducing test, no longer exist. See branch
  `claude/remove_url_exposure`.

## 2. HIGH — JDBC URL sanitizer leaks passwords in the canonical Oracle EZConnect form

- **Production:** `JdbcUrlSanitizer.java:39` (`ORACLE_PASSWORD_PATTERN`).
- **Repro:** `sanitizeUrl("jdbc:oracle:thin:scott/tigerpw@//myhost:1521/orcl")`
- **Expected:** `tigerpw` masked. **Actual:** returned verbatim — password leaks.
- **Why:** the pattern anchors on `//` appearing **before** `user/pass@`. In the canonical Oracle thin/EZConnect form the `//` appears **after** the `@` (`@//myhost`), so the pattern never matches. (The already-passing `//scott/tigerpw@myhost` form works precisely because its `//` precedes the credentials.)
- **Reproducing test:** `JdbcUrlSanitizerTest.testSanitizeUrlOracleEzConnectFormMasksPassword` (`@Disabled`).
- **Fix sketch:** match `:user/pass@` independent of a leading `//`, e.g. anchor on `:` or `@` rather than requiring `//` first.
- **RESOLVED (2026-07-19):** same resolution as #1 — `JdbcUrlSanitizer` (and the
  `exposeUrls` configuration option that was its only production caller) was
  deleted rather than patched, so this bug's code path no longer exists. See
  branch `claude/remove_url_exposure`.

## 3. LOW — Validator rejects read-only functions whose name merely contains `PROC`

- **Production:** `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/validation/ReadOnlySqlValidator.java:372`.
- **Repro:** `validateReadOnly("SELECT reprocess(id) FROM jobs")` → thrown/rejected.
- **Why:** the procedure heuristic `.*\bSELECT\b.*\w*[_]?PROC\w*\s*\(.*` matches any identifier containing the substring `PROC` (`reprocess(`, `preprocess(`, …).
- **Reproducing test:** `ValidSelectQueriesTest.testFunctionNameContainingProcAllowed` (`@Disabled`).
- **Fix sketch:** require a word boundary / anchored procedure name rather than an embedded `PROC` substring.

## 4. LOW — Validator rejects a column literally named `nextval` when alias-qualified

- **Production:** `ReadOnlySqlValidator.java:352`.
- **Repro:** `validateReadOnly("SELECT t.nextval FROM mytable t")` → rejected.
- **Why:** `.*\w+\.NEXTVAL\b.*` matches the plain column reference `t.nextval`, not just Oracle `sequence.NEXTVAL`.
- **Reproducing test:** `ValidSelectQueriesTest.testColumnNamedNextvalAllowed` (`@Disabled`).

## 5. LOW — Keyword heuristics fire on tokens inside string literals (`NEXT VALUE FOR`)

- **Production:** `ReadOnlySqlValidator.java:358`.
- **Repro:** `validateReadOnly("SELECT 'NEXT VALUE FOR promo' AS label FROM campaigns")` → rejected.
- **Why:** a bare `normalized.contains("NEXT VALUE FOR")` with no string-literal guard. `normalized` (line 263) only collapses whitespace + uppercases; the `INTO` check has a literal guard (lines 305–313) but this one does not.
- **Reproducing test:** `ValidSelectQueriesTest.testNextValueForInsideStringLiteralAllowed` (`@Disabled`).

## 6. LOW — Same string-literal blind spot for `USE INDEX` / `LAST_INSERT_ID(` / `proc(`

- **Production:** `ReadOnlySqlValidator.java:340`, `:346`, `:372`.
- **Repro:** `validateReadOnly("SELECT description FROM notes WHERE description = 'run proc() and USE INDEX'")` → rejected.
- **Why:** these checks also scan the raw uppercased text with no string-literal guard, so write-related keywords appearing inside a string literal trigger rejection.
- **Reproducing test:** `ValidSelectQueriesTest.testWriteKeywordFunctionsInsideStringLiteralAllowed` (`@Disabled`).
- **Note (5 & 6):** the clean fix is to strip/blank string literals from `normalized` once, up front, and run all keyword checks against that — which would also harden checks that currently have ad-hoc guards.

---

## Behavioral notes (not filed as bugs)

`ResourcesHandler.handleReadResource` (`jmcp-core/.../protocol/ResourcesHandler.java:105`)
reports a `resources/read` request with **no `uri` param** as a generic internal
error (`-32603`, "Resource read failed") because deserializing the params fails
before the null-URI guard is reached. A missing required param would be more
precisely reported as invalid-params (`-32602`). It still rejects the request
(no NPE, no false success); `ResourcesHandlerTest.testHandleReadResourceMissingUri`
pins the current `-32603` behavior so any hardening surfaces as a deliberate change.

`ResourceProxyTool.execute` (`jmcp-server/.../tools/ResourceProxyTool.java:81`)
calls `params.get("operation").asString()` with no null check, so a call
missing the schema-required `operation` field throws a raw `NullPointerException`
rather than a descriptive `IllegalArgumentException`. The input schema marks
`operation` as required so the framework should reject it earlier; this is a
defensiveness/UX smell, not a correctness bug. `ResourceProxyToolTest.testMissingOperation`
now pins the current NPE behavior so any hardening surfaces as a deliberate test change.

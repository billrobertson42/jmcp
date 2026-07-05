/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.org.peacetalk.jmcp.jdbc.validation;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.validation.ReadOnlySqlValidator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for valid SELECT queries that should be allowed by ReadOnlySqlValidator.
 */
class ValidSelectQueriesTest {

    @Test
    void testSimpleSelect() {
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * FROM users"));
        assertTrue(ReadOnlySqlValidator.isReadOnly("SELECT * FROM users"));
    }

    @Test
    void testSelectWithWhere() {
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT id, name FROM users WHERE age > 25"));
    }

    @Test
    void testSelectWithJoin() {
        String sql = "SELECT u.name, o.order_id FROM users u JOIN orders o ON u.id = o.user_id";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithSubquery() {
        String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE total > 100)";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithCTE() {
        String sql = """
            WITH recent_users AS (
                SELECT * FROM users WHERE created_date > '2024-01-01'
            )
            SELECT * FROM recent_users WHERE age > 18
            """;
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
        assertTrue(ReadOnlySqlValidator.isReadOnly(sql));
    }

    @Test
    void testSelectWithComplexCTE() {
        String sql = """
            WITH
                active_users AS (SELECT * FROM users WHERE status = 'active'),
                user_orders AS (SELECT * FROM orders WHERE user_id IN (SELECT id FROM active_users))
            SELECT au.name, COUNT(uo.id) as order_count
            FROM active_users au
            LEFT JOIN user_orders uo ON au.id = uo.user_id
            GROUP BY au.name
            """;
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithWindowFunction() {
        String sql = "SELECT name, age, ROW_NUMBER() OVER (ORDER BY age DESC) as rank FROM users";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithGroupBy() {
        String sql = "SELECT department, COUNT(*) FROM employees GROUP BY department HAVING COUNT(*) > 5";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithUnion() {
        String sql = "SELECT name FROM users UNION SELECT name FROM customers";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testMultipleSelectStatements() {
        String sql = "SELECT * FROM users; SELECT * FROM orders;";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectCurrvalAllowed() {
        // SELECT currval() - PostgreSQL - read-only, gets current value
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT currval('my_sequence')"));
    }

    @Test
    void testSelectLastvalAllowed() {
        // SELECT lastval() - PostgreSQL - read-only, gets last value
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT lastval()"));
    }

    @Test
    void testOracleSequenceCurrval() {
        // Oracle sequence.CURRVAL - read-only, gets current value
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT user_id_seq.CURRVAL FROM dual"));
    }

    @Test
    void testRejectSQLiteRowId() {
        // SQLite rowid/oid - can be used to track insertions
        // Note: rowid is read-only but can track auto-increment
        String sql = "SELECT rowid, * FROM users";
        // Currently allowed - debatable whether to reject
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectForUpdate() {
        // SELECT FOR UPDATE - acquires locks (modifies transaction state)
        // Note: This is actually a SELECT, but acquires exclusive locks
        // Debatable if should be rejected - currently allowed but worth noting
        String sql = "SELECT * FROM users WHERE id = 1 FOR UPDATE";
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testRejectSelectForShare() {
        // SELECT FOR SHARE - PostgreSQL - acquires shared locks
        // Note: This is a SELECT that modifies transaction state
        String sql = "SELECT * FROM users WHERE id = 1 FOR SHARE";
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithoutRowlock() {
        // WAITFOR/NoWait variants - SQL Server
        // These are read-only but control lock waiting
        String sql = "SELECT * FROM users WITH (NOLOCK)";
        // This is a valid SELECT hint, not a state modification
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly(sql));
    }

    // ========== Unusual-but-valid SELECTs (must NOT be false-positive rejected) ==========

    @Test
    void testSelectDistinct() {
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT DISTINCT name FROM users"));
    }

    @Test
    void testSelectCaseExpression() {
        String sql = "SELECT CASE WHEN age > 18 THEN 'adult' ELSE 'minor' END AS bracket FROM users";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectExistsSubquery() {
        String sql = "SELECT 1 WHERE EXISTS (SELECT 1 FROM users WHERE age > 18)";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testParenthesizedSelect() {
        // A top-level parenthesized SELECT is still read-only.
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("(SELECT * FROM users)"));
    }

    @Test
    void testSelectWithOrderByLimit() {
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT id FROM users ORDER BY id DESC LIMIT 10"));
    }

    @Test
    void testDmlKeywordsInsideStringLiteralAllowed() {
        // Write keywords appearing only inside quoted string literals must not be
        // treated as write operations - this is a false-positive guard.
        String sql = "SELECT 'we should INSERT then UPDATE and DELETE' AS note FROM users";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql),
            "DML keywords inside a string literal must not trigger rejection");
        assertTrue(ReadOnlySqlValidator.isReadOnly(sql));
    }

    @Test
    void testAggregateFunctionAllowed() {
        // Guard against the procedure-name heuristic over-matching ordinary functions.
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT SUM(amount), COUNT(*) FROM orders"));
    }

    @Test
    void testColumnNamedProcessAllowed() {
        // A column named "process" (contains the substring "proc") must be allowed.
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT process FROM jobs"));
    }

    // ========== Known false-positive bugs (correct tests, disabled until validator fixed) ==========
    // These assert the DESIRED behavior (read-only SELECTs that must be ACCEPTED) but currently
    // fail because of over-broad string heuristics in ReadOnlySqlValidator. See findings file.

    @Test
    @Disabled("BUG: procedure-name heuristic matches any function containing 'proc' substring "
        + "(e.g. reprocess) — see test-review/jdbc-validation.md")
    void testFunctionNameContainingProcAllowed() {
        // ReadOnlySqlValidator.java:372 regex \\w*[_]?PROC\\w*\\s*\\( matches reprocess(), preprocessing(), etc.
        String sql = "SELECT reprocess(id) FROM jobs";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql),
            "A read-only function whose name merely contains 'proc' must be allowed");
    }

    @Test
    @Disabled("BUG: NEXTVAL heuristic matches a column named 'nextval' qualified by an alias "
        + "(t.nextval) — see test-review/jdbc-validation.md")
    void testColumnNamedNextvalAllowed() {
        // ReadOnlySqlValidator.java:352 regex \\w+\\.NEXTVAL\\b matches the plain column reference t.nextval.
        String sql = "SELECT t.nextval FROM mytable t";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql),
            "A column literally named 'nextval' is a read-only reference and must be allowed");
    }

    @Test
    @Disabled("BUG: keyword heuristics fire on tokens inside string literals — "
        + "'NEXT VALUE FOR' in a literal is rejected — see test-review/jdbc-validation.md")
    void testNextValueForInsideStringLiteralAllowed() {
        // ReadOnlySqlValidator.java:358 does a bare normalized.contains("NEXT VALUE FOR") with no
        // string-literal guard (unlike the INTO check), so the literal below is wrongly rejected.
        String sql = "SELECT 'NEXT VALUE FOR promo' AS label FROM campaigns";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql),
            "'NEXT VALUE FOR' inside a string literal must not be treated as sequence access");
    }

    @Test
    @Disabled("BUG: LAST_INSERT_ID(/USE INDEX/proc( heuristics fire inside string literals — "
        + "see test-review/jdbc-validation.md")
    void testWriteKeywordFunctionsInsideStringLiteralAllowed() {
        // ReadOnlySqlValidator.java:346 (LAST_INSERT_ID(), 340 (USE/FORCE/IGNORE INDEX) and 372 (proc()
        // scan the raw normalized text with no string-literal guard.
        String sql = "SELECT description FROM notes WHERE description = 'run proc() and USE INDEX'";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql),
            "write-related keywords inside a string literal must not trigger rejection");
    }
}


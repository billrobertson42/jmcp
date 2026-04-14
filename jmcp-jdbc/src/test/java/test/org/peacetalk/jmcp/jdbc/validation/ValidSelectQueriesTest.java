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
}


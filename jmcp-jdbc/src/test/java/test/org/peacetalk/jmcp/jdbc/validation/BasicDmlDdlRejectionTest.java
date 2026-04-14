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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for basic DML/DDL statement rejection and edge cases.
 */
class BasicDmlDdlRejectionTest {

    // ========== Invalid Non-SELECT Queries ==========

    @Test
    void testRejectInsert() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INSERT INTO users (name, age) VALUES ('John', 30)"));
        assertTrue(ex.getMessage().contains("read-only"));
        assertFalse(ReadOnlySqlValidator.isReadOnly("INSERT INTO users VALUES (1, 'John')"));
    }

    @Test
    void testRejectUpdate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("UPDATE users SET age = 31 WHERE id = 1"));
        assertTrue(ex.getMessage().contains("read-only"));
    }

    @Test
    void testRejectDelete() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DELETE FROM users WHERE id = 1"));
        assertTrue(ex.getMessage().contains("read-only"));
    }

    @Test
    void testRejectDrop() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP TABLE users"));
    }

    @Test
    void testRejectCreate() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TABLE test (id INT)"));
    }

    @Test
    void testRejectAlter() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER TABLE users ADD COLUMN email VARCHAR(255)"));
    }

    @Test
    void testRejectTruncate() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("TRUNCATE TABLE users"));
    }

    @Test
    void testRejectMerge() {
        String sql = "MERGE INTO users USING updates ON users.id = updates.id WHEN MATCHED THEN UPDATE SET name = updates.name";
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(sql));
    }

    // ========== Multiple Statements ==========

    @Test
    void testRejectMixedStatements() {
        String sql = "SELECT * FROM users; INSERT INTO logs VALUES (1, 'access');";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(sql));
        assertTrue(ex.getMessage().contains("Statement 2"));
    }

    // ========== Edge Cases ==========

    @Test
    void testRejectNullSql() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(null));
    }

    @Test
    void testRejectBlankSql() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("   "));
    }

    @Test
    void testRejectEmptyString() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(""));
    }

    @Test
    void testKeywordsInStrings() {
        // Keywords in string literals should not trigger false positives
        String sql = "SELECT 'INSERT INTO fake' as message, 'UPDATE test' as another FROM users";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testKeywordsInComments() {
        // Keywords in comments should not trigger false positives
        String sql = "SELECT * FROM users -- This is a comment about INSERT";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectIntoRejected() {
        // SELECT INTO is a write operation in some databases
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * INTO new_table FROM users"));
    }

    @Test
    void testPostgreSQLReturningClause() {
        // This should be rejected as it's part of an INSERT
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INSERT INTO users VALUES (1, 'John') RETURNING id"));
    }

    // ========== Invalid SQL ==========

    @Test
    void testInvalidSqlThrowsParseException() {
        // Invalid SQL now throws IllegalArgumentException (treated as non-SELECT)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INVALID SQL GARBAGE;;;"));
    }

    @Test
    void testIncompleteSql() {
        // Incomplete SQL now throws IllegalArgumentException (treated as non-SELECT)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * FROM"));
    }
}


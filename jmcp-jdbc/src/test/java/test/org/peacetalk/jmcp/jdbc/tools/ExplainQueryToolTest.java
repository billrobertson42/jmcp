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

package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionSupplier;
import org.peacetalk.jmcp.jdbc.tools.ExplainQueryTool;
import org.peacetalk.jmcp.jdbc.tools.results.ExplainQueryResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

class ExplainQueryToolTest {

    private Connection connection;
    private ExplainQueryTool explainQueryTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
            stmt.execute("CREATE INDEX idx_users_age ON users(age)");

            // Insert test data
            for (int i = 1; i <= 100; i++) {
                stmt.execute("INSERT INTO users VALUES (" + i + ", 'User" + i + "', " + (20 + i % 50) + ")");
            }
        }

        explainQueryTool = new ExplainQueryTool();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testGetName() {
        assertEquals("explain-query", explainQueryTool.getName());
    }

    @Test
    void testGetDescription() {
        String description = explainQueryTool.getDescription();
        assertNotNull(description);
        String lower = description.toLowerCase();
        assertTrue(lower.contains("execution plan") || lower.contains("plan"),
            "description should mention the execution plan, was: " + description);
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = explainQueryTool.getInputSchema();
        assertNotNull(schema);

        assertThatJson(mapper.writeValueAsString(schema)).and(
            j -> j.node("properties.sql").isPresent(),
            j -> j.node("required").isArray(),
            j -> j.node("required[0]")
                .describedAs("'sql' must be the required field")
                .isEqualTo("sql")
        );
    }

    @Test
    void testExplainSimpleQuery() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users");

        Object result = explainQueryTool.execute(params, context);
        assertNotNull(result);
        assertInstanceOf(ExplainQueryResult.class, result);

        ExplainQueryResult explainResult = (ExplainQueryResult) result;
        // The echoed sql must be the (trimmed) original query, not the wrapped EXPLAIN.
        assertEquals("SELECT * FROM users", explainResult.sql());
        assertNotNull(explainResult.plan());
        assertFalse(explainResult.plan().isEmpty(), "H2 EXPLAIN produces a non-empty plan");
        // H2 plans reference the scanned table.
        assertTrue(explainResult.plan().toUpperCase().contains("USERS"),
            "plan should reference the USERS table, was: " + explainResult.plan());
    }

    @Test
    void testSqlIsTrimmedInResult() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "   SELECT * FROM users   ");

        ExplainQueryResult explainResult = (ExplainQueryResult) explainQueryTool.execute(params, context);
        assertEquals("SELECT * FROM users", explainResult.sql(),
            "leading/trailing whitespace should be trimmed before echoing sql");
    }

    @Test
    void testExplainQueryWithIndexUsesIndex() throws Exception {
        ConnectionSupplier context = () -> connection;

        // age has idx_users_age; H2's plan text names the index it chooses.
        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users WHERE age = 25");

        ExplainQueryResult explainResult = (ExplainQueryResult) explainQueryTool.execute(params, context);
        assertTrue(explainResult.plan().toUpperCase().contains("IDX_USERS_AGE"),
            "H2 plan for an indexed predicate should name idx_users_age, was: " + explainResult.plan());
    }

    @Test
    void testExplainJoinQuery() throws Exception {
        // Create another table for join
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE orders (id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2))");
            stmt.execute("INSERT INTO orders VALUES (1, 1, 100.00), (2, 2, 200.00)");
        }

        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT u.name, o.amount FROM users u JOIN orders o ON u.id = o.user_id");

        ExplainQueryResult explainResult = (ExplainQueryResult) explainQueryTool.execute(params, context);
        // Both joined tables should appear in the plan.
        String planUpper = explainResult.plan().toUpperCase();
        assertTrue(planUpper.contains("USERS") && planUpper.contains("ORDERS"),
            "join plan should reference both USERS and ORDERS, was: " + explainResult.plan());
    }

    @Test
    void testFormatTypeIsH2() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users");

        ExplainQueryResult explainResult = (ExplainQueryResult) explainQueryTool.execute(params, context);
        // The connection is H2, so getFormatType must resolve to exactly "H2" (not UNKNOWN).
        assertEquals("H2", explainResult.format(),
            "format should be resolved from the H2 product name");
    }

    @Test
    void testInvalidQueryThrowsException() {
        ConnectionSupplier context = () -> connection;

        // Query passes read-only validation but references a missing table,
        // so EXPLAIN fails at the database with a SQLException.
        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM nonexistent_table");

        assertThrows(java.sql.SQLException.class,
            () -> explainQueryTool.execute(params, context));
    }

    @Test
    void testRejectInsertStatement() {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "INSERT INTO users (id, name) VALUES (999, 'Hacker')");

        // Should throw IllegalArgumentException due to ReadOnlySqlValidator
        assertThrows(IllegalArgumentException.class, () -> {
            explainQueryTool.execute(params, context);
        });
    }

    @Test
    void testRejectUpdateStatement() {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "UPDATE users SET name = 'Hacked' WHERE id = 1");

        // Should throw IllegalArgumentException due to ReadOnlySqlValidator
        assertThrows(IllegalArgumentException.class, () -> {
            explainQueryTool.execute(params, context);
        });
    }

    @Test
    void testRejectDeleteStatement() {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "DELETE FROM users WHERE id = 1");

        // Should throw IllegalArgumentException due to ReadOnlySqlValidator
        assertThrows(IllegalArgumentException.class, () -> {
            explainQueryTool.execute(params, context);
        });
    }

    @Test
    void testRejectDropStatement() {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "DROP TABLE users");

        // Should throw IllegalArgumentException due to ReadOnlySqlValidator
        assertThrows(IllegalArgumentException.class, () -> {
            explainQueryTool.execute(params, context);
        });
    }
}


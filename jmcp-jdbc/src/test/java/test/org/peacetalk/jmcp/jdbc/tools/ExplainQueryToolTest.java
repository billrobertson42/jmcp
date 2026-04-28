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
        assertTrue(description.toLowerCase().contains("explain") ||
                   description.toLowerCase().contains("execution") ||
                   description.toLowerCase().contains("plan"));
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = explainQueryTool.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.has("properties"));
        assertTrue(schema.get("properties").has("sql"));
        assertTrue(schema.has("required"));
    }

    @Test
    void testExplainSimpleQuery() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users");

        Object result = explainQueryTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof ExplainQueryResult);

        ExplainQueryResult explainResult = (ExplainQueryResult) result;
        assertEquals("SELECT * FROM users", explainResult.sql());
        assertNotNull(explainResult.plan());
        assertFalse(explainResult.plan().isEmpty());
        assertNotNull(explainResult.format());
    }

    @Test
    void testExplainQueryWithWhere() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users WHERE age > 30");

        Object result = explainQueryTool.execute(params, context);
        assertTrue(result instanceof ExplainQueryResult);

        ExplainQueryResult explainResult = (ExplainQueryResult) result;
        assertNotNull(explainResult.plan());
        assertFalse(explainResult.plan().isEmpty());
    }

    @Test
    void testExplainQueryWithIndex() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users WHERE age = 25");

        Object result = explainQueryTool.execute(params, context);
        assertTrue(result instanceof ExplainQueryResult);

        ExplainQueryResult explainResult = (ExplainQueryResult) result;
        assertNotNull(explainResult.plan());
        // Plan should mention the index (database-specific)
        assertTrue(explainResult.plan().length() > 0);
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

        Object result = explainQueryTool.execute(params, context);
        assertTrue(result instanceof ExplainQueryResult);

        ExplainQueryResult explainResult = (ExplainQueryResult) result;
        assertNotNull(explainResult.plan());
        // Plan should show join operation
        assertTrue(explainResult.plan().length() > 0);
    }

    @Test
    void testExplainAggregateQuery() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT age, COUNT(*) FROM users GROUP BY age");

        Object result = explainQueryTool.execute(params, context);
        assertTrue(result instanceof ExplainQueryResult);

        ExplainQueryResult explainResult = (ExplainQueryResult) result;
        assertNotNull(explainResult.plan());
        assertTrue(explainResult.plan().length() > 0);
    }

    @Test
    void testFormatTypeIsSet() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users");

        Object result = explainQueryTool.execute(params, context);
        ExplainQueryResult explainResult = (ExplainQueryResult) result;

        assertNotNull(explainResult.format());
        // H2 database should return "H2" format
        assertTrue(explainResult.format().contains("H2") ||
                   explainResult.format().equals("UNKNOWN"));
    }

    @Test
    void testInvalidQueryThrowsException() {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM nonexistent_table");

        assertThrows(Exception.class, () -> {
            explainQueryTool.execute(params, context);
        });
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


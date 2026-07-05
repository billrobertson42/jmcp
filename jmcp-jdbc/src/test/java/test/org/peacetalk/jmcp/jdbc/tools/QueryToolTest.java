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
import org.peacetalk.jmcp.jdbc.tools.QueryTool;
import org.peacetalk.jmcp.jdbc.tools.results.CompactQueryResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

class QueryToolTest {

    private Connection connection;
    private QueryTool queryTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        // Create in-memory H2 database
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        // Create test table
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 35)");
        }

        queryTool = new QueryTool();
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
        assertEquals("query", queryTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(queryTool.getDescription());
        assertTrue(queryTool.getDescription().contains("SELECT"));
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = queryTool.getInputSchema();
        assertNotNull(schema);

        assertThatJson(mapper.writeValueAsString(schema)).and(
            j -> j.node("type").isEqualTo("object"),
            j -> j.node("properties.sql").isPresent()
        );
    }

    @Test
    void testExecuteSimpleQuery() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT id, name, age FROM users ORDER BY id");

        Object result = queryTool.execute(params, context);
        assertNotNull(result);
        assertInstanceOf(CompactQueryResult.class, result);

        CompactQueryResult queryResult = (CompactQueryResult) result;
        // Compact format: columns are the projected names in order.
        assertEquals(List.of("ID", "NAME", "AGE"), queryResult.columns());
        assertEquals(3, queryResult.count(), "three rows inserted in setUp");
        assertEquals(3, queryResult.rows().size(), "count must match rows list size");
        assertFalse(queryResult.hasMore(), "result is well under MAX_ROWS so more=false");

        // rows are positional arrays aligned to columns, not keyed objects.
        List<Object> firstRow = queryResult.rows().get(0);
        assertEquals(3, firstRow.size(), "each row array must have one cell per column");
        assertEquals(1, ((Number) firstRow.get(0)).intValue(), "row 0 col 0 = id");
        assertEquals("Alice", firstRow.get(1), "row 0 col 1 = name");
        assertEquals(30, ((Number) firstRow.get(2)).intValue(), "row 0 col 2 = age");
        // query tool does not attach table/schema context.
        assertNull(queryResult.table(), "query tool leaves table null");
        assertNull(queryResult.schema(), "query tool leaves schema null");
    }

    @Test
    void testExecuteQueryWithWhereClause() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT id, name FROM users WHERE age > 28 ORDER BY id");

        Object result = queryTool.execute(params, context);
        assertInstanceOf(CompactQueryResult.class, result);

        CompactQueryResult queryResult = (CompactQueryResult) result;
        assertEquals(2, queryResult.count(), "Alice(30) and Charlie(35) match age > 28");
        // Verify the WHERE actually filtered: Bob(25) must be absent.
        assertEquals("Alice", queryResult.rows().get(0).get(1));
        assertEquals("Charlie", queryResult.rows().get(1).get(1));
    }

    @Test
    void testExecuteQueryReturningNoRows() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT id, name FROM users WHERE age > 1000");

        CompactQueryResult queryResult = (CompactQueryResult) queryTool.execute(params, context);
        assertEquals(0, queryResult.count(), "no rows match the predicate");
        assertTrue(queryResult.rows().isEmpty(), "rows list should be empty, not null");
        assertFalse(queryResult.hasMore(), "no rows means no more rows");
        // Column metadata is still available on an empty result set.
        assertEquals(List.of("ID", "NAME"), queryResult.columns());
    }

    @Test
    void testExecuteQueryWithParameters() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT name FROM users WHERE id = ?");
        ArrayNode boundParams = params.putArray("parameters");
        boundParams.add("2");

        CompactQueryResult queryResult = (CompactQueryResult) queryTool.execute(params, context);
        assertEquals(1, queryResult.count(), "only id=2 should match");
        // Bound parameters are always sent as strings; H2 coerces "2" to INT for the id column.
        assertEquals("Bob", queryResult.rows().get(0).get(0),
            "parameter binding must resolve to the row with id=2");
    }

    @Test
    void testValidateOnlyDoesNotExecute() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users");
        params.put("validate_only", true);

        Object result = queryTool.execute(params, context);
        assertInstanceOf(QueryTool.ValidationResult.class, result,
            "validate_only must short-circuit to a ValidationResult, not a query result");

        QueryTool.ValidationResult validation = (QueryTool.ValidationResult) result;
        assertTrue(validation.valid(), "valid SELECT should report valid=true");
        assertNotNull(validation.message());
    }

    @Test
    void testRejectNonSelectQuery() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "DELETE FROM users WHERE id = 1");

        // JSqlParser validates and rejects DELETE statements before any execution.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> queryTool.execute(params, context));
        assertTrue(ex.getMessage().contains("Only SELECT queries are allowed"),
            "rejection message should explain the read-only restriction, was: " + ex.getMessage());

        // The DELETE must not have taken effect: all 3 rows still present.
        ObjectNode countParams = mapper.createObjectNode();
        countParams.put("sql", "SELECT id FROM users");
        CompactQueryResult after = (CompactQueryResult) queryTool.execute(countParams, context);
        assertEquals(3, after.count(), "rejected DELETE must not have modified the table");
    }

    @Test
    void testRejectInsertQuery() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "INSERT INTO users VALUES (4, 'Dave', 40)");

        // JSqlParser validates and rejects INSERT statements
        assertThrows(IllegalArgumentException.class, () -> queryTool.execute(params, context));
    }

    @Test
    void testRejectUpdateQuery() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "UPDATE users SET age = 31 WHERE id = 1");

        // JSqlParser validates and rejects UPDATE statements
        assertThrows(IllegalArgumentException.class, () -> queryTool.execute(params, context));
    }

    @Test
    void testRejectDropStatement() {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "DROP TABLE users");

        assertThrows(IllegalArgumentException.class, () -> queryTool.execute(params, context));
    }

    @Test
    void testValidateOnlyStillRejectsNonSelect() {
        ConnectionSupplier context = () -> connection;

        // Read-only validation runs before the validate_only short-circuit,
        // so a DELETE must be rejected even when only asking to validate syntax.
        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "DELETE FROM users WHERE id = 1");
        params.put("validate_only", true);

        assertThrows(IllegalArgumentException.class, () -> queryTool.execute(params, context));
    }

    @Test
    void testBlankSqlIsRejected() {
        ConnectionSupplier context = () -> connection;

        // sql.trim() yields empty; ReadOnlySqlValidator rejects null/blank SQL.
        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "   ");

        assertThrows(IllegalArgumentException.class, () -> queryTool.execute(params, context));
    }
}


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
import org.peacetalk.jmcp.jdbc.tools.GetRowCountTool;
import org.peacetalk.jmcp.jdbc.tools.results.RowCountResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

class GetRowCountToolTest {

    private Connection connection;
    private GetRowCountTool getRowCountTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("INSERT INTO products VALUES (1, 'Product 1')");
            stmt.execute("INSERT INTO products VALUES (2, 'Product 2')");
            stmt.execute("INSERT INTO products VALUES (3, 'Product 3')");
            stmt.execute("INSERT INTO products VALUES (4, 'Product 4')");
            stmt.execute("INSERT INTO products VALUES (5, 'Product 5')");

            // An empty table for the zero-count boundary case.
            stmt.execute("CREATE TABLE empty_table (id INT PRIMARY KEY)");
        }

        getRowCountTool = new GetRowCountTool();
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
        assertEquals("get-row-count", getRowCountTool.getName());
    }

    @Test
    void testGetDescription() {
        String description = getRowCountTool.getDescription();
        assertNotNull(description);
        // The description documents that this is an exact COUNT(*), not an estimate.
        assertTrue(description.contains("COUNT(*)"),
            "description should mention COUNT(*), was: " + description);
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = getRowCountTool.getInputSchema();
        assertNotNull(schema);

        assertThatJson(mapper.writeValueAsString(schema)).and(
            j -> j.node("properties.table").isPresent(),
            j -> j.node("required").isArray(),
            j -> j.node("required[0]")
                .describedAs("'table' must be the required field")
                .isEqualTo("table")
        );
    }

    @Test
    void testExecuteGetRowCount() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "PRODUCTS");

        Object result = getRowCountTool.execute(params, context);
        assertNotNull(result);
        assertInstanceOf(RowCountResult.class, result);

        RowCountResult rowCountResult = (RowCountResult) result;
        assertEquals("PRODUCTS", rowCountResult.table());
        assertEquals(5, rowCountResult.rowCount(), "five products were inserted");
        // schema is resolved from the connection default (H2 -> PUBLIC) even when caller omits it.
        assertEquals("PUBLIC", rowCountResult.schema(),
            "omitted schema should resolve to the connection's default schema");
    }

    @Test
    void testExecuteGetRowCountEmptyTable() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "EMPTY_TABLE");

        RowCountResult result = (RowCountResult) getRowCountTool.execute(params, context);
        assertEquals(0, result.rowCount(), "empty table must report a count of zero");
    }

    @Test
    void testLowercaseTableNameIsResolved() throws Exception {
        // H2 stores identifiers uppercase; validateTableExists retries in uppercase,
        // so a lowercase request should still succeed.
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "products");

        RowCountResult result = (RowCountResult) getRowCountTool.execute(params, context);
        assertEquals(5, result.rowCount(), "lowercase table name should resolve to PRODUCTS");
    }

    @Test
    void testNonexistentTableThrows() {
        // A table name that fails metadata validation must be rejected (SQL-injection guard),
        // not blindly interpolated into the COUNT(*) statement.
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "DOES_NOT_EXIST");

        SQLException ex = assertThrows(SQLException.class,
            () -> getRowCountTool.execute(params, context));
        assertTrue(ex.getMessage().contains("does not exist"),
            "should report the table does not exist, was: " + ex.getMessage());
    }

    @Test
    void testSqlInjectionTableNameRejected() {
        // The table argument is validated against DatabaseMetaData, so an injection
        // payload cannot reach the executed SQL.
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "PRODUCTS; DROP TABLE PRODUCTS; --");

        assertThrows(SQLException.class, () -> getRowCountTool.execute(params, context));
    }
}


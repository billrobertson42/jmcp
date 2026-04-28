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
import java.sql.Statement;

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
        assertNotNull(getRowCountTool.getDescription());
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = getRowCountTool.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.get("properties").has("table"));
        assertTrue(schema.has("required"));
    }

    @Test
    void testExecuteGetRowCount() throws Exception {
        ConnectionSupplier context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "PRODUCTS");

        Object result = getRowCountTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof RowCountResult);

        RowCountResult rowCountResult = (RowCountResult) result;
        assertEquals("PRODUCTS", rowCountResult.table());
        assertEquals(5, rowCountResult.rowCount());
    }
}


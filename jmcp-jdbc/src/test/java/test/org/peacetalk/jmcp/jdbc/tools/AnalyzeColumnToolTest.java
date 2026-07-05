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
import org.peacetalk.jmcp.jdbc.tools.AnalyzeColumnTool;
import org.peacetalk.jmcp.jdbc.tools.results.ColumnAnalysis;
import org.peacetalk.jmcp.jdbc.tools.results.ValueFrequency;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeColumnToolTest {

    private Connection connection;
    private AnalyzeColumnTool tool;
    private ObjectMapper mapper;
    private ConnectionSupplier ctx;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE products (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    price DECIMAL(10, 2),
                    stock_quantity INT
                )
                """);

            // Insert test data with some variety
            for (int i = 1; i <= 100; i++) {
                stmt.execute(String.format(
                    "INSERT INTO products VALUES (%d, 'Product %d', %.2f, %d)",
                    i, i, 10.0 + (i % 50), 5 + (i % 20)
                ));
            }

            // A small table with fully known statistics so exact assertions are possible:
            // category has 3 non-null distinct values ('A' x3, 'B' x2, 'C' x1) plus 2 NULLs.
            stmt.execute("CREATE TABLE items (id INT PRIMARY KEY, category VARCHAR(10))");
            stmt.execute("INSERT INTO items VALUES (1, 'A')");
            stmt.execute("INSERT INTO items VALUES (2, 'A')");
            stmt.execute("INSERT INTO items VALUES (3, 'A')");
            stmt.execute("INSERT INTO items VALUES (4, 'B')");
            stmt.execute("INSERT INTO items VALUES (5, 'B')");
            stmt.execute("INSERT INTO items VALUES (6, 'C')");
            stmt.execute("INSERT INTO items VALUES (7, NULL)");
            stmt.execute("INSERT INTO items VALUES (8, NULL)");
        }

        tool = new AnalyzeColumnTool();
        mapper = new ObjectMapper();

        ctx = new ConnectionSupplier() {
            public String getConnectionId() {
                return "test";
            }

            public Connection getConnection() {
                return connection;
            }
        };
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testGetName() {
        assertEquals("analyze-column", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertFalse(description.isBlank());
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = tool.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.isObject());
        JsonNode properties = schema.get("properties");
        assertTrue(properties.has("table"));
        assertTrue(properties.has("column"));
        // top_values is bounded 1..50 per MAX_TOP_VALUES_COUNT.
        JsonNode topValues = properties.get("top_values");
        assertEquals(1, topValues.get("minimum").asInt(), "top_values minimum should be 1");
        assertEquals(50, topValues.get("maximum").asInt(), "top_values maximum should be 50");
    }

    @Test
    void testAnalyzeIntegerColumnExactStats() throws Exception {
        // id is the primary key: 100 unique non-null values 1..100.
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "id");
        input.put("schema", "PUBLIC");

        ColumnAnalysis analysis = (ColumnAnalysis) tool.execute(mapper.valueToTree(input), ctx);

        assertEquals("id", analysis.column(), "original (as-requested) column name is echoed");
        assertEquals("products", analysis.table());
        assertEquals(100, analysis.totalRows());
        assertEquals(100, analysis.distinctCount(), "every id is unique");
        assertEquals(0, analysis.nullCount(), "id is a non-null primary key");
        assertEquals("1", analysis.minValue(), "min id is 1 (rendered as string)");
        assertEquals("100", analysis.maxValue(), "max id is 100 (rendered as string)");
    }

    @Test
    void testAnalyzeColumnWithNullsAndTopValueOrdering() throws Exception {
        // items.category: 'A' x3, 'B' x2, 'C' x1, plus 2 NULLs -> 8 total rows.
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "items");
        input.put("column", "category");
        input.put("schema", "PUBLIC");

        ColumnAnalysis analysis = (ColumnAnalysis) tool.execute(mapper.valueToTree(input), ctx);

        assertEquals(8, analysis.totalRows());
        assertEquals(3, analysis.distinctCount(), "distinct excludes NULL: A, B, C");
        assertEquals(2, analysis.nullCount(), "two rows have NULL category");
        assertEquals("A", analysis.minValue());
        assertEquals("C", analysis.maxValue());

        // Top values are ordered by descending frequency and exclude NULLs.
        List<ValueFrequency> top = analysis.topValues();
        assertEquals(3, top.size(), "three non-null distinct values");
        assertEquals("A", top.get(0).value(), "'A' is most frequent");
        assertEquals(3, top.get(0).frequency());
        assertEquals(2, top.get(1).frequency(), "'B' is second with 2");
        assertEquals(1, top.get(2).frequency(), "'C' is least with 1");
    }

    @Test
    void testTopValuesRespectsLimit() throws Exception {
        // items.category has 3 distinct values; a limit of 2 must truncate to the top 2.
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "items");
        input.put("column", "category");
        input.put("schema", "PUBLIC");
        input.put("top_values", 2);

        ColumnAnalysis analysis = (ColumnAnalysis) tool.execute(mapper.valueToTree(input), ctx);

        assertEquals(2, analysis.topValues().size(), "top_values=2 should cap the list at 2");
        assertEquals("A", analysis.topValues().get(0).value());
        assertEquals("B", analysis.topValues().get(1).value());
    }

    @Test
    void testDefaultTopValuesCountIsTen() throws Exception {
        // With no top_values, the default of 10 applies. products.stock_quantity has
        // exactly 20 distinct values (5 + i%20), so the list must be capped at 10.
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "stock_quantity");
        input.put("schema", "PUBLIC");

        ColumnAnalysis analysis = (ColumnAnalysis) tool.execute(mapper.valueToTree(input), ctx);

        assertEquals(20, analysis.distinctCount(), "stock_quantity has 20 distinct values");
        assertEquals(10, analysis.topValues().size(),
            "default top_values is 10, so the list is capped at 10 even though 20 exist");
    }

    @Test
    void testTopValuesZeroRejected() {
        // top_values must be between 1 and 50; 0 is out of range.
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "id");
        input.put("schema", "PUBLIC");
        input.put("top_values", 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> tool.execute(mapper.valueToTree(input), ctx));
        assertTrue(ex.getMessage().contains("between 1 and 50"),
            "message should state the valid range, was: " + ex.getMessage());
    }

    @Test
    void testTopValuesAboveMaxRejected() {
        // 51 exceeds MAX_TOP_VALUES_COUNT (50).
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "id");
        input.put("schema", "PUBLIC");
        input.put("top_values", 51);

        assertThrows(IllegalArgumentException.class,
            () -> tool.execute(mapper.valueToTree(input), ctx));
    }

    @Test
    void testTopValuesAtMaxAccepted() throws Exception {
        // 50 is the inclusive upper bound and must be accepted.
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "id");
        input.put("schema", "PUBLIC");
        input.put("top_values", 50);

        assertDoesNotThrow(() -> tool.execute(mapper.valueToTree(input), ctx));
    }

    @Test
    void testLowercaseColumnResolvedButOriginalEchoed() throws Exception {
        // H2 stores columns uppercase; a lowercase request resolves via metadata,
        // yet the response echoes the original (lowercase) column name.
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "items");
        input.put("column", "category");
        input.put("schema", "PUBLIC");

        ColumnAnalysis analysis = (ColumnAnalysis) tool.execute(mapper.valueToTree(input), ctx);
        assertEquals("category", analysis.column(), "original column name is returned verbatim");
        assertEquals(3, analysis.distinctCount(), "analysis ran against the resolved CATEGORY column");
    }

    @Test
    void testMissingTableThrowsError() {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "nonexistent");
        input.put("column", "id");
        input.put("schema", "PUBLIC");

        java.sql.SQLException ex = assertThrows(java.sql.SQLException.class,
            () -> tool.execute(mapper.valueToTree(input), ctx));
        assertTrue(ex.getMessage().contains("does not exist"),
            "should report the table does not exist, was: " + ex.getMessage());
    }

    @Test
    void testMissingColumnThrowsError() {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "nonexistent");
        input.put("schema", "PUBLIC");

        java.sql.SQLException ex = assertThrows(java.sql.SQLException.class,
            () -> tool.execute(mapper.valueToTree(input), ctx));
        assertTrue(ex.getMessage().contains("nonexistent") && ex.getMessage().contains("does not exist"),
            "should report the missing column, was: " + ex.getMessage());
    }
}


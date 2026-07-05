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
import org.peacetalk.jmcp.jdbc.tools.SampleDataTool;
import org.peacetalk.jmcp.jdbc.tools.results.CompactQueryResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SampleDataToolTest {

    private Connection connection;
    private SampleDataTool tool;
    private ObjectMapper mapper;
    private ConnectionSupplier ctx;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE employees (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(100),
                    salary DECIMAL(10, 2),
                    department VARCHAR(50)
                )
                """);

            // Insert test data
            for (int i = 1; i <= 50; i++) {
                stmt.execute(String.format(
                    "INSERT INTO employees (name, salary, department) VALUES ('Employee %d', %.2f, 'Dept %d')",
                    i, 50000.0 + (i * 1000), (i % 5)
                ));
            }
        }

        tool = new SampleDataTool();
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
        assertEquals("sample-data", tool.getName());
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
        assertTrue(schema.has("properties"));

        JsonNode props = schema.get("properties");
        // The parameters an LLM needs must actually be declared.
        assertTrue(props.has("table"), "table is the only required input");
        assertTrue(props.has("sample_size"));
        assertTrue(props.has("strategy"));
        assertEquals("table", schema.get("required").get(0).asString());

        // sample_size must advertise its clamp bounds (1..100) in the schema so
        // clients can validate without parsing the English description.
        JsonNode sampleSize = props.get("sample_size");
        assertEquals(1, sampleSize.get("minimum").asInt());
        assertEquals(100, sampleSize.get("maximum").asInt());
    }

    @Test
    void testSampleFirstRows() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 10);

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        assertEquals("employees", result.table());
        assertEquals(10, result.count(), "sample_size=10 must return 10 rows");
        assertEquals(10, result.rows().size());
        assertEquals(4, result.columns().size(), "no column filter → all 4 columns");
        result.rows().forEach(row ->
            assertEquals(4, row.size(), "each row must carry every projected column"));
    }

    @Test
    void testSampleRandomRows() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "random");
        input.put("sample_size", 10);

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        // Random still returns exactly the requested number of rows.
        assertEquals(10, result.count());
        assertEquals(10, result.rows().size());
    }

    @Test
    void testSampleLastRows() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "last");
        input.put("sample_size", 10);

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        assertEquals(10, result.count());
        assertEquals(10, result.rows().size());
    }

    @Test
    void testSampleWithColumnFilter() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 10);
        input.put("columns", "id,name");

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        // Only the two requested columns must be projected — not all four.
        var cols = result.columns();
        assertEquals(2, cols.size(), "column filter must restrict the projection");
        assertTrue(cols.stream().anyMatch(c -> c.equalsIgnoreCase("id")));
        assertTrue(cols.stream().anyMatch(c -> c.equalsIgnoreCase("name")));
        assertTrue(cols.stream().noneMatch(c -> c.equalsIgnoreCase("salary")),
            "unrequested columns must not leak into the result");
        result.rows().forEach(row -> assertEquals(2, row.size()));
    }

    @Test
    void testSampleWithDefaultStrategy() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("sample_size", 10);

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        assertEquals(10, result.count());
        assertEquals(10, result.rows().size());
    }

    @Test
    void testSampleLargeRowCount() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 100);  // Max allowed is 100

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        // The table only has 50 rows, so the sample is capped by available data.
        assertEquals(50, result.count(), "cannot return more rows than the table holds");
        assertEquals(50, result.rows().size());
    }

    @Test
    void testSampleSmallRowCount() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 1);

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        assertEquals(1, result.count());
        assertEquals(1, result.rows().size());
    }

    @Test
    void testMissingTableThrowsError() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "nonexistent");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 10);

        assertThrows(Exception.class, () ->
            tool.execute(mapper.valueToTree(input), ctx)
        );
    }

    @Test
    void testInvalidStrategyDefaultsOrThrows() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "invalid_strategy");
        input.put("sample_size", 10);

        // Should throw - invalid strategy rejection
        assertThrows(IllegalArgumentException.class, () ->
            tool.execute(mapper.valueToTree(input), ctx)
        );
    }

    @Test
    void testSampleWithoutRowCount() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");

        CompactQueryResult result = assertInstanceOf(CompactQueryResult.class,
            tool.execute(mapper.valueToTree(input), ctx));

        // No sample_size supplied → the default (10) is applied.
        assertEquals(10, result.count(), "omitted sample_size must fall back to the default of 10");
        assertEquals(10, result.rows().size());
    }
}


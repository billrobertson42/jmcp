package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.SampleDataTool;
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
    private ConnectionContext ctx;

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

        ctx = new ConnectionContext() {
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
    }

    @Test
    void testSampleFirstRows() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 10);

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }

    @Test
    void testSampleRandomRows() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "random");
        input.put("sample_size", 10);

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }

    @Test
    void testSampleLastRows() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "last");
        input.put("sample_size", 10);

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }

    @Test
    void testSampleWithColumnFilter() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 10);
        input.put("columns", "id,name");

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }

    @Test
    void testSampleWithDefaultStrategy() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("sample_size", 10);

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }

    @Test
    void testSampleLargeRowCount() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 100);  // Max allowed is 100

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }

    @Test
    void testSampleSmallRowCount() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "employees");
        input.put("database_id", "test");
        input.put("strategy", "first");
        input.put("sample_size", 1);

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
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

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }
}


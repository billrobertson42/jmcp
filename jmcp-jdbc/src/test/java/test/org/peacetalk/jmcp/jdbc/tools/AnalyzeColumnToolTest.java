package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.AnalyzeColumnTool;
import org.peacetalk.jmcp.jdbc.tools.results.ColumnAnalysis;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeColumnToolTest {

    private Connection connection;
    private AnalyzeColumnTool tool;
    private ObjectMapper mapper;
    private ConnectionContext ctx;

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
        }

        tool = new AnalyzeColumnTool();
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
        assertTrue(schema.has("properties"));
    }

    @Test
    void testAnalyzeIntegerColumn() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "id");
        input.put("schema", "PUBLIC");
        input.put("database_id", "test");

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
        ColumnAnalysis analysis = mapper.convertValue(result, ColumnAnalysis.class);

        assertEquals("id", analysis.column());
        assertTrue(analysis.distinctCount() > 0);
    }

    @Test
    void testAnalyzeStringColumn() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "name");
        input.put("schema", "PUBLIC");
        input.put("database_id", "test");

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
        ColumnAnalysis analysis = mapper.convertValue(result, ColumnAnalysis.class);

        assertEquals("name", analysis.column());
    }

    @Test
    void testAnalyzeDecimalColumn() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "price");
        input.put("schema", "PUBLIC");
        input.put("database_id", "test");

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
        ColumnAnalysis analysis = mapper.convertValue(result, ColumnAnalysis.class);

        assertEquals("price", analysis.column());
    }

    @Test
    void testTopValuesWithLimit() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "stock_quantity");
        input.put("schema", "PUBLIC");
        input.put("database_id", "test");
        input.put("top_values", 5);

        Object result = tool.execute(mapper.valueToTree(input), ctx);

        assertNotNull(result);
    }

    @Test
    void testMissingTableThrowsError() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "nonexistent");
        input.put("column", "id");
        input.put("schema", "PUBLIC");
        input.put("database_id", "test");

        assertThrows(Exception.class, () ->
            tool.execute(mapper.valueToTree(input), ctx)
        );
    }

    @Test
    void testMissingColumnThrowsError() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "nonexistent");
        input.put("schema", "PUBLIC");
        input.put("database_id", "test");

        assertThrows(Exception.class, () ->
            tool.execute(mapper.valueToTree(input), ctx)
        );
    }

    @Test
    void testAnalyzeWithoutTopValues() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("table", "products");
        input.put("column", "id");
        input.put("schema", "PUBLIC");
        input.put("database_id", "test");

        Object result = tool.execute(mapper.valueToTree(input), ctx);
        assertNotNull(result);
    }
}


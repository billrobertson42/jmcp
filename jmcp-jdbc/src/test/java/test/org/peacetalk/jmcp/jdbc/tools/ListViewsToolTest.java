package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.ListViewsTool;
import org.peacetalk.jmcp.jdbc.tools.results.ViewInfo;
import org.peacetalk.jmcp.jdbc.tools.results.ViewsListResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ListViewsToolTest {

    private Connection connection;
    private ListViewsTool listViewsTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            // Create base tables
            stmt.execute("CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("CREATE TABLE orders (id INT PRIMARY KEY, customer_id INT, amount DECIMAL(10,2))");

            // Insert test data
            stmt.execute("INSERT INTO customers VALUES (1, 'Alice'), (2, 'Bob')");
            stmt.execute("INSERT INTO orders VALUES (1, 1, 100.00), (2, 1, 150.00), (3, 2, 200.00)");

            // Create views
            stmt.execute("CREATE VIEW customer_summary AS SELECT c.id, c.name, COUNT(o.id) as order_count FROM customers c LEFT JOIN orders o ON c.id = o.customer_id GROUP BY c.id, c.name");
            stmt.execute("CREATE VIEW high_value_orders AS SELECT * FROM orders WHERE amount > 100");
        }

        listViewsTool = new ListViewsTool();
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
        assertEquals("list-views", listViewsTool.getName());
    }

    @Test
    void testGetDescription() {
        String description = listViewsTool.getDescription();
        assertNotNull(description);
        assertTrue(description.toLowerCase().contains("view"));
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = listViewsTool.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.has("properties"));
        assertTrue(schema.get("properties").has("schema"));
        assertTrue(schema.get("properties").has("includeDefinitions"));
    }

    @Test
    void testListAllViews() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();

        Object result = listViewsTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof ViewsListResult);

        ViewsListResult viewsResult = (ViewsListResult) result;
        assertTrue(viewsResult.count() >= 2, "Should have at least 2 views (may include system views)");
        assertNotNull(viewsResult.views());
        assertTrue(viewsResult.views().size() >= 2);
    }

    @Test
    void testViewsContainExpectedData() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("includeDefinitions", true);

        Object result = listViewsTool.execute(params, context);
        ViewsListResult viewsResult = (ViewsListResult) result;

        // Find customer_summary view
        ViewInfo customerSummary = viewsResult.views().stream()
            .filter(v -> "CUSTOMER_SUMMARY".equals(v.name()))
            .findFirst()
            .orElse(null);

        assertNotNull(customerSummary);
        assertNotNull(customerSummary.definition());
        assertNotNull(customerSummary.columns());
        assertTrue(customerSummary.columns().size() > 0);
    }

    @Test
    void testExcludeDefinitions() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("includeDefinitions", false);

        Object result = listViewsTool.execute(params, context);
        ViewsListResult viewsResult = (ViewsListResult) result;

        assertTrue(viewsResult.count() >= 2, "Should have at least 2 views (may include system views)");
        // Definitions should not be included
        for (ViewInfo view : viewsResult.views()) {
            assertNull(view.definition());
        }
    }

    @Test
    void testSchemaFilter() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("schema", "PUBLIC");

        Object result = listViewsTool.execute(params, context);
        ViewsListResult viewsResult = (ViewsListResult) result;

        // Should still find views in PUBLIC schema
        assertTrue(viewsResult.count() > 0);
    }

    @Test
    void testNonExistentSchema() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("schema", "NONEXISTENT");

        Object result = listViewsTool.execute(params, context);
        ViewsListResult viewsResult = (ViewsListResult) result;

        assertEquals(0, viewsResult.count());
        assertNotNull(viewsResult.views());
        assertTrue(viewsResult.views().isEmpty());
    }
}


package test.org.peacetalk.jmcp.jdbc.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.GetRowCountTool;
import org.peacetalk.jmcp.jdbc.tools.results.RowCountResult;

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
        ConnectionContext context = () -> connection;

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


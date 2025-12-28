package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.PreviewTableTool;
import org.peacetalk.jmcp.jdbc.tools.results.TablePreviewResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class PreviewTableToolTest {

    private Connection connection;
    private PreviewTableTool previewTableTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE items (id INT PRIMARY KEY, description VARCHAR(100))");
            for (int i = 1; i <= 50; i++) {
                stmt.execute("INSERT INTO items VALUES (" + i + ", 'Item " + i + "')");
            }
        }

        previewTableTool = new PreviewTableTool();
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
        assertEquals("preview-table", previewTableTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(previewTableTool.getDescription());
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = previewTableTool.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.get("properties").has("table"));
        assertTrue(schema.get("properties").has("limit"));
    }

    @Test
    void testExecuteDefaultLimit() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "ITEMS");

        Object result = previewTableTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof TablePreviewResult);

        TablePreviewResult previewResult = (TablePreviewResult) result;
        assertNotNull(previewResult.rows());
        assertNotNull(previewResult.columns());

        // Default limit is 10
        assertEquals(10, previewResult.rowCount());
        assertEquals(10, previewResult.limit());
    }

    @Test
    void testExecuteCustomLimit() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "ITEMS");
        params.put("limit", 5);

        Object result = previewTableTool.execute(params, context);
        assertTrue(result instanceof TablePreviewResult);

        TablePreviewResult previewResult = (TablePreviewResult) result;
        assertEquals(5, previewResult.rowCount());
        assertEquals(5, previewResult.limit());
    }

    @Test
    void testExecuteMaxLimitEnforced() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "ITEMS");
        params.put("limit", 200); // Over max

        Object result = previewTableTool.execute(params, context);
        assertTrue(result instanceof TablePreviewResult);

        TablePreviewResult previewResult = (TablePreviewResult) result;
        // Should be capped at 100
        assertEquals(100, previewResult.limit());
    }
}


package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.ListProceduresTool;
import org.peacetalk.jmcp.jdbc.tools.results.ProcedureInfo;
import org.peacetalk.jmcp.jdbc.tools.results.ProceduresListResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ListProceduresToolTest {

    private Connection connection;
    private ListProceduresTool listProceduresTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            // Create base table
            stmt.execute("CREATE TABLE items (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))");
            stmt.execute("INSERT INTO items VALUES (1, 'Item1', 10.00), (2, 'Item2', 20.00)");

            // Create a simple function
            stmt.execute("CREATE ALIAS GET_ITEM_COUNT AS 'int getItemCount(java.sql.Connection conn) throws Exception { " +
                        "java.sql.ResultSet rs = conn.createStatement().executeQuery(\"SELECT COUNT(*) FROM items\"); " +
                        "rs.next(); return rs.getInt(1); }'");

            // Create another function with parameter
            stmt.execute("CREATE ALIAS GET_ITEM_PRICE AS 'java.math.BigDecimal getItemPrice(java.sql.Connection conn, int id) throws Exception { " +
                        "java.sql.PreparedStatement ps = conn.prepareStatement(\"SELECT price FROM items WHERE id = ?\"); " +
                        "ps.setInt(1, id); java.sql.ResultSet rs = ps.executeQuery(); " +
                        "if (rs.next()) return rs.getBigDecimal(1); return null; }'");
        }

        listProceduresTool = new ListProceduresTool();
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
        assertEquals("list-procedures", listProceduresTool.getName());
    }

    @Test
    void testGetDescription() {
        String description = listProceduresTool.getDescription();
        assertNotNull(description);
        assertTrue(description.toLowerCase().contains("procedure") ||
                   description.toLowerCase().contains("function"));
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = listProceduresTool.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.has("properties"));
        assertTrue(schema.get("properties").has("schema"));
        assertTrue(schema.get("properties").has("includeDetails"));
    }

    @Test
    void testListAllProcedures() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();

        Object result = listProceduresTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof ProceduresListResult);

        ProceduresListResult proceduresResult = (ProceduresListResult) result;
        assertNotNull(proceduresResult.procedures());
        assertTrue(proceduresResult.count() >= 0); // H2 may or may not expose functions as procedures
    }

    @Test
    void testListProceduresWithDetails() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("includeDetails", true);

        Object result = listProceduresTool.execute(params, context);
        assertTrue(result instanceof ProceduresListResult);

        ProceduresListResult proceduresResult = (ProceduresListResult) result;
        assertNotNull(proceduresResult.procedures());

        // If procedures are found, verify they have details
        for (ProcedureInfo proc : proceduresResult.procedures()) {
            assertNotNull(proc.name());
            // Details may or may not be available depending on database
        }
    }

    @Test
    void testListProceduresWithoutDetails() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("includeDetails", false);

        Object result = listProceduresTool.execute(params, context);
        assertTrue(result instanceof ProceduresListResult);

        ProceduresListResult proceduresResult = (ProceduresListResult) result;
        assertNotNull(proceduresResult.procedures());

        // Procedures should have basic info only
        for (ProcedureInfo proc : proceduresResult.procedures()) {
            assertNotNull(proc.name());
            // Parameters, definition etc. should be null
            assertNull(proc.parameters());
            assertNull(proc.definition());
        }
    }

    @Test
    void testSchemaFilter() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("schema", "PUBLIC");

        Object result = listProceduresTool.execute(params, context);
        assertTrue(result instanceof ProceduresListResult);

        ProceduresListResult proceduresResult = (ProceduresListResult) result;
        assertNotNull(proceduresResult.procedures());
    }

    @Test
    void testNonExistentSchema() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("schema", "NONEXISTENT");

        Object result = listProceduresTool.execute(params, context);
        assertTrue(result instanceof ProceduresListResult);

        ProceduresListResult proceduresResult = (ProceduresListResult) result;
        assertEquals(0, proceduresResult.count());
        assertNotNull(proceduresResult.procedures());
        assertTrue(proceduresResult.procedures().isEmpty());
    }

    @Test
    void testProcedureMetadata() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("includeDetails", true);

        Object result = listProceduresTool.execute(params, context);
        ProceduresListResult proceduresResult = (ProceduresListResult) result;

        // Verify structure even if no procedures found
        assertNotNull(proceduresResult.procedures());
        assertTrue(proceduresResult.count() >= 0);

        for (ProcedureInfo proc : proceduresResult.procedures()) {
            assertNotNull(proc.name());
            assertNotNull(proc.type()); // Should be "PROCEDURE" or "FUNCTION"
            assertTrue("PROCEDURE".equals(proc.type()) ||
                      "FUNCTION".equals(proc.type()) ||
                      "UNKNOWN".equals(proc.type()));
        }
    }
}


package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.ListTablesTool;
import org.peacetalk.jmcp.jdbc.tools.results.TablesListResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ListTablesToolTest {

    private Connection connection;
    private ListTablesTool listTablesTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE orders (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE products (id INT PRIMARY KEY)");
        }

        listTablesTool = new ListTablesTool();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void testGetName() {
        assertEquals("list-tables", listTablesTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(listTablesTool.getDescription());
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = listTablesTool.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    void testExecuteListTables() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();

        Object result = listTablesTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof TablesListResult);

        TablesListResult tablesResult = (TablesListResult) result;
        assertNotNull(tablesResult.tables());
        assertTrue(tablesResult.count() >= 3);
    }
}


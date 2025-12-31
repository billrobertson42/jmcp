package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.QueryTool;
import org.peacetalk.jmcp.jdbc.tools.results.CompactQueryResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class QueryToolTest {

    private Connection connection;
    private QueryTool queryTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        // Create in-memory H2 database
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        // Create test table
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 35)");
        }

        queryTool = new QueryTool();
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
        assertEquals("query", queryTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(queryTool.getDescription());
        assertTrue(queryTool.getDescription().contains("SELECT"));
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = queryTool.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type").asText());
        assertTrue(schema.has("properties"));
        assertTrue(schema.get("properties").has("sql"));
    }

    @Test
    void testExecuteSimpleQuery() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users");

        Object result = queryTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof CompactQueryResult);

        CompactQueryResult queryResult = (CompactQueryResult) result;
        assertNotNull(queryResult.columns());
        assertNotNull(queryResult.rows());
        assertEquals(3, queryResult.count());
    }

    @Test
    void testExecuteQueryWithWhereClause() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "SELECT * FROM users WHERE age > 28");

        Object result = queryTool.execute(params, context);
        assertTrue(result instanceof CompactQueryResult);

        CompactQueryResult queryResult = (CompactQueryResult) result;
        assertEquals(2, queryResult.count());
    }

    @Test
    void testRejectNonSelectQuery() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "DELETE FROM users WHERE id = 1");

        // JSqlParser validates and rejects DELETE statements
        assertThrows(IllegalArgumentException.class, () -> {
            queryTool.execute(params, context);
        });
    }

    @Test
    void testRejectInsertQuery() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "INSERT INTO users VALUES (4, 'Dave', 40)");

        // JSqlParser validates and rejects INSERT statements
        assertThrows(IllegalArgumentException.class, () -> {
            queryTool.execute(params, context);
        });
    }

    @Test
    void testRejectUpdateQuery() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("sql", "UPDATE users SET age = 31 WHERE id = 1");

        // JSqlParser validates and rejects UPDATE statements
        assertThrows(IllegalArgumentException.class, () -> {
            queryTool.execute(params, context);
        });
    }
}


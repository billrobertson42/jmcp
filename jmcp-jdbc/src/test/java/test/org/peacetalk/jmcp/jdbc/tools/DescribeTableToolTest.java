package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.tools.DescribeTableTool;
import org.peacetalk.jmcp.jdbc.tools.results.TableDescription;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DescribeTableToolTest {

    private Connection connection;
    private DescribeTableTool describeTableTool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:describeTableTest", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "email VARCHAR(255), " +
                "age INT DEFAULT 0" +
                ")");
            stmt.execute("CREATE INDEX idx_users_email ON users(email)");
        }

        describeTableTool = new DescribeTableTool();
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
        assertEquals("describe-table", describeTableTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(describeTableTool.getDescription());
    }

    @Test
    void testGetInputSchema() {
        JsonNode schema = describeTableTool.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.has("properties"));
        assertTrue(schema.get("properties").has("table"));
        assertTrue(schema.has("required"));
    }

    @Test
    void testExecuteDescribeTable() throws Exception {
        ConnectionContext context = () -> connection;

        ObjectNode params = mapper.createObjectNode();
        params.put("table", "USERS");

        Object result = describeTableTool.execute(params, context);
        assertNotNull(result);
        assertTrue(result instanceof TableDescription);

        TableDescription tableDesc = (TableDescription) result;
        assertEquals("USERS", tableDesc.table());
        assertNotNull(tableDesc.columns());
        assertNotNull(tableDesc.primaryKeys());
        assertNotNull(tableDesc.indexes());

        // Should have at least our 4 columns (id, name, email, age)
        // H2 or other databases might include system columns
        assertTrue(tableDesc.columns().size() >= 4,
            "Expected at least 4 columns, but got " + tableDesc.columns().size());

        // Should have 1 primary key (id)
        assertTrue(tableDesc.primaryKeys().size() >= 1);
    }
}


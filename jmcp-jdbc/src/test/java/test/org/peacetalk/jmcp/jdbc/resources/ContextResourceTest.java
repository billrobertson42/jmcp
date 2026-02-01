package test.org.peacetalk.jmcp.jdbc.resources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.resources.ContextResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContextResourceTest {

    private Connection connection;
    private ConnectionManager connectionManager;
    private ContextResource contextResource;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE test_table (id INT, name VARCHAR(100))");
            stmt.execute("INSERT INTO test_table VALUES (1, 'Test')");
        }

        JdbcDriverManager driverManager = new JdbcDriverManager(Path.of("/tmp"));
        connectionManager = new ConnectionManager(driverManager);
        connectionManager.registerConnection("test", "h2",
                "jdbc:h2:mem:test", "sa", "");

        contextResource = new ContextResource(connectionManager);
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (connectionManager != null) {
            connectionManager.closeAll();
        }
    }

    @Test
    void testGetUri() {
        String uri = contextResource.getUri();
        assertNotNull(uri);
        assertTrue(uri.contains("context"));
    }

    @Test
    void testGetName() {
        String name = contextResource.getName();
        assertNotNull(name);
        assertFalse(name.isBlank());
    }

    @Test
    void testGetDescription() {
        String description = contextResource.getDescription();
        assertNotNull(description);
        assertFalse(description.isBlank());
    }

    @Test
    void testGetMimeType() {
        String mimeType = contextResource.getMimeType();
        assertNotNull(mimeType);
        assertTrue(mimeType.contains("json"));
    }

    @Test
    void testReadReturnsValidJson() throws Exception {
        String json = contextResource.read();
        assertNotNull(json);
        assertFalse(json.isBlank());

        // Should be valid JSON
        Map response = mapper.readValue(json, Map.class);
        assertNotNull(response);
    }

    @Test
    void testReadContainsConnections() throws Exception {
        String json = contextResource.read();

        Map response = mapper.readValue(json, Map.class);
        assertTrue(response.containsKey("connections"),
            "Response should contain connections field");
    }

    @Test
    void testReadContainsTools() throws Exception {
        String json = contextResource.read();

        Map response = mapper.readValue(json, Map.class);
        assertTrue(response.containsKey("availableTools"),
            "Response should contain availableTools field");
    }

    @Test
    void testReadContainsResources() throws Exception {
        String json = contextResource.read();

        Map response = mapper.readValue(json, Map.class);
        assertTrue(response.containsKey("resources"),
            "Response should contain resources field");
    }

    @Test
    void testConnectionListingInContext() throws Exception {
        connectionManager.registerConnection("test2", "h2",
                "jdbc:h2:mem:test2", "sa", "");

        String json = contextResource.read();
        Map response = mapper.readValue(json, Map.class);

        java.util.List connections = (java.util.List) response.get("connections");
        assertEquals(2, connections.size(),
            "Should list both registered connections");
    }

    @Test
    void testMultipleReads() throws Exception {
        String json1 = contextResource.read();
        String json2 = contextResource.read();

        assertNotNull(json1);
        assertNotNull(json2);
        // Both should be valid JSON
        mapper.readValue(json1, Map.class);
        mapper.readValue(json2, Map.class);
    }
}


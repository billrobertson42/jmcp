package test.org.peacetalk.jmcp.jdbc.resources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.resources.TablesListResource;
import org.peacetalk.jmcp.jdbc.resources.ViewsListResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TablesListResourceTest {

    private Connection connection;
    private ConnectionManager connectionManager;
    private TablesListResource tablesListResource;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA TEST_SCHEMA");
            stmt.execute("CREATE TABLE TEST_SCHEMA.TABLE1 (id INT, name VARCHAR(100))");
            stmt.execute("CREATE TABLE TEST_SCHEMA.TABLE2 (id INT, \"VALUE\" INT)");
        }

        JdbcDriverManager driverManager = new JdbcDriverManager(Path.of("/tmp"));
        connectionManager = new ConnectionManager(driverManager);
        connectionManager.registerConnection("test", "h2",
                "jdbc:h2:mem:test", "sa", "");

        tablesListResource = new TablesListResource("test", "TEST_SCHEMA", connectionManager);
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (connectionManager != null) {
            connectionManager.closeAll();
        }
    }

    @Test
    void testGetUri() {
        String uri = tablesListResource.getUri();
        assertNotNull(uri);
        assertTrue(uri.contains("tables"));
    }

    @Test
    void testGetName() {
        String name = tablesListResource.getName();
        assertNotNull(name);
        assertFalse(name.isBlank());
    }

    @Test
    void testGetDescription() {
        String description = tablesListResource.getDescription();
        assertNotNull(description);
        assertFalse(description.isBlank());
    }

    @Test
    void testGetMimeType() {
        String mimeType = tablesListResource.getMimeType();
        assertNotNull(mimeType);
        assertTrue(mimeType.contains("json"));
    }

    @Test
    void testReadReturnsValidJson() throws Exception {
        String json = tablesListResource.read();
        assertNotNull(json);
        assertFalse(json.isBlank());

        // Should be valid JSON
        var response = mapper.readValue(json, Map.class);
        assertNotNull(response);
    }

    @Test
    void testReadContainsTables() throws Exception {
        String json = tablesListResource.read();

        var response = mapper.readValue(json, Map.class);
        assertTrue(response.containsKey("tables"),
            "Response should contain tables field");
    }

    @Test
    void testTableListingCount() throws Exception {
        String json = tablesListResource.read();
        var response = mapper.readValue(json, Map.class);

        var tables = (java.util.List<?>) response.get("tables");
        assertEquals(2, tables.size(), "Should list both tables");
    }

    @Test
    void testEmptySchema() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA EMPTY_SCHEMA");
        }

        TablesListResource emptyResource = new TablesListResource("test", "EMPTY_SCHEMA", connectionManager);
        String json = emptyResource.read();

        var response = mapper.readValue(json, Map.class);
        var tables = (java.util.List<?>) response.get("tables");
        assertEquals(0, tables.size(), "Empty schema should have no tables");
    }
}

class ViewsListResourceTest {

    private Connection connection;
    private ConnectionManager connectionManager;
    private ViewsListResource viewsListResource;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA TEST_SCHEMA");
            stmt.execute("CREATE TABLE TEST_SCHEMA.BASE_TABLE (id INT, name VARCHAR(100))");
            stmt.execute("CREATE VIEW TEST_SCHEMA.VIEW1 AS SELECT id FROM TEST_SCHEMA.BASE_TABLE");
            stmt.execute("CREATE VIEW TEST_SCHEMA.VIEW2 AS SELECT name FROM TEST_SCHEMA.BASE_TABLE");
        }

        JdbcDriverManager driverManager = new JdbcDriverManager(Path.of("/tmp"));
        connectionManager = new ConnectionManager(driverManager);
        connectionManager.registerConnection("test", "h2",
                "jdbc:h2:mem:test", "sa", "");

        viewsListResource = new ViewsListResource("test", "TEST_SCHEMA", connectionManager);
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (connectionManager != null) {
            connectionManager.closeAll();
        }
    }

    @Test
    void testGetUri() {
        String uri = viewsListResource.getUri();
        assertNotNull(uri);
        assertTrue(uri.contains("views"));
    }

    @Test
    void testGetName() {
        String name = viewsListResource.getName();
        assertNotNull(name);
        assertFalse(name.isBlank());
    }

    @Test
    void testGetDescription() {
        String description = viewsListResource.getDescription();
        assertNotNull(description);
        assertFalse(description.isBlank());
    }

    @Test
    void testGetMimeType() {
        String mimeType = viewsListResource.getMimeType();
        assertNotNull(mimeType);
        assertTrue(mimeType.contains("json"));
    }

    @Test
    void testReadReturnsValidJson() throws Exception {
        String json = viewsListResource.read();
        assertNotNull(json);
        assertFalse(json.isBlank());

        // Should be valid JSON
        var response = mapper.readValue(json, Map.class);
        assertNotNull(response);
    }

    @Test
    void testReadContainsViews() throws Exception {
        String json = viewsListResource.read();

        var response = mapper.readValue(json, Map.class);
        assertTrue(response.containsKey("views"),
            "Response should contain views field");
    }

    @Test
    void testViewListingCount() throws Exception {
        String json = viewsListResource.read();
        var response = mapper.readValue(json, Map.class);

        var views = (java.util.List<?>) response.get("views");
        assertEquals(2, views.size(), "Should list both views");
    }

    @Test
    void testEmptySchema() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA empty_schema");
        }

        ViewsListResource emptyResource = new ViewsListResource("test", "empty_schema", connectionManager);
        String json = emptyResource.read();

        var response = mapper.readValue(json, Map.class);
        var views = (java.util.List<?>) response.get("views");
        assertEquals(0, views.size(), "Empty schema should have no views");
    }
}


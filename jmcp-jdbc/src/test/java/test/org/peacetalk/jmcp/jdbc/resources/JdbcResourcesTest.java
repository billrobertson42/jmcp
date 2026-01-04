package test.org.peacetalk.jmcp.jdbc.resources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.resources.*;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for JDBC resource classes.
 */
class JdbcResourcesTest {

    private Connection connection;
    private ConnectionManager mockConnectionManager;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        // Create in-memory H2 database
        connection = DriverManager.getConnection("jdbc:h2:mem:resourcetest;DB_CLOSE_DELAY=-1", "sa", "");

        // Create test schema and tables
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
            stmt.execute("CREATE TABLE IF NOT EXISTS test_schema.users (id INT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("CREATE TABLE IF NOT EXISTS test_schema.orders (id INT PRIMARY KEY, user_id INT, total DECIMAL(10,2))");
            stmt.execute("ALTER TABLE test_schema.orders ADD FOREIGN KEY (user_id) REFERENCES test_schema.users(id)");
            stmt.execute("CREATE VIEW IF NOT EXISTS test_schema.user_orders AS SELECT u.name, o.total FROM test_schema.users u JOIN test_schema.orders o ON u.id = o.user_id");
        }

        // Setup mock connection manager
        mockConnectionManager = mock(ConnectionManager.class);
        ConnectionContext mockContext = mock(ConnectionContext.class);
        when(mockContext.getConnection()).thenReturn(connection);
        when(mockConnectionManager.getContext("testdb")).thenReturn(mockContext);
        when(mockConnectionManager.listConnections()).thenReturn(List.of(
            new ConnectionInfo("testdb", "jdbc:h2:mem:resourcetest", "sa", "h2")
        ));

        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP VIEW IF EXISTS test_schema.user_orders");
                stmt.execute("DROP TABLE IF EXISTS test_schema.orders");
                stmt.execute("DROP TABLE IF EXISTS test_schema.users");
                stmt.execute("DROP SCHEMA IF EXISTS test_schema");
            }
            connection.close();
        }
    }

    // ConnectionsListResource tests

    @Test
    void testConnectionsListResourceMetadata() {
        ConnectionsListResource resource = new ConnectionsListResource(mockConnectionManager);

        assertEquals("db://connections", resource.getUri());
        assertEquals("Database Connections", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testConnectionsListResourceRead() throws Exception {
        ConnectionsListResource resource = new ConnectionsListResource(mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("connections"));
        assertTrue(json.has("count"));
        assertEquals(1, json.get("count").asInt());

        JsonNode firstConnection = json.get("connections").get(0);
        assertEquals("testdb", firstConnection.get("id").asString());
        assertEquals("h2", firstConnection.get("databaseType").asString());
        assertTrue(firstConnection.has("resourceUri"));
        assertTrue(firstConnection.has("schemasUri"));
    }

    // ConnectionResource tests

    @Test
    void testConnectionResourceMetadata() {
        ConnectionResource resource = new ConnectionResource("testdb", mockConnectionManager);

        assertEquals("db://connection/testdb", resource.getUri());
        assertEquals("Connection: testdb", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testConnectionResourceRead() throws Exception {
        ConnectionResource resource = new ConnectionResource("testdb", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertEquals("testdb", json.get("id").asString());
        assertTrue(json.has("database"));
        assertTrue(json.has("links"));

        JsonNode database = json.get("database");
        assertNotNull(database.get("productName"));
        assertNotNull(database.get("productVersion"));
    }

    // SchemasListResource tests

    @Test
    void testSchemasListResourceMetadata() {
        SchemasListResource resource = new SchemasListResource("testdb", mockConnectionManager);

        assertEquals("db://connection/testdb/schemas", resource.getUri());
        assertEquals("Schemas in testdb", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testSchemasListResourceRead() throws Exception {
        SchemasListResource resource = new SchemasListResource("testdb", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("schemas"));
        assertTrue(json.has("count"));
        assertTrue(json.get("count").asInt() > 0);
        assertTrue(json.has("links"));
    }

    // SchemaResource tests

    @Test
    void testSchemaResourceMetadata() {
        SchemaResource resource = new SchemaResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA", resource.getUri());
        assertEquals("Schema: TEST_SCHEMA", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testSchemaResourceRead() throws Exception {
        SchemaResource resource = new SchemaResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertEquals("TEST_SCHEMA", json.get("name").asString());
        assertTrue(json.has("tableCount"));
        assertTrue(json.has("viewCount"));
        assertTrue(json.has("links"));

        // We created 2 tables (users, orders) and 1 view
        assertEquals(2, json.get("tableCount").asInt());
        assertEquals(1, json.get("viewCount").asInt());
    }

    // TablesListResource tests

    @Test
    void testTablesListResourceMetadata() {
        TablesListResource resource = new TablesListResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/tables", resource.getUri());
        assertEquals("Tables in TEST_SCHEMA", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testTablesListResourceRead() throws Exception {
        TablesListResource resource = new TablesListResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("tables"));
        assertTrue(json.has("count"));
        assertEquals(2, json.get("count").asInt());
        assertTrue(json.has("links"));
    }

    // TableResource tests

    @Test
    void testTableResourceMetadata() {
        TableResource resource = new TableResource("testdb", "TEST_SCHEMA", "USERS", mockConnectionManager);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/table/USERS", resource.getUri());
        assertEquals("Table: USERS", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testTableResourceRead() throws Exception {
        TableResource resource = new TableResource("testdb", "TEST_SCHEMA", "USERS", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertEquals("USERS", json.get("name").asString());
        assertTrue(json.has("columns"));
        assertTrue(json.has("primaryKey"));
        assertTrue(json.has("indexes"));
        assertTrue(json.has("foreignKeys"));
        assertTrue(json.has("links"));

        // Check columns
        JsonNode columns = json.get("columns");
        assertEquals(2, columns.size()); // id and name columns
    }

    @Test
    void testTableResourceWithForeignKey() throws Exception {
        TableResource resource = new TableResource("testdb", "TEST_SCHEMA", "ORDERS", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("foreignKeys"));

        JsonNode foreignKeys = json.get("foreignKeys");
        assertFalse(foreignKeys.isEmpty());

        JsonNode fk = foreignKeys.get(0);
        assertEquals("USERS", fk.get("referencedTable").asString());
        assertTrue(fk.has("referencedTableUri"));
    }

    // ViewsListResource tests

    @Test
    void testViewsListResourceMetadata() {
        ViewsListResource resource = new ViewsListResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/views", resource.getUri());
        assertEquals("Views in TEST_SCHEMA", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testViewsListResourceRead() throws Exception {
        ViewsListResource resource = new ViewsListResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("views"));
        assertTrue(json.has("count"));
        assertEquals(1, json.get("count").asInt());
        assertTrue(json.has("links"));
    }

    // ViewResource tests

    @Test
    void testViewResourceMetadata() {
        ViewResource resource = new ViewResource("testdb", "TEST_SCHEMA", "USER_ORDERS", mockConnectionManager, mapper);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/view/USER_ORDERS", resource.getUri());
        assertEquals("View: USER_ORDERS", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testViewResourceRead() throws Exception {
        ViewResource resource = new ViewResource("testdb", "TEST_SCHEMA", "USER_ORDERS", mockConnectionManager, mapper);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertEquals("USER_ORDERS", json.get("name").asString());
        assertTrue(json.has("columns"));
        assertTrue(json.has("links"));

        // Check columns (name and total from the view)
        JsonNode columns = json.get("columns");
        assertEquals(2, columns.size());
    }
}


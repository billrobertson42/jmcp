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
        assertTrue(json.has("tables"));
        assertTrue(json.has("views"));
        assertTrue(json.has("links"));

        // We created 2 tables (users, orders) and 1 view
        JsonNode tables = json.get("tables");
        assertEquals(2, tables.size());
        // Check that tables have name and uri fields
        assertTrue(tables.get(0).has("name"));
        assertTrue(tables.get(0).has("uri"));

        JsonNode views = json.get("views");
        assertEquals(1, views.size());
        // Check that views have name and uri fields
        assertTrue(views.get(0).has("name"));
        assertTrue(views.get(0).has("uri"));
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

        // Verify parent link points to schema (not deleted tables list)
        JsonNode links = json.get("links");
        assertTrue(links.has("parent"));
        assertEquals("db://connection/testdb/schema/TEST_SCHEMA", links.get("parent").asString());
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

    @Test
    void testTableResourceIncludesReverseForeignKeys() throws Exception {
        // Test that users table (which is referenced by orders) includes reverse foreign keys
        TableResource resource = new TableResource("testdb", "TEST_SCHEMA", "USERS", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("reverseForeignKeys"));

        JsonNode reverseFks = json.get("reverseForeignKeys");
        assertFalse(reverseFks.isEmpty());

        // Orders table references Users, so Users should have a reverse FK
        JsonNode reverseFk = reverseFks.get(0);
        assertEquals("ORDERS", reverseFk.get("referencingTable").asString());
        assertTrue(reverseFk.has("referencingTableUri"));
    }

    // RelationshipsResource tests

    @Test
    void testRelationshipsResourceMetadata() {
        RelationshipsResource resource = new RelationshipsResource("testdb", mockConnectionManager);

        assertEquals("db://connection/testdb/relationships", resource.getUri());
        assertEquals("Relationships for connection: testdb", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testRelationshipsResourceRead() throws Exception {
        RelationshipsResource resource = new RelationshipsResource("testdb", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("connectionId"));
        assertEquals("testdb", json.get("connectionId").asString());
        assertTrue(json.has("relationships"));
        assertTrue(json.has("links"));

        JsonNode relationships = json.get("relationships");
        assertFalse(relationships.isEmpty());

        // Should have at least the orders -> users FK
        boolean foundOrdersUsersFK = false;
        for (int i = 0; i < relationships.size(); i++) {
            JsonNode rel = relationships.get(i);
            if ("ORDERS".equals(rel.get("fromTable").asString()) &&
                "USERS".equals(rel.get("toTable").asString())) {
                foundOrdersUsersFK = true;
                assertTrue(rel.has("columns"));
                assertTrue(rel.has("fromTableUri"));
                assertTrue(rel.has("toTableUri"));
            }
        }
        assertTrue(foundOrdersUsersFK, "Should find ORDERS -> USERS relationship");
    }

    // SchemaRelationshipsResource tests

    @Test
    void testSchemaRelationshipsResourceMetadata() {
        SchemaRelationshipsResource resource = new SchemaRelationshipsResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/relationships", resource.getUri());
        assertEquals("Relationships for schema: TEST_SCHEMA", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testSchemaRelationshipsResourceRead() throws Exception {
        SchemaRelationshipsResource resource = new SchemaRelationshipsResource("testdb", "TEST_SCHEMA", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        JsonNode json = mapper.readTree(result);
        assertTrue(json.has("connectionId"));
        assertEquals("testdb", json.get("connectionId").asString());
        assertTrue(json.has("schema"));
        assertEquals("TEST_SCHEMA", json.get("schema").asString());
        assertTrue(json.has("relationships"));
        assertTrue(json.has("links"));

        JsonNode relationships = json.get("relationships");
        assertFalse(relationships.isEmpty());

        // Should have the orders -> users FK (both in TEST_SCHEMA)
        boolean foundOrdersUsersFK = false;
        for (int i = 0; i < relationships.size(); i++) {
            JsonNode rel = relationships.get(i);
            if ("ORDERS".equals(rel.get("fromTable").asString()) &&
                "USERS".equals(rel.get("toTable").asString())) {
                foundOrdersUsersFK = true;
                assertEquals("TEST_SCHEMA", rel.get("fromSchema").asString());
                assertEquals("TEST_SCHEMA", rel.get("toSchema").asString());
                assertTrue(rel.has("columns"));
                assertTrue(rel.has("fromTableUri"));
                assertTrue(rel.has("toTableUri"));
            }
        }
        assertTrue(foundOrdersUsersFK, "Should find ORDERS -> USERS relationship in TEST_SCHEMA");
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
        assertTrue(json.has("viewDefinition"));
        assertTrue(json.has("links"));

        // View definition should be present (may be null if DB doesn't support it)
        // For H2, it should retrieve the definition
        JsonNode viewDef = json.get("viewDefinition");
        assertNotNull(viewDef); // Field should exist even if null

        // Check columns (name and total from the view)
        JsonNode columns = json.get("columns");
        assertEquals(2, columns.size());

        // Verify parent link points to schema (not deleted views list)
        JsonNode links = json.get("links");
        assertTrue(links.has("parent"));
        assertEquals("db://connection/testdb/schema/TEST_SCHEMA", links.get("parent").asString());
    }
}


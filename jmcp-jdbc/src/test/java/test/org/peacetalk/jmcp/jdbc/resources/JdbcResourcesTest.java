/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.org.peacetalk.jmcp.jdbc.resources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionSupplier;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.resources.*;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
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

            // Create a test procedure/function
            stmt.execute("DROP ALIAS IF EXISTS test_schema.calculate_tax");
            stmt.execute("""
                CREATE ALIAS test_schema.calculate_tax AS $$
                    double calculateTax(double amount, double rate) {
                        return amount * rate;
                    }
                $$
            """);
        }

        // Setup mock connection manager
        mockConnectionManager = mock(ConnectionManager.class);
        ConnectionContext mockContext = mock(ConnectionContext.class);
        when(mockContext.getConnection()).thenReturn(connection);
        when(mockContext.isSchemaVisible(anyString())).thenReturn(true);
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
                stmt.execute("DROP ALIAS IF EXISTS test_schema.calculate_tax");
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

        assertThatJson(result).and(
            j -> j.node("connections[0].id").isEqualTo("testdb"),
            j -> j.node("connections[0].databaseType").isEqualTo("h2"),
            j -> j.node("connections[0].resourceUri").isPresent(),
            j -> j.node("connections[0].schemasUri").isPresent()
        );
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

        assertThatJson(result).and(
            j -> j.node("id").isEqualTo("testdb"),
            j -> j.node("links").isPresent(),
            j -> j.node("database.productName").isPresent(),
            j -> j.node("database.productVersion").isPresent()
        );
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
        assertTrue(json.has("links"));
        assertTrue(json.get("schemas").isArray());

        // With all schemas visible, TEST_SCHEMA should be listed with its navigation URIs.
        JsonNode testSchema = findSchemaEntry(json.get("schemas"), "TEST_SCHEMA");
        assertNotNull(testSchema, "TEST_SCHEMA should appear in the schema listing");
        assertEquals("db://connection/testdb/schema/TEST_SCHEMA",
            testSchema.get("resourceUri").asString());
        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/tables",
            testSchema.get("tablesUri").asString());

        // Parent link points back at the connection.
        assertEquals("db://connection/testdb", json.get("links").get("parent").asString());
    }

    @Test
    void testSchemasListResourceExcludesFilteredSchema() throws Exception {
        // Reconfigure the shared mock context so TEST_SCHEMA is hidden by the schema filter.
        ConnectionContext filteredContext = mock(ConnectionContext.class);
        when(filteredContext.getConnection()).thenReturn(connection);
        when(filteredContext.isSchemaVisible(anyString())).thenReturn(true);
        when(filteredContext.isSchemaVisible("TEST_SCHEMA")).thenReturn(false);
        when(mockConnectionManager.getContext("testdb")).thenReturn(filteredContext);

        SchemasListResource resource = new SchemasListResource("testdb", mockConnectionManager);
        JsonNode json = mapper.readTree(resource.read());

        assertNull(findSchemaEntry(json.get("schemas"), "TEST_SCHEMA"),
            "A schema hidden by isSchemaVisible must be absent from the listing");
        // Other schemas remain visible, so exclusion is targeted rather than emptying the list.
        assertNotNull(findSchemaEntry(json.get("schemas"), "PUBLIC"),
            "Schemas still allowed by the filter should remain visible");
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

        // We created 2 tables (users, orders) and 1 view.
        assertThatJson(result).and(
            j -> j.node("name").isEqualTo("TEST_SCHEMA"),
            j -> j.node("links").isPresent(),
            j -> j.node("tables").isArray().hasSize(2),
            j -> j.node("tables[0].name").isPresent(),
            j -> j.node("tables[0].uri").isPresent(),
            j -> j.node("views").isArray().hasSize(1),
            j -> j.node("views[0].name").isPresent(),
            j -> j.node("views[0].uri").isPresent(),
            j -> j.node("procedures").isArray()
        );

        // H2 may or may not report procedures; when present, verify their shape.
        JsonNode procedures = mapper.readTree(result).get("procedures");
        if (procedures.size() > 0) {
            assertThatJson(mapper.writeValueAsString(procedures.get(0))).and(
                j -> j.node("name").isPresent(),
                j -> j.node("type").isPresent(),
                j -> j.node("uri").isPresent()
            );
        }
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

        assertThatJson(result).and(
            j -> j.node("name").isEqualTo("USERS"),
            j -> j.node("primaryKey").isPresent(),
            j -> j.node("indexes").isPresent(),
            j -> j.node("foreignKeys").isPresent(),
            // id and name columns.
            j -> j.node("columns").isArray().hasSize(2),
            // Verify parent link points to schema (not deleted tables list).
            j -> j.node("links.parent").isEqualTo("db://connection/testdb/schema/TEST_SCHEMA")
        );
    }

    @Test
    void testTableResourceWithForeignKey() throws Exception {
        TableResource resource = new TableResource("testdb", "TEST_SCHEMA", "ORDERS", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        assertThatJson(result).and(
            j -> j.node("foreignKeys").isArray().isNotEmpty(),
            j -> j.node("foreignKeys[0].referencedTable").isEqualTo("USERS"),
            j -> j.node("foreignKeys[0].referencedTableUri").isPresent(),
            // Referential action fields must be present (their value may be null).
            j -> j.node("foreignKeys[0].onDelete").isPresent(),
            j -> j.node("foreignKeys[0].onUpdate").isPresent()
        );
    }

    @Test
    void testTableResourceIncludesReverseForeignKeys() throws Exception {
        // Test that users table (which is referenced by orders) includes reverse foreign keys
        TableResource resource = new TableResource("testdb", "TEST_SCHEMA", "USERS", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        // Orders table references Users, so Users should have a reverse FK.
        assertThatJson(result).and(
            j -> j.node("reverseForeignKeys").isArray().isNotEmpty(),
            j -> j.node("reverseForeignKeys[0].referencingTable").isEqualTo("ORDERS"),
            j -> j.node("reverseForeignKeys[0].referencingTableUri").isPresent(),
            // Referential action fields must be present (their value may be null).
            j -> j.node("reverseForeignKeys[0].onDelete").isPresent(),
            j -> j.node("reverseForeignKeys[0].onUpdate").isPresent()
        );
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

        // Should have at least the orders -> users FK.
        assertThatJson(result).and(
            j -> j.node("connectionId").isEqualTo("testdb"),
            j -> j.node("links").isPresent(),
            j -> j.node("relationships").isArray()
                .isNotEmpty()
                .describedAs("Should find ORDERS -> USERS relationship")
                .anySatisfy(rel -> assertThatJson(rel).and(
                    r -> r.node("fromTable").isEqualTo("ORDERS"),
                    r -> r.node("toTable").isEqualTo("USERS"),
                    r -> r.node("columns").isPresent(),
                    r -> r.node("fromTableUri").isPresent(),
                    r -> r.node("toTableUri").isPresent()
                ))
        );
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

        // Should have the orders -> users FK (both in TEST_SCHEMA).
        assertThatJson(result).and(
            j -> j.node("connectionId").isEqualTo("testdb"),
            j -> j.node("schema").isEqualTo("TEST_SCHEMA"),
            j -> j.node("links").isPresent(),
            j -> j.node("relationships").isArray()
                .isNotEmpty()
                .describedAs("Should find ORDERS -> USERS relationship in TEST_SCHEMA")
                .anySatisfy(rel -> assertThatJson(rel).and(
                    r -> r.node("fromTable").isEqualTo("ORDERS"),
                    r -> r.node("toTable").isEqualTo("USERS"),
                    r -> r.node("fromSchema").isEqualTo("TEST_SCHEMA"),
                    r -> r.node("toSchema").isEqualTo("TEST_SCHEMA"),
                    r -> r.node("columns").isPresent(),
                    r -> r.node("fromTableUri").isPresent(),
                    r -> r.node("toTableUri").isPresent()
                ))
        );
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

        // viewDefinition field should exist even if null (may be null if the DB doesn't
        // support retrieving it; for H2 it should retrieve the actual definition).
        // Columns: name and total from the view.
        assertThatJson(result).and(
            j -> j.node("name").isEqualTo("USER_ORDERS"),
            j -> j.node("viewDefinition").isPresent(),
            j -> j.node("columns").isArray().hasSize(2),
            // Verify parent link points to schema (not deleted views list).
            j -> j.node("links.parent").isEqualTo("db://connection/testdb/schema/TEST_SCHEMA")
        );
    }

    // ProcedureResource tests

    @Test
    void testProcedureResourceMetadata() {
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/procedure/CALCULATE_TAX", resource.getUri());
        assertEquals("Procedure: CALCULATE_TAX", resource.getName());
        assertNotNull(resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testProcedureResourceRead() throws Exception {
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        String result = resource.read();
        assertNotNull(result);

        assertThatJson(result).and(
            j -> j.node("name").isEqualTo("CALCULATE_TAX"),
            j -> j.node("schema").isEqualTo("TEST_SCHEMA"),
            j -> j.node("type").isPresent(),
            j -> j.node("links.parent").isEqualTo("db://connection/testdb/schema/TEST_SCHEMA")
        );
    }

    /**
     * Finds the entry in a schemas array whose "name" field equals the given
     * schema name, or returns null if no such entry is present.
     */
    private JsonNode findSchemaEntry(JsonNode schemasArray, String schemaName) {
        for (int i = 0; i < schemasArray.size(); i++) {
            JsonNode entry = schemasArray.get(i);
            JsonNode nameNode = entry.get("name");
            if (nameNode != null && schemaName.equals(nameNode.asString())) {
                return entry;
            }
        }
        return null;
    }
}




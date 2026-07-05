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
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.config.ConnectionConfig;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverClassManager;
import org.peacetalk.jmcp.jdbc.resources.ContextResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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

        JdbcDriverClassManager driverManager = new JdbcDriverClassManager(Path.of("/tmp"));
        connectionManager = new ConnectionManager(driverManager);
        connectionManager.registerConnection(ConnectionConfig.basic("test", "h2",
                "jdbc:h2:mem:test", "sa", ""));

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
        connectionManager.registerConnection(ConnectionConfig.basic("test2", "h2",
                "jdbc:h2:mem:test2", "sa", ""));

        String json = contextResource.read();
        Map response = mapper.readValue(json, Map.class);

        java.util.List connections = (java.util.List) response.get("connections");
        assertEquals(2, connections.size(),
            "Should list both registered connections");
    }

    @Test
    void testReadReportsConnectionIdentity() throws Exception {
        JsonNode root = mapper.readTree(contextResource.read());

        JsonNode connections = root.get("connections");
        assertEquals(1, connections.size(), "Only the 'test' connection is registered");

        JsonNode conn = connections.get(0);
        assertEquals("test", conn.get("databaseId").asString());
        assertEquals("h2", conn.get("databaseType").asString());
    }

    @Test
    void testReadSchemaSummaryReflectsCreatedTable() throws Exception {
        // The default PUBLIC schema owns test_table; the context summary should surface it.
        JsonNode root = mapper.readTree(contextResource.read());

        JsonNode publicSchema = findSchema(root, "PUBLIC");
        assertNotNull(publicSchema, "PUBLIC schema should be present in the context summary");
        assertTrue(publicSchema.get("isDefault").asBoolean(), "PUBLIC is H2's default schema");
        assertTrue(publicSchema.get("tableCount").asInt() >= 1,
            "PUBLIC should report at least the one table we created");
        assertTrue(schemaContainsTable(publicSchema, "TEST_TABLE"),
            "PUBLIC schema's table list should include TEST_TABLE");
    }

    @Test
    void testSchemaFilterExcludesHiddenSchema() throws Exception {
        // Create two extra schemas in the shared in-memory database.
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS VISIBLE_SCHEMA");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS HIDDEN_SCHEMA");
        }

        // Register a second connection to the same DB whose schemaFilter omits HIDDEN_SCHEMA.
        connectionManager.registerConnection(new ConnectionConfig(
            "filtered", "h2", "jdbc:h2:mem:test", "sa", "",
            List.of("PUBLIC", "VISIBLE_SCHEMA")));

        JsonNode root = mapper.readTree(contextResource.read());

        JsonNode filteredConn = null;
        for (JsonNode conn : root.get("connections")) {
            if ("filtered".equals(conn.get("databaseId").asString())) {
                filteredConn = conn;
            }
        }
        assertNotNull(filteredConn, "The filtered connection should appear in the context summary");

        List<String> schemaNames = new ArrayList<>();
        for (JsonNode schema : filteredConn.get("schemas")) {
            schemaNames.add(schema.get("name").asString());
        }

        assertTrue(schemaNames.contains("VISIBLE_SCHEMA"),
            "Schema present in the filter should be visible: " + schemaNames);
        assertTrue(schemaNames.contains("PUBLIC"),
            "PUBLIC is in the filter and should be visible: " + schemaNames);
        assertFalse(schemaNames.contains("HIDDEN_SCHEMA"),
            "Schema omitted from the filter must be excluded: " + schemaNames);
        assertFalse(schemaNames.contains("INFORMATION_SCHEMA"),
            "INFORMATION_SCHEMA is not in the filter and must be excluded: " + schemaNames);
    }

    @Test
    void testMultipleReadsProduceIdenticalOutput() throws Exception {
        // read() must be side-effect free and deterministic for the same manager state.
        String json1 = contextResource.read();
        String json2 = contextResource.read();

        assertEquals(mapper.readTree(json1), mapper.readTree(json2),
            "Repeated reads of the same state should produce identical context");
    }

    /**
     * Finds the schema summary with the given name under the first (only) connection.
     */
    private JsonNode findSchema(JsonNode root, String schemaName) {
        for (JsonNode conn : root.get("connections")) {
            for (JsonNode schema : conn.get("schemas")) {
                if (schemaName.equals(schema.get("name").asString())) {
                    return schema;
                }
            }
        }
        return null;
    }

    private boolean schemaContainsTable(JsonNode schema, String tableName) {
        for (JsonNode t : schema.get("tables")) {
            if (tableName.equals(t.asString())) {
                return true;
            }
        }
        return false;
    }
}

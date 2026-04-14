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
import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.resources.JdbcResourceProvider;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import static org.peacetalk.jmcp.jdbc.resources.Util.SCHEME;

/**
 * Tests for JdbcResourceProvider URI routing and resource lookup.
 */
class JdbcResourceProviderTest {

    private Connection connection;
    private ConnectionManager mockConnectionManager;
    private JdbcResourceProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        // Create in-memory H2 database
        connection = DriverManager.getConnection("jdbc:h2:mem:providertest;DB_CLOSE_DELAY=-1", "sa", "");

        // Create test schema and tables
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
            stmt.execute("CREATE TABLE IF NOT EXISTS test_schema.users (id INT PRIMARY KEY)");
            stmt.execute("CREATE VIEW IF NOT EXISTS test_schema.user_view AS SELECT * FROM test_schema.users");
        }

        // Setup mock connection manager
        mockConnectionManager = mock(ConnectionManager.class);
        ConnectionContext mockContext = mock(ConnectionContext.class);
        when(mockContext.getConnection()).thenReturn(connection);
        when(mockConnectionManager.getContext("testdb")).thenReturn(mockContext);
        when(mockConnectionManager.listConnections()).thenReturn(List.of(
            new ConnectionInfo("testdb", "db:h2:mem:providertest", "sa", "h2")
        ));

        provider = new JdbcResourceProvider();
        provider.setConnectionManager(mockConnectionManager);
        provider.initialize();
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.shutdown();
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP VIEW IF EXISTS test_schema.user_view");
                stmt.execute("DROP TABLE IF EXISTS test_schema.users");
                stmt.execute("DROP SCHEMA IF EXISTS test_schema");
            }
            connection.close();
        }
    }

    @Test
    void testSupportsScheme() {
        assertTrue(provider.supportsScheme("db"));
        assertFalse(provider.supportsScheme("http"));
        assertFalse(provider.supportsScheme(null));
    }

    @Test
    void testGetName() {
        assertEquals("JDBC Database Resources", provider.getName());
    }

    @Test
    void testListResources() {
        List<Resource> resources = provider.listResources(null);
        assertNotNull(resources);
        assertFalse(resources.isEmpty());

        // Should include connections list resource and individual connection resources
        boolean hasConnectionsList = resources.stream()
            .anyMatch(r -> (SCHEME + "://connections").equals(r.getUri()));
        assertTrue(hasConnectionsList);
    }

    // URI routing tests

    @Test
    void testGetResourceConnections() {
        Resource resource = provider.getResource(SCHEME + "://connections");
        assertNotNull(resource);
        assertEquals(SCHEME + "://connections", resource.getUri());
    }

    @Test
    void testGetResourceConnection() {
        Resource resource = provider.getResource(SCHEME + "://connection/testdb");
        assertNotNull(resource);
        assertEquals(SCHEME + "://connection/testdb", resource.getUri());
    }

    @Test
    void testGetResourceSchemas() {
        Resource resource = provider.getResource(SCHEME + "://connection/testdb/schemas");
        assertNotNull(resource);
        assertEquals(SCHEME + "://connection/testdb/schemas", resource.getUri());
    }

    @Test
    void testGetResourceSchema() {
        Resource resource = provider.getResource(SCHEME + "://connection/testdb/schema/TEST_SCHEMA");
        assertNotNull(resource);
        assertEquals(SCHEME + "://connection/testdb/schema/TEST_SCHEMA", resource.getUri());
    }

    @Test
    void testGetResourceTable() {
        Resource resource = provider.getResource(SCHEME + "://connection/testdb/schema/TEST_SCHEMA/table/USERS");
        assertNotNull(resource);
        assertEquals(SCHEME + "://connection/testdb/schema/TEST_SCHEMA/table/USERS", resource.getUri());
    }

    @Test
    void testGetResourceView() {
        Resource resource = provider.getResource(SCHEME + "://connection/testdb/schema/TEST_SCHEMA/view/USER_VIEW");
        assertNotNull(resource);
        assertEquals(SCHEME + "://connection/testdb/schema/TEST_SCHEMA/view/USER_VIEW", resource.getUri());
    }

    // Invalid URI tests

    @Test
    void testGetResourceInvalidScheme() {
        Resource resource = provider.getResource("http://example.com");
        assertNull(resource);
    }

    @Test
    void testGetResourceInvalidUri() {
        Resource resource = provider.getResource(SCHEME + "://invalid/path/too/many/segments/here/extra");
        assertNull(resource);
    }

    @Test
    void testGetResourceNullUri() {
        Resource resource = provider.getResource(null);
        assertNull(resource);
    }

    @Test
    void testGetResourceUnknownConnection() {
        Resource resource = provider.getResource(SCHEME + "://connection/unknown");
        assertNull(resource);
    }

    @Test
    void testGetResourceInvalidRootResource() {
        Resource resource = provider.getResource(SCHEME + "://invalid");
        assertNull(resource);
    }

    @Test
    void testInitializeWithoutConnectionManager() {
        JdbcResourceProvider newProvider = new JdbcResourceProvider();
        assertThrows(IllegalStateException.class, newProvider::initialize);
    }
}


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

package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.peacetalk.jmcp.jdbc.ConnectionSupplier;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.JdbcUrlSanitizer;
import org.peacetalk.jmcp.jdbc.config.ConnectionConfig;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverClassManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionManagerTest {

    @TempDir
    Path tempDir;

    private JdbcDriverClassManager driverManager;
    private ConnectionManager connectionManager;

    @BeforeEach
    void setUp() throws Exception {
        driverManager = new JdbcDriverClassManager(tempDir);
        connectionManager = new ConnectionManager(driverManager);
    }

    @AfterEach
    void tearDown() {
        connectionManager.closeAll();
    }

    @Test
    void testRegisterConnection() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));

        assertNotNull(connectionManager.getContext("test-h2"));
    }

    @Test
    void testRegisterMultipleConnections() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("db1", "h2",
                "jdbc:h2:mem:db1", "sa", ""));
        connectionManager.registerConnection(ConnectionConfig.basic("db2", "h2",
                "jdbc:h2:mem:db2", "sa", ""));

        assertNotNull(connectionManager.getContext("db1"));
        assertNotNull(connectionManager.getContext("db2"));
    }

    @Test
    void testGetContext() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));

        ConnectionSupplier ctx = connectionManager.getContext("test-h2");
        assertNotNull(ctx);
    }

    @Test
    void testGetContextNotFound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            connectionManager.getContext("nonexistent"));
        assertTrue(ex.getMessage().contains("nonexistent"),
            "error should name the missing connection id");
    }

    @Test
    void testRegisterDuplicateConnectionIdThrows() {
        ConnectionConfig first = ConnectionConfig.basic("dup", "h2",
                "jdbc:h2:mem:db1", "sa", "");
        ConnectionConfig second = ConnectionConfig.basic("dup", "h2",
                "jdbc:h2:mem:db2", "sa", "");

        assertDoesNotThrow(() -> connectionManager.registerConnection(first));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> connectionManager.registerConnection(second));
        assertTrue(ex.getMessage().contains("dup"),
            "duplicate-id error should name the offending id");

        // The original registration must be untouched (still the db1 context).
        assertEquals("jdbc:h2:mem:db1", connectionManager.getContext("dup").getJdbcUrl());
    }

    @Test
    void testRegisterUnknownDatabaseTypeThrows() {
        ConnectionConfig config = ConnectionConfig.basic("bad", "not-a-db",
                "jdbc:nope:mem:x", "sa", "");

        assertThrows(IllegalArgumentException.class,
            () -> connectionManager.registerConnection(config));
        // A failed registration must not leave a phantom entry behind.
        assertThrows(IllegalArgumentException.class,
            () -> connectionManager.getContext("bad"));
    }

    @Test
    void testGetConnectionFromContext() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));

        ConnectionSupplier ctx = connectionManager.getContext("test-h2");
        try (Connection conn = ctx.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }
    }

    @Test
    void testListConnections() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("db1", "h2",
                "jdbc:h2:mem:db1", "sa", ""));
        connectionManager.registerConnection(ConnectionConfig.basic("db2", "h2",
                "jdbc:h2:mem:db2", "sa", ""));

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(2, connections.size());
        assertTrue(connections.stream().anyMatch(c -> "db1".equals(c.id())));
        assertTrue(connections.stream().anyMatch(c -> "db2".equals(c.id())));
    }

    @Test
    void testListConnectionsEmpty() {
        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertTrue(connections.isEmpty());
    }

    @Test
    void testConnectionInfoFields() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(1, connections.size());

        ConnectionInfo info = connections.getFirst();
        assertEquals("test-h2", info.id());
        assertEquals("h2", info.databaseType());
        assertNotNull(info.url());
        assertNotNull(info.username());
    }

    @Test
    void testSetDefaultConnectionId() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("primary", "h2",
                "jdbc:h2:mem:db1", "sa", ""));
        connectionManager.registerConnection(ConnectionConfig.basic("secondary", "h2",
                "jdbc:h2:mem:db2", "sa", ""));

        connectionManager.setDefaultConnectionId("secondary");
        assertEquals("secondary", connectionManager.getDefaultConnectionId());
    }

    @Test
    void testGetDefaultConnectionId() throws Exception {
        // ConnectionManager initializes with "default" as the default connection ID
        assertEquals("default", connectionManager.getDefaultConnectionId());

        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));
        connectionManager.setDefaultConnectionId("test-h2");

        assertEquals("test-h2", connectionManager.getDefaultConnectionId());
    }

    @Test
    void testExposeUrlsReturnsSanitizedUrl() throws Exception {
        // When exposeUrls=true the manager returns the real URL run through the
        // sanitizer (rather than the fully-hidden "****"). We use a connectable
        // H2 URL here because registerConnection eagerly opens a pool; the
        // masking of embedded secrets itself is covered directly in
        // JdbcUrlSanitizerTest.
        String testUrl = "jdbc:h2:mem:exposetest;DB_CLOSE_DELAY=-1";

        connectionManager.setExposeUrls(true);
        connectionManager.registerConnection(
            ConnectionConfig.basic("test", "h2", testUrl, "sa", ""));

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(1, connections.size());

        String exposedUrl = connections.getFirst().url();
        assertNotEquals("****", exposedUrl,
            "with exposeUrls=true the URL must not be fully hidden");
        assertEquals(JdbcUrlSanitizer.sanitizeUrl(testUrl), exposedUrl,
            "exposed URL must be the sanitized form of the registered URL");
    }

    @Test
    void testUrlExposureControl() throws Exception {
        String testUrl = "jdbc:h2:mem:testdb";
        connectionManager.registerConnection(ConnectionConfig.basic("test", "h2", testUrl, "sa", ""));
        connectionManager.setExposeUrls(false);

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(1, connections.size());
        assertEquals("****", connections.getFirst().url());
    }

    @Test
    void testUrlExposureWhenEnabled() throws Exception {
        String testUrl = "jdbc:h2:mem:testdb";
        connectionManager.registerConnection(ConnectionConfig.basic("test", "h2", testUrl, "sa", ""));
        connectionManager.setExposeUrls(true);

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(1, connections.size());
        // With no sensitive params, the exposed URL is the exact original.
        assertEquals(testUrl, connections.getFirst().url());
    }

    @Test
    void testExposeUrlsDefaultsToFalse() {
        // Security default: URLs are hidden unless explicitly enabled.
        assertFalse(connectionManager.isExposeUrls(),
            "exposeUrls must default to false for security");
    }

    @Test
    void testCloseConnectionErrorNamesMissingId() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));
        connectionManager.closeConnection("test-h2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> connectionManager.getContext("test-h2"));
        assertTrue(ex.getMessage().contains("test-h2"),
            "post-close lookup error should name the id");
    }

    @Test
    void testCloseUnknownConnectionIsNoOp() {
        // Closing an id that was never registered must be silently ignored.
        assertDoesNotThrow(() -> connectionManager.closeConnection("never-registered"));
    }

    @Test
    void testConnectionInfoUsernameIsExactValue() throws Exception {
        connectionManager.setExposeUrls(true);
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "myuser", "mypassword"));

        ConnectionInfo info = connectionManager.listConnections().getFirst();
        assertEquals("myuser", info.username(), "username must be surfaced verbatim");
        // ConnectionInfo carries no password field; the config password must not
        // leak through any other field.
        assertFalse(String.valueOf(info.url()).contains("mypassword"),
            "config password must never appear in the exposed URL");
    }

    @Test
    void testCloseAll() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("db1", "h2",
                "jdbc:h2:mem:db1", "sa", ""));
        connectionManager.registerConnection(ConnectionConfig.basic("db2", "h2",
                "jdbc:h2:mem:db2", "sa", ""));

        connectionManager.closeAll();

        assertThrows(Exception.class, () ->
            connectionManager.getContext("db1"));
        assertThrows(Exception.class, () ->
            connectionManager.getContext("db2"));
    }

    @Test
    void testConnectionPersistence() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));

        ConnectionSupplier ctx1 = connectionManager.getContext("test-h2");
        ConnectionSupplier ctx2 = connectionManager.getContext("test-h2");

        // Should get the same connection context
        assertSame(ctx1, ctx2);
    }

    @Test
    void testConnectionWithStatements() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", ""));

        ConnectionSupplier ctx = connectionManager.getContext("test-h2");
        try (Connection conn = ctx.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("INSERT INTO test_table VALUES (1, 'Test')");
                stmt.executeQuery("SELECT * FROM test_table");
            }
        }
    }

    @Test
    void testMultipleConnectionsIndependent() throws Exception {
        connectionManager.registerConnection(ConnectionConfig.basic("db1", "h2",
                "jdbc:h2:mem:db1", "sa", ""));
        connectionManager.registerConnection(ConnectionConfig.basic("db2", "h2",
                "jdbc:h2:mem:db2", "sa", ""));

        ConnectionSupplier ctx1 = connectionManager.getContext("db1");
        ConnectionSupplier ctx2 = connectionManager.getContext("db2");

        try (Connection conn1 = ctx1.getConnection();
             Connection conn2 = ctx2.getConnection()) {

            try (Statement stmt1 = conn1.createStatement()) {
                stmt1.execute("CREATE TABLE test (id INT)");
            }

            // Table should not exist in second database
            try (Statement stmt2 = conn2.createStatement()) {
                assertThrows(Exception.class, () ->
                    stmt2.execute("SELECT * FROM test"));
            }
        }
    }
}


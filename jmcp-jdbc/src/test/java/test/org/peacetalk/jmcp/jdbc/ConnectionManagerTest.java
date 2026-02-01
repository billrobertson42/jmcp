package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionManagerTest {

    @TempDir
    Path tempDir;

    private JdbcDriverManager driverManager;
    private ConnectionManager connectionManager;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDriverManager driverManager = new JdbcDriverManager(tempDir);
        connectionManager = new ConnectionManager(driverManager);
    }

    @AfterEach
    void tearDown() {
        connectionManager.closeAll();
    }

    @Test
    void testRegisterConnection() throws Exception {
        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");

        assertNotNull(connectionManager.getContext("test-h2"));
    }

    @Test
    void testRegisterMultipleConnections() throws Exception {
        connectionManager.registerConnection("db1", "h2",
                "jdbc:h2:mem:db1", "sa", "");
        connectionManager.registerConnection("db2", "h2",
                "jdbc:h2:mem:db2", "sa", "");

        assertNotNull(connectionManager.getContext("db1"));
        assertNotNull(connectionManager.getContext("db2"));
    }

    @Test
    void testGetConnectionContext() throws Exception {
        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");

        ConnectionContext ctx = connectionManager.getContext("test-h2");
        assertNotNull(ctx);
    }

    @Test
    void testGetConnectionContextNotFound() {
        assertThrows(RuntimeException.class, () ->
            connectionManager.getContext("nonexistent"));
    }

    @Test
    void testGetConnectionFromContext() throws Exception {
        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");

        ConnectionContext ctx = connectionManager.getContext("test-h2");
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
        connectionManager.registerConnection("db1", "h2",
                "jdbc:h2:mem:db1", "sa", "");
        connectionManager.registerConnection("db2", "h2",
                "jdbc:h2:mem:db2", "sa", "");

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
        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");

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
        connectionManager.registerConnection("primary", "h2",
                "jdbc:h2:mem:db1", "sa", "");
        connectionManager.registerConnection("secondary", "h2",
                "jdbc:h2:mem:db2", "sa", "");

        connectionManager.setDefaultConnectionId("secondary");
        assertEquals("secondary", connectionManager.getDefaultConnectionId());
    }

    @Test
    void testGetDefaultConnectionId() throws Exception {
        // ConnectionManager initializes with "default" as the default connection ID
        assertEquals("default", connectionManager.getDefaultConnectionId());

        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");
        connectionManager.setDefaultConnectionId("test-h2");

        assertEquals("test-h2", connectionManager.getDefaultConnectionId());
    }

    @Test
    void testUrlSanitization() throws Exception {
        // Test that URLs are properly handled based on exposeUrls setting
        String testUrl = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";

        // Set exposeUrls to true so we can see the URL
        connectionManager.setExposeUrls(true);
        connectionManager.registerConnection("test", "h2", testUrl, "sa", "");

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(1, connections.size());

        String exposedUrl = connections.getFirst().url();
        assertNotNull(exposedUrl);
        // When exposeUrls is true, URL should be visible (possibly sanitized if it had sensitive params)
        assertTrue(exposedUrl.contains("jdbc:h2"), "URL should be visible when exposeUrls is true");
    }

    @Test
    void testUrlExposureControl() throws Exception {
        String testUrl = "jdbc:h2:mem:testdb";
        connectionManager.registerConnection("test", "h2", testUrl, "sa", "");
        connectionManager.setExposeUrls(false);

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(1, connections.size());
        assertEquals("****", connections.getFirst().url());
    }

    @Test
    void testUrlExposureWhenEnabled() throws Exception {
        String testUrl = "jdbc:h2:mem:testdb";
        connectionManager.registerConnection("test", "h2", testUrl, "sa", "");
        connectionManager.setExposeUrls(true);

        List<ConnectionInfo> connections = connectionManager.listConnections();
        assertEquals(1, connections.size());
        assertNotEquals("****", connections.getFirst().url());
    }

    @Test
    void testCloseConnection() throws Exception {
        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");

        connectionManager.closeConnection("test-h2");

        assertThrows(Exception.class, () ->
            connectionManager.getContext("test-h2"));
    }

    @Test
    void testCloseAll() throws Exception {
        connectionManager.registerConnection("db1", "h2",
                "jdbc:h2:mem:db1", "sa", "");
        connectionManager.registerConnection("db2", "h2",
                "jdbc:h2:mem:db2", "sa", "");

        connectionManager.closeAll();

        assertThrows(Exception.class, () ->
            connectionManager.getContext("db1"));
        assertThrows(Exception.class, () ->
            connectionManager.getContext("db2"));
    }

    @Test
    void testConnectionPersistence() throws Exception {
        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");

        ConnectionContext ctx1 = connectionManager.getContext("test-h2");
        ConnectionContext ctx2 = connectionManager.getContext("test-h2");

        // Should get the same connection context
        assertSame(ctx1, ctx2);
    }

    @Test
    void testConnectionWithStatements() throws Exception {
        connectionManager.registerConnection("test-h2", "h2",
                "jdbc:h2:mem:testdb", "sa", "");

        ConnectionContext ctx = connectionManager.getContext("test-h2");
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
        connectionManager.registerConnection("db1", "h2",
                "jdbc:h2:mem:db1", "sa", "");
        connectionManager.registerConnection("db2", "h2",
                "jdbc:h2:mem:db2", "sa", "");

        ConnectionContext ctx1 = connectionManager.getContext("db1");
        ConnectionContext ctx2 = connectionManager.getContext("db2");

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


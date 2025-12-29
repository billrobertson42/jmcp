package test.org.peacetalk.jmcp.jdbc.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.peacetalk.jmcp.jdbc.driver.MavenCoordinates;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JdbcDriverManagerTest {

    @TempDir
    Path tempDir;

    private JdbcDriverManager driverManager;

    @BeforeEach
    void setUp() throws Exception {
        driverManager = new JdbcDriverManager(tempDir);
    }

    @Test
    void testGetKnownDriver() {
        MavenCoordinates postgresql = driverManager.getKnownDriver("postgresql");
        assertNotNull(postgresql);
        assertEquals("org.postgresql", postgresql.groupId());
        assertEquals("postgresql", postgresql.artifactId());

        MavenCoordinates mysql = driverManager.getKnownDriver("mysql");
        assertNotNull(mysql);
        assertEquals("com.mysql", mysql.groupId());

        MavenCoordinates mariadb = driverManager.getKnownDriver("mariadb");
        assertNotNull(mariadb);

        MavenCoordinates h2 = driverManager.getKnownDriver("h2");
        assertNotNull(h2);

        MavenCoordinates sqlite = driverManager.getKnownDriver("sqlite");
        assertNotNull(sqlite);
    }

    @Test
    void testGetKnownDriverCaseInsensitive() {
        MavenCoordinates postgresql = driverManager.getKnownDriver("PostgreSQL");
        assertNotNull(postgresql);
        assertEquals("org.postgresql", postgresql.groupId());

        MavenCoordinates mysql = driverManager.getKnownDriver("MYSQL");
        assertNotNull(mysql);
    }

    @Test
    void testGetUnknownDriver() {
        MavenCoordinates unknown = driverManager.getKnownDriver("unknowndb");
        assertNull(unknown);
    }

    @Test
    void testLoadDriverThrowsForUnknownDatabase() {
        assertThrows(IllegalArgumentException.class, () -> {
            driverManager.loadDriver("unknowndb");
        });
    }

    @Test
    void testDriverManagerCreatesDirectory() throws Exception {
        Path newDir = tempDir.resolve("drivers");
        JdbcDriverManager manager = new JdbcDriverManager(newDir);

        assertTrue(newDir.toFile().exists());
        assertTrue(newDir.toFile().isDirectory());
    }

    @Test
    void testUnloadNonExistentDriver() throws Exception {
        // Should not throw
        assertDoesNotThrow(() -> {
            driverManager.unloadDriver("postgresql");
        });
    }
}


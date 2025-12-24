package test.org.peacetalk.jmcp.jdbc.driver;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.driver.DriverCoordinates;

import static org.junit.jupiter.api.Assertions.*;


class DriverCoordinatesTest {

    @Test
    void testCoordinatesCreation() {
        DriverCoordinates coords = new DriverCoordinates(
            "org.postgresql",
            "postgresql",
            "42.7.1"
        );

        assertEquals("org.postgresql", coords.groupId());
        assertEquals("postgresql", coords.artifactId());
        assertEquals("42.7.1", coords.version());
    }

    @Test
    void testToPath() {
        DriverCoordinates coords = new DriverCoordinates(
            "org.postgresql",
            "postgresql",
            "42.7.1"
        );

        String expected = "org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar";
        assertEquals(expected, coords.toPath());
    }

    @Test
    void testGetMavenCentralUrl() {
        DriverCoordinates coords = new DriverCoordinates(
            "org.postgresql",
            "postgresql",
            "42.7.1"
        );

        String expected = "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar";
        assertEquals(expected, coords.getMavenCentralUrl());
    }

    @Test
    void testToString() {
        DriverCoordinates coords = new DriverCoordinates(
            "com.mysql",
            "mysql-connector-j",
            "8.3.0"
        );

        assertEquals("com.mysql:mysql-connector-j:8.3.0", coords.toString());
    }

    @Test
    void testWithComplexGroupId() {
        DriverCoordinates coords = new DriverCoordinates(
            "com.oracle.database.jdbc",
            "ojdbc11",
            "23.3.0.23.09"
        );

        String expectedPath = "com/oracle/database/jdbc/ojdbc11/23.3.0.23.09/ojdbc11-23.3.0.23.09.jar";
        assertEquals(expectedPath, coords.toPath());
    }
}


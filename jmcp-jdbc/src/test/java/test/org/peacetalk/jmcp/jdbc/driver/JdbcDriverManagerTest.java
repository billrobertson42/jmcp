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

package test.org.peacetalk.jmcp.jdbc.driver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.driver.MavenCoordinates;

import java.nio.file.Path;
import java.sql.Driver;

import static org.junit.jupiter.api.Assertions.*;

class JdbcDriverManagerTest {

    @TempDir
    Path tempDir;

    private JdbcDriverManager driverManager;

    @BeforeEach
    void setUp() throws Exception {
        driverManager = new JdbcDriverManager(tempDir);
    }

    @AfterEach
    void tearDown() {
        // Clean up any loaded drivers
    }

    @Test
    void testManagerInitialization() {
        assertNotNull(driverManager);
    }

    @Test
    void testLoadH2Driver() throws Exception {
        var classLoader = driverManager.loadDriver("h2");
        assertNotNull(classLoader);

        // Should be able to load the H2 driver class
        Class<?> driverClass = classLoader.loadClass("org.h2.Driver");
        assertNotNull(driverClass);
        assertTrue(Driver.class.isAssignableFrom(driverClass));
    }

    @Test
    void testGetKnownDriver() {
        MavenCoordinates h2 = driverManager.getKnownDriver("h2");
        assertNotNull(h2);

        MavenCoordinates sqlite = driverManager.getKnownDriver("sqlite");
        assertNotNull(sqlite);
    }

    @Test
    void testGetUnknownDriver() {
        assertThrows(IllegalArgumentException.class, () ->
            driverManager.getKnownDriver("unknown-db")
        );
    }

    @Test
    void testUnloadDriver() throws Exception {
        var classLoader = driverManager.loadDriver("h2");
        assertNotNull(classLoader);

        driverManager.unloadDriver("h2");

        // Should not throw after unload
    }

    @Test
    void testUnloadByCoordinates() throws Exception {
        MavenCoordinates coords = driverManager.getKnownDriver("h2");
        var classLoader = driverManager.loadDriver(coords);
        assertNotNull(classLoader);

        driverManager.unloadDriver(coords);
    }

    @Test
    void testMultipleLoadUnloadCycles() throws Exception {
        for (int i = 0; i < 3; i++) {
            var classLoader = driverManager.loadDriver("h2");
            assertNotNull(classLoader);
            driverManager.unloadDriver("h2");
        }
    }

    @Test
    void testLoadDifferentDrivers() throws Exception {
        var h2Loader = driverManager.loadDriver("h2");
        var sqliteLoader = driverManager.loadDriver("sqlite");

        assertNotNull(h2Loader);
        assertNotNull(sqliteLoader);

        // They should be different classloaders
        assertNotSame(h2Loader, sqliteLoader);
    }

    @Test
    void testClassLoaderIsolation() throws Exception {
        var h2Loader = driverManager.loadDriver("h2");

        // H2 Driver should be loadable from h2 classloader
        Class<?> h2DriverClass = h2Loader.loadClass("org.h2.Driver");
        assertNotNull(h2DriverClass);
        assertEquals("org.h2.Driver", h2DriverClass.getName());
    }
}


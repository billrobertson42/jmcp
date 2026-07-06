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

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.driver.MavenCoordinates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class MavenCoordinatesTest {

    @Test
    void testToPath() {
        MavenCoordinates coords = new MavenCoordinates(
            "org.postgresql",
            "postgresql",
            "42.7.1"
        );

        String expected = "org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar";
        assertEquals(expected, coords.toPath());
    }

    @Test
    void testGetMavenCentralUrl() {
        MavenCoordinates coords = new MavenCoordinates(
            "org.postgresql",
            "postgresql",
            "42.7.1"
        );

        String expected = "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar";
        assertEquals(expected, coords.getMavenCentralUrl());
    }

    @Test
    void testToString() {
        MavenCoordinates coords = new MavenCoordinates(
            "com.mysql",
            "mysql-connector-j",
            "8.3.0"
        );

        assertEquals("com.mysql:mysql-connector-j:8.3.0", coords.toString());
    }

    @Test
    void testWithComplexGroupId() {
        MavenCoordinates coords = new MavenCoordinates(
            "com.oracle.database.jdbc",
            "ojdbc11",
            "23.3.0.23.09"
        );

        String expectedPath = "com/oracle/database/jdbc/ojdbc11/23.3.0.23.09/ojdbc11-23.3.0.23.09.jar";
        assertEquals(expectedPath, coords.toPath());
    }

    @Test
    void testUrlEndsWithPath() {
        // getMavenCentralUrl is just the Central base + toPath; keep them consistent.
        MavenCoordinates coords = new MavenCoordinates("org.xerial", "sqlite-jdbc", "3.51.1.0");
        assertEquals("https://repo1.maven.org/maven2/" + coords.toPath(),
            coords.getMavenCentralUrl());
        assertTrue(coords.getMavenCentralUrl().endsWith(coords.toPath()));
    }

    @Test
    void testRecordEqualityByComponents() {
        // JdbcDriverClassManager caches loaders keyed by toString(); equal coordinates
        // must be equal (and hash equally) so the cache dedupes correctly.
        MavenCoordinates a = new MavenCoordinates("org.postgresql", "postgresql", "42.7.1");
        MavenCoordinates b = new MavenCoordinates("org.postgresql", "postgresql", "42.7.1");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.toString(), b.toString());
    }

    @Test
    void testRecordInequalityWhenVersionDiffers() {
        MavenCoordinates v1 = new MavenCoordinates("org.postgresql", "postgresql", "42.7.1");
        MavenCoordinates v2 = new MavenCoordinates("org.postgresql", "postgresql", "42.7.8");

        assertNotEquals(v1, v2);
        assertNotEquals(v1.toString(), v2.toString());
        assertNotEquals(v1.toPath(), v2.toPath());
    }
}


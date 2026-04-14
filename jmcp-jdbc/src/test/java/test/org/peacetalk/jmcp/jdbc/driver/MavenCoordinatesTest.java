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


class MavenCoordinatesTest {

    @Test
    void testCoordinatesCreation() {
        MavenCoordinates coords = new MavenCoordinates(
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
}


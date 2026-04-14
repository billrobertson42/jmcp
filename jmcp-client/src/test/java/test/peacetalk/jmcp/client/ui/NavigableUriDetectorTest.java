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

package test.peacetalk.jmcp.client.ui;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.client.ui.NavigableUriDetector;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NavigableUriDetector
 */
class NavigableUriDetectorTest {

    @Test
    void testFindNavigableUrisEmpty() {
        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris("");
        assertTrue(results.isEmpty());
    }

    @Test
    void testFindNavigableUrisNull() {
        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(null);
        assertTrue(results.isEmpty());
    }

    @Test
    void testFindNavigableUriSingle() {
        String json = """
            {
              "name": "test",
              "uri": "db://connection/db1"
            }
            """;

        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);

        assertEquals(1, results.size());
        assertEquals("uri", results.get(0).fieldName());
        assertEquals("db://connection/db1", results.get(0).uri());
    }

    @Test
    void testFindNavigableUriMultiple() {
        String json = """
            {
              "resourceUri": "db://connection/db1",
              "schemasUri": "db://connection/db1/schemas"
            }
            """;

        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);

        assertEquals(2, results.size());
        assertEquals("resourceUri", results.get(0).fieldName());
        assertEquals("schemasUri", results.get(1).fieldName());
    }

    @Test
    void testFindNavigableUriWithParent() {
        String json = """
            {
              "name": "USERS",
              "parent": "db://connection/db1/schema/public"
            }
            """;

        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);

        assertEquals(1, results.size());
        assertEquals("parent", results.get(0).fieldName());
    }

    @Test
    void testFindNavigableUriWithForeignKey() {
        String json = """
            {
              "foreignKeys": [{
                "referencedTable": "users",
                "referencedTableUri": "db://connection/db1/schema/public/table/users"
              }]
            }
            """;

        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);

        assertEquals(1, results.size());
        assertEquals("referencedTableUri", results.get(0).fieldName());
    }

    @Test
    void testIsNavigableUriValid() {
        assertTrue(NavigableUriDetector.isNavigableUri("db://connection"));
        assertTrue(NavigableUriDetector.isNavigableUri("http://example.com"));
        assertTrue(NavigableUriDetector.isNavigableUri("file://path/to/file"));
    }

    @Test
    void testIsNavigableUriInvalid() {
        assertFalse(NavigableUriDetector.isNavigableUri(null));
        assertFalse(NavigableUriDetector.isNavigableUri(""));
        assertFalse(NavigableUriDetector.isNavigableUri("not a uri"));
        assertFalse(NavigableUriDetector.isNavigableUri("db:"));
    }

    @Test
    void testExtractScheme() {
        assertEquals("db", NavigableUriDetector.extractScheme("db://connection"));
        assertEquals("http", NavigableUriDetector.extractScheme("http://example.com"));
        assertNull(NavigableUriDetector.extractScheme(null));
        assertNull(NavigableUriDetector.extractScheme("no-scheme"));
    }

    @Test
    void testNavigableUriRecord() {
        NavigableUriDetector.NavigableUri uri =
            new NavigableUriDetector.NavigableUri("uri", "db://test", 10, 50);

        assertEquals("uri", uri.fieldName());
        assertEquals("db://test", uri.uri());
        assertEquals(10, uri.startIndex());
        assertEquals(50, uri.endIndex());
    }
}


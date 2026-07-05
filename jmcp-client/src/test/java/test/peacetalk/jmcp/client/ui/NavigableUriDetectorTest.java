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
        assertTrue(results.isEmpty(), "empty input yields no matches");
    }

    @Test
    void testFindNavigableUrisNull() {
        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(null);
        assertTrue(results.isEmpty(), "null input yields no matches");
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

        assertEquals(1, results.size(), "exactly one 'uri' field should match");
        assertEquals("uri", results.get(0).fieldName());
        assertEquals("db://connection/db1", results.get(0).uri());
    }

    @Test
    void testFindNavigableUriIndicesLocateTheMatch() {
        // The reported [startIndex, endIndex) should slice out the whole "field": "value" match.
        String json = """
                {"uri": "db://x"}""";
        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);

        assertEquals(1, results.size());
        NavigableUriDetector.NavigableUri match = results.get(0);
        String slice = json.substring(match.startIndex(), match.endIndex());
        assertEquals("\"uri\": \"db://x\"", slice,
            "start/end indices should bracket the matched field/value pair");
        assertTrue(match.startIndex() >= 0 && match.endIndex() <= json.length(),
            "indices must be within the input bounds");
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

        assertEquals(2, results.size(), "both *Uri fields should match");
        assertEquals("resourceUri", results.get(0).fieldName());
        assertEquals("db://connection/db1", results.get(0).uri());
        assertEquals("schemasUri", results.get(1).fieldName());
        assertEquals("db://connection/db1/schemas", results.get(1).uri());
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
        assertEquals("db://connection/db1/schema/public", results.get(0).uri(),
            "the parent URI value should be captured");
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

        assertEquals(1, results.size(), "only the *Uri field should match, not referencedTable");
        assertEquals("referencedTableUri", results.get(0).fieldName());
        assertEquals("db://connection/db1/schema/public/table/users", results.get(0).uri());
    }

    @Test
    void testNonMatchingFieldNameIgnored() {
        // "name" is not a URI field even though its value contains a scheme.
        String json = """
                {"name": "db://connection/db1"}""";
        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);
        assertTrue(results.isEmpty(),
            "a scheme value under a non-URI field name should not be detected");
    }

    @Test
    void testUriFieldWithoutSchemeIgnored() {
        // The value must contain "://" to match; a plain value under "uri" is skipped.
        String json = """
                {"uri": "plain-value"}""";
        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);
        assertTrue(results.isEmpty(),
            "a 'uri' field whose value lacks a scheme should not match");
    }

    @Test
    void testFindNavigableUriWithRelationships() {
        // "relationships" is one of the recognized field names.
        String json = """
                {"relationships": "db://connection/db1/relationships"}""";
        List<NavigableUriDetector.NavigableUri> results = NavigableUriDetector.findNavigableUris(json);

        assertEquals(1, results.size());
        assertEquals("relationships", results.get(0).fieldName());
        assertEquals("db://connection/db1/relationships", results.get(0).uri());
    }

    @Test
    void testIsNavigableUriValid() {
        assertTrue(NavigableUriDetector.isNavigableUri("db://connection"));
        assertTrue(NavigableUriDetector.isNavigableUri("http://example.com"));
        assertTrue(NavigableUriDetector.isNavigableUri("file://path/to/file"));
    }

    @Test
    void testIsNavigableUriInvalid() {
        assertFalse(NavigableUriDetector.isNavigableUri(null), "null is not navigable");
        assertFalse(NavigableUriDetector.isNavigableUri(""), "empty is not navigable");
        assertFalse(NavigableUriDetector.isNavigableUri("not a uri"), "plain text is not navigable");
        assertFalse(NavigableUriDetector.isNavigableUri("db:"), "'db:' without '//' is not navigable");
    }

    @Test
    void testIsNavigableUriRejectsUppercaseScheme() {
        // The scheme pattern is ^[a-z]+://.*, so an uppercase scheme is rejected.
        assertFalse(NavigableUriDetector.isNavigableUri("HTTP://example.com"),
            "uppercase scheme should not be considered navigable");
    }

    @Test
    void testIsNavigableUriRejectsDigitScheme() {
        // [a-z]+ excludes digits, so a scheme with digits is rejected.
        assertFalse(NavigableUriDetector.isNavigableUri("db1://connection"),
            "a scheme containing digits should not be considered navigable");
    }

    @Test
    void testIsNavigableUriRejectsEmptyScheme() {
        assertFalse(NavigableUriDetector.isNavigableUri("://no-scheme"),
            "an empty scheme should not be considered navigable");
    }

    @Test
    void testExtractScheme() {
        assertEquals("db", NavigableUriDetector.extractScheme("db://connection"));
        assertEquals("http", NavigableUriDetector.extractScheme("http://example.com"));
        assertNull(NavigableUriDetector.extractScheme(null), "null URI has no scheme");
        assertNull(NavigableUriDetector.extractScheme("no-scheme"), "no '://' means no scheme");
    }

    @Test
    void testExtractSchemeEmptyScheme() {
        // idx of "://" is 0, and the guard requires idx > 0, so an empty scheme returns null.
        assertNull(NavigableUriDetector.extractScheme("://x"),
            "leading '://' (empty scheme) should return null");
    }

    @Test
    void testExtractSchemePreservesFirstSchemeOnly() {
        // indexOf finds the first "://"; everything before it is the scheme.
        assertEquals("db", NavigableUriDetector.extractScheme("db://host/path://more"),
            "only the substring before the first '://' is the scheme");
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

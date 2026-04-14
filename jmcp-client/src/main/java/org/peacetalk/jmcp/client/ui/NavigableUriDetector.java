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

package org.peacetalk.jmcp.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for identifying navigable resource URIs in JSON content.
 * Recognizes fields that contain resource URIs based on naming conventions.
 */
public class NavigableUriDetector {

    // Field names that indicate a navigable URI
    private static final Set<String> URI_FIELD_NAMES = Set.of(
        "uri",
        "[a-zA-Z0-9_]+Uri",
        "parent",
        "relationships"
    );

    // Pattern to match resource URIs (e.g., db://...)
    private static final Pattern RESOURCE_URI_PATTERN = Pattern.compile(
        "\"(" + String.join("|", URI_FIELD_NAMES) + ")\"\\s*:\\s*\"([^\"]+://[^\"]+)\""
    );

    /**
     * Represents a navigable URI found in content.
     */
    public record NavigableUri(
        String fieldName,
        String uri,
        int startIndex,
        int endIndex
    ) {}

    /**
     * Find all navigable URIs in a JSON string.
     *
     * @param jsonContent The JSON content to scan
     * @return List of navigable URIs with their positions
     */
    public static List<NavigableUri> findNavigableUris(String jsonContent) {
        List<NavigableUri> results = new ArrayList<>();

        if (jsonContent == null || jsonContent.isEmpty()) {
            return results;
        }

        Matcher matcher = RESOURCE_URI_PATTERN.matcher(jsonContent);
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String uri = matcher.group(2);
            results.add(new NavigableUri(fieldName, uri, matcher.start(), matcher.end()));
        }

        return results;
    }

    /**
     * Check if a URI looks like a navigable resource URI.
     *
     * @param uri The URI to check
     * @return true if it looks like a resource URI
     */
    public static boolean isNavigableUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        // Check for scheme:// pattern
        return uri.matches("^[a-z]+://.*");
    }

    /**
     * Extract the scheme from a URI.
     *
     * @param uri The URI
     * @return The scheme (e.g., "db") or null if invalid
     */
    public static String extractScheme(String uri) {
        if (uri == null) {
            return null;
        }
        int idx = uri.indexOf("://");
        if (idx > 0) {
            return uri.substring(0, idx);
        }
        return null;
    }
}


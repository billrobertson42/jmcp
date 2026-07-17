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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renders a Jackson JSON tree into display segments: plain text interleaved
 * with navigable resource-URI links. The renderer pretty-prints while walking
 * the tree (2-space indent, matching Jackson's default pretty printer), so no
 * character offsets are ever computed. Any string <em>value</em> — in an
 * object or an array — that looks like a resource URI becomes a link segment.
 *
 * The concatenation of all segments' text is valid JSON equivalent to the
 * input tree.
 */
public final class JsonSegmentRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // A resource URI: a scheme (lowercase letter, then letters/digits/+/./-)
    // followed by :// and at least one more character.
    private static final Pattern RESOURCE_URI_PATTERN =
            Pattern.compile("^[a-z][a-z0-9+.-]*://.+");

    private JsonSegmentRenderer() {}

    /**
     * A piece of rendered output: plain text, or a navigable link.
     * For links, {@code text} is the URI itself (the surrounding JSON quotes
     * are part of the adjacent text segments).
     *
     * @param text The display text of this segment
     * @param uri The navigable URI, or null for plain text segments
     */
    public record Segment(String text, String uri) {
        public boolean isLink() {
            return uri != null;
        }

        public static Segment text(String text) {
            return new Segment(text, null);
        }

        public static Segment link(String uri) {
            return new Segment(uri, uri);
        }
    }

    /**
     * Parse content as JSON and render it. If the content is not a JSON
     * object or array (including not JSON at all), it is returned unchanged
     * as a single plain-text segment.
     *
     * @param content The content to render
     * @return The rendered segments
     */
    public static List<Segment> render(String content) {
        try {
            JsonNode node = MAPPER.readTree(content);
            if (node.isObject() || node.isArray()) {
                return render(node);
            }
        } catch (JacksonException e) {
            // Not JSON - fall through to plain text
        }
        return List.of(Segment.text(content));
    }

    /**
     * Render a JSON tree into segments.
     *
     * @param node The tree to render
     * @return The rendered segments
     */
    public static List<Segment> render(JsonNode node) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        walk(node, 0, buffer, segments);
        flush(buffer, segments);
        return segments;
    }

    /**
     * Check if a string value looks like a navigable resource URI.
     *
     * @param value The value to check
     * @return true if it matches the resource-URI shape
     */
    public static boolean isNavigableUri(String value) {
        return value != null && RESOURCE_URI_PATTERN.matcher(value).matches();
    }

    private static void walk(JsonNode node, int indent, StringBuilder buffer, List<Segment> segments) {
        if (node.isObject()) {
            walkObject(node, indent, buffer, segments);
        } else if (node.isArray()) {
            walkArray(node, indent, buffer, segments);
        } else if (node.isString() && isLinkable(node.stringValue())) {
            buffer.append('"');
            flush(buffer, segments);
            segments.add(Segment.link(node.stringValue()));
            buffer.append('"');
        } else {
            // Scalar: delegate serialization (quoting/escaping, number form) to Jackson
            buffer.append(MAPPER.writeValueAsString(node));
        }
    }

    private static void walkObject(JsonNode node, int indent, StringBuilder buffer, List<Segment> segments) {
        if (node.isEmpty()) {
            buffer.append("{ }");
            return;
        }
        buffer.append('{');
        boolean first = true;
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!first) {
                buffer.append(',');
            }
            first = false;
            buffer.append('\n').append("  ".repeat(indent + 1));
            buffer.append(MAPPER.writeValueAsString(entry.getKey())).append(" : ");
            walk(entry.getValue(), indent + 1, buffer, segments);
        }
        buffer.append('\n').append("  ".repeat(indent)).append('}');
    }

    private static void walkArray(JsonNode node, int indent, StringBuilder buffer, List<Segment> segments) {
        if (node.isEmpty()) {
            buffer.append("[ ]");
            return;
        }
        // Arrays render inline (elements separated by ", "), matching Jackson's
        // default pretty printer; nested objects still break onto new lines.
        buffer.append("[ ");
        for (int i = 0; i < node.size(); i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            walk(node.get(i), indent, buffer, segments);
        }
        buffer.append(" ]");
    }

    /**
     * A value is rendered as a link only if it is URI-shaped AND needs no JSON
     * escaping, so that splicing the raw URI between quote segments keeps the
     * concatenated output valid JSON.
     */
    private static boolean isLinkable(String value) {
        return isNavigableUri(value)
                && MAPPER.writeValueAsString(value).equals('"' + value + '"');
    }

    private static void flush(StringBuilder buffer, List<Segment> segments) {
        if (buffer.length() > 0) {
            segments.add(Segment.text(buffer.toString()));
            buffer.setLength(0);
        }
    }
}

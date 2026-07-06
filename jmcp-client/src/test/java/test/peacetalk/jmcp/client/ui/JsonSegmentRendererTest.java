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
import org.peacetalk.jmcp.client.ui.JsonSegmentRenderer;
import org.peacetalk.jmcp.client.ui.JsonSegmentRenderer.Segment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonSegmentRenderer - the tree-walking replacement for
 * regex-based URI detection.
 */
class JsonSegmentRendererTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<Segment> links(List<Segment> segments) {
        return segments.stream().filter(Segment::isLink).toList();
    }

    private static String concat(List<Segment> segments) {
        return segments.stream().map(Segment::text).collect(joining());
    }

    @Test
    void uriInObjectFieldBecomesLink() {
        List<Segment> segments = JsonSegmentRenderer.render("""
                {"uri": "db://connection/db1"}""");

        assertEquals(List.of(
                        Segment.text("{\n  \"uri\" : \""),
                        Segment.link("db://connection/db1"),
                        Segment.text("\"\n}")),
                segments,
                "the URI value must be a link, quotes and layout must be text");
    }

    @Test
    void urisInsideArrayBecomeLinks() {
        // The old regex only matched "field": "uri" pairs, so URIs that were
        // array elements (e.g. a relationships array) were never linked.
        List<Segment> segments = JsonSegmentRenderer.render("""
                {"relationships": ["db://connection/db1/rel/a", "db://connection/db1/rel/b"]}""");

        List<Segment> links = links(segments);
        assertEquals(2, links.size(), "both array-element URIs must become links");
        assertEquals("db://connection/db1/rel/a", links.get(0).uri());
        assertEquals("db://connection/db1/rel/b", links.get(1).uri());
    }

    @Test
    void uriInTopLevelArrayBecomesLink() {
        List<Segment> segments = JsonSegmentRenderer.render("""
                ["db://connection/db1", "not a uri"]""");

        List<Segment> links = links(segments);
        assertEquals(1, links.size(), "only the URI-shaped element becomes a link");
        assertEquals("db://connection/db1", links.get(0).uri());
        assertEquals("[ \"", segments.get(0).text(), "array opening must precede the link");
    }

    @Test
    void uriInNestedObjectBecomesLink() {
        List<Segment> segments = JsonSegmentRenderer.render("""
                {"table": {"meta": {"parent": "db://connection/db1/schema/public"}}}""");

        List<Segment> links = links(segments);
        assertEquals(1, links.size(), "a URI nested two objects deep must be found");
        assertEquals("db://connection/db1/schema/public", links.get(0).uri());
    }

    @Test
    void anyUriShapedValueIsLinkedRegardlessOfFieldName() {
        // The field-name whitelist is gone: "location" was never in the old
        // URI_FIELD_NAMES set but its value is URI-shaped.
        List<Segment> segments = JsonSegmentRenderer.render("""
                {"location": "custom-scheme+1.2://host/path"}""");

        List<Segment> links = links(segments);
        assertEquals(1, links.size(), "any resource-URI-shaped string value must be navigable");
        assertEquals("custom-scheme+1.2://host/path", links.get(0).uri());
    }

    @Test
    void nonUriStringsAreNotLinked() {
        List<Segment> segments = JsonSegmentRenderer.render("""
                {"name": "USERS", "note": "db:", "upper": "DB://x", "badScheme": "1db://x"}""");

        assertEquals(List.of(), links(segments),
                "plain text, missing '//', uppercase scheme and digit-leading scheme must not link");
    }

    @Test
    void escapedQuotesInStringsRenderEscapedAndDoNotBreakOutput() {
        // The old regex used [^"]+ and broke on escaped quotes inside values.
        List<Segment> segments = JsonSegmentRenderer.render("""
                {"msg": "say \\"hi\\"", "uri": "db://x"}""");

        assertEquals(1, links(segments).size(), "the URI after the escaped string must still link");
        String out = concat(segments);
        assertTrue(out.contains("say \\\"hi\\\""),
                "the quote inside the value must be re-escaped in the output: " + out);
        assertEquals("say \"hi\"", MAPPER.readTree(out).get("msg").stringValue(),
                "output must round-trip the escaped string exactly");
    }

    @Test
    void uriNeedingJsonEscapingIsNotLinked() {
        // Splicing a raw backslash between quote segments would corrupt the
        // JSON output, so such values stay escaped text.
        List<Segment> segments = JsonSegmentRenderer.render("""
                {"uri": "db://a\\\\b"}""");

        assertEquals(List.of(), links(segments), "a URI containing a backslash must not be linked");
        assertEquals("db://a\\b", MAPPER.readTree(concat(segments)).get("uri").stringValue(),
                "the value must still round-trip with its escaping intact");
    }

    @Test
    void concatenatedSegmentsMatchJacksonDefaultPrettyPrinting() {
        // The rendered layout must be visually equivalent to what the display
        // showed before (Jackson's default pretty printer).
        String json = """
                {"name":"test","count":3,"ratio":3.14,"big":9999999999,"flag":true,
                 "nothing":null,"emptyArr":[],"emptyObj":{},
                 "arr":["db://a",1],
                 "nested":{"uri":"db://connection/db1","list":[{"x":1}]}}""";
        JsonNode node = MAPPER.readTree(json);

        String expected = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        assertEquals(expected, concat(JsonSegmentRenderer.render(node)),
                "segment concatenation must equal Jackson's default pretty printing");
    }

    @Test
    void concatenatedSegmentsRoundTripToEquivalentJson() {
        String json = """
                {"uri":"db://x","items":["db://y",{"k":"v"}],"n":1}""";
        JsonNode node = MAPPER.readTree(json);

        JsonNode reparsed = MAPPER.readTree(concat(JsonSegmentRenderer.render(node)));
        assertEquals(node, reparsed, "rendered output must parse back to an equivalent tree");
    }

    @Test
    void nonJsonContentIsASinglePlainTextSegment() {
        List<Segment> segments = JsonSegmentRenderer.render("Error: something went wrong");

        assertEquals(List.of(Segment.text("Error: something went wrong")), segments,
                "non-JSON content must pass through unchanged as plain text");
    }

    @Test
    void malformedJsonIsASinglePlainTextSegment() {
        List<Segment> segments = JsonSegmentRenderer.render("{\"oops\": ");

        assertEquals(List.of(Segment.text("{\"oops\": ")), segments,
                "malformed JSON must pass through unchanged as plain text");
    }

    @Test
    void scalarJsonContentIsTreatedAsPlainText() {
        // Only objects and arrays are re-rendered; a bare scalar (e.g. an error
        // string that happens to parse) is displayed verbatim.
        List<Segment> segments = JsonSegmentRenderer.render("42");

        assertEquals(List.of(Segment.text("42")), segments,
                "a scalar root is not JSON-rendered, it is shown verbatim");
    }

    @Test
    void isNavigableUriAcceptsResourceUriShapes() {
        assertTrue(JsonSegmentRenderer.isNavigableUri("db://connection/db1"));
        assertTrue(JsonSegmentRenderer.isNavigableUri("a://b"), "single-letter scheme is valid");
        assertTrue(JsonSegmentRenderer.isNavigableUri("db2+x.y-z://host"),
                "digits, '+', '.' and '-' are allowed after the first scheme letter");
    }

    @Test
    void isNavigableUriRejectsNonResourceUriShapes() {
        assertFalse(JsonSegmentRenderer.isNavigableUri(null), "null is not navigable");
        assertFalse(JsonSegmentRenderer.isNavigableUri(""), "empty is not navigable");
        assertFalse(JsonSegmentRenderer.isNavigableUri("no scheme"), "plain text is not navigable");
        assertFalse(JsonSegmentRenderer.isNavigableUri("db:x"), "missing '//' is not navigable");
        assertFalse(JsonSegmentRenderer.isNavigableUri("db://"), "empty rest is not navigable");
        assertFalse(JsonSegmentRenderer.isNavigableUri("1db://x"),
                "scheme must start with a lowercase letter");
        assertFalse(JsonSegmentRenderer.isNavigableUri("DB://x"),
                "uppercase scheme is not navigable");
        assertFalse(JsonSegmentRenderer.isNavigableUri("://x"), "empty scheme is not navigable");
    }
}

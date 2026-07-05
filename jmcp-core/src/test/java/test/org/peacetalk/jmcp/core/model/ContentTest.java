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

package test.org.peacetalk.jmcp.core.model;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.Content;
import tools.jackson.databind.ObjectMapper;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

// NOTE: exact-key-name/field-count checks below pin the MCP wire spec (a Java
// field rename could silently break it), NOT Jackson's ability to serialize.
class ContentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCreateTextContent() {
        Content content = Content.text("Hello, world!");

        assertEquals("text", content.type());
        assertEquals("Hello, world!", content.text());
        assertNull(content.data());
        assertNull(content.mimeType());
    }

    @Test
    void testCreateImageContent() {
        Content content = Content.image("base64data", "image/png");

        assertEquals("image", content.type());
        assertNull(content.text());
        assertEquals("base64data", content.data());
        assertEquals("image/png", content.mimeType());
    }

    @Test
    void testTextContentRequiresText() {
        assertThrows(IllegalArgumentException.class, () ->
            new Content("text", null, null, null));
    }

    @Test
    void testImageContentRequiresDataAndMimeType() {
        assertThrows(IllegalArgumentException.class, () ->
            new Content("image", null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
            new Content("image", null, "data", null));
        assertThrows(IllegalArgumentException.class, () ->
            new Content("image", null, null, "image/png"));
    }

    @Test
    void testContentRequiresType() {
        assertThrows(IllegalArgumentException.class, () ->
            new Content(null, "text", null, null));
        assertThrows(IllegalArgumentException.class, () ->
            new Content("", "text", null, null));
        assertThrows(IllegalArgumentException.class, () ->
            new Content("  ", "text", null, null));
    }

    @Test
    void testUnknownTypeRejected() {
        // The type must be exactly "text" or "image"; anything else must be rejected
        // by the compact constructor (catches a regressed / removed enum-style check).
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new Content("video", null, null, null));
        assertTrue(ex.getMessage().contains("video"),
            "Exception message should echo the invalid type: " + ex.getMessage());
    }

    @Test
    void testTextContentSerializesExactShapeAndOmitsNulls() {
        // Serialized text content must contain exactly type + text, with the image-only
        // fields (data, mimeType) omitted because of @JsonInclude(NON_NULL).
        Content content = Content.text("Hello, world!");

        assertThatJson(mapper.writeValueAsString(content))
            .isEqualTo("""
                    {"type":"text","text":"Hello, world!"}""");
    }

    @Test
    void testImageContentSerializesExactShapeAndOmitsNulls() {
        // Serialized image content must contain type + data + mimeType, with text omitted.
        Content content = Content.image("base64data", "image/png");

        assertThatJson(mapper.writeValueAsString(content))
            .isEqualTo("""
                    {"type":"image","data":"base64data","mimeType":"image/png"}""");
    }

    @Test
    void testTextContentRoundTrip() throws Exception {
        Content original = Content.text("round trip me");

        String json = mapper.writeValueAsString(original);
        Content deserialized = mapper.readValue(json, Content.class);

        assertEquals(original, deserialized, "text content must survive a JSON round-trip unchanged");
    }

    @Test
    void testImageContentRoundTrip() throws Exception {
        Content original = Content.image("YWJj", "image/jpeg");

        String json = mapper.writeValueAsString(original);
        Content deserialized = mapper.readValue(json, Content.class);

        assertEquals(original, deserialized, "image content must survive a JSON round-trip unchanged");
    }

    @Test
    void testDeserializeUnknownTypeFails() {
        // Deserializing content with an out-of-range type must be rejected by the
        // compact constructor invoked during construction.
        String json = """
                {"type":"audio","text":"x"}""";
        assertThrows(Exception.class, () -> mapper.readValue(json, Content.class),
            "Deserializing an unknown content type must fail");
    }

    @Test
    void testDeserializeTextTypeWithoutTextFails() {
        // type=text but no text field violates the compact constructor's invariant.
        String json = """
                {"type":"text"}""";
        assertThrows(Exception.class, () -> mapper.readValue(json, Content.class),
            "Deserializing text content without a text field must fail");
    }
}

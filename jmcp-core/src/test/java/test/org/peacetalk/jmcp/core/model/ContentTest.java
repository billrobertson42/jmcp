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

import static org.junit.jupiter.api.Assertions.*;

class ContentTest {

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
}


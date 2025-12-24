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


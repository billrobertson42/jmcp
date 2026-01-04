package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Resource contents in the Model Context Protocol.
 *
 * Corresponds to TypeScript definitions:
 * <pre>
 * interface TextResourceContents {
 *   uri: string;
 *   mimeType?: string;
 *   text: string;
 * }
 *
 * interface BlobResourceContents {
 *   uri: string;
 *   mimeType?: string;
 *   blob: string; // base64-encoded
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/resources/">MCP Resources Specification</a>
 *
 * @param uri The URI of the resource
 * @param mimeType Optional MIME type of the content
 * @param text Text content (for text resources)
 * @param blob Base64-encoded binary content (for binary resources)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceContents(
    @JsonProperty("uri")
    @NotBlank(message = "Resource URI is required")
    String uri,

    @JsonProperty("mimeType")
    String mimeType,

    @JsonProperty("text")
    String text,

    @JsonProperty("blob")
    String blob
) {
    public ResourceContents {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Resource URI cannot be null or blank");
        }
        // Either text or blob must be present, but not both
        if (text == null && blob == null) {
            throw new IllegalArgumentException("Either text or blob must be provided");
        }
        if (text != null && blob != null) {
            throw new IllegalArgumentException("Cannot have both text and blob content");
        }
    }

    /**
     * Create a text resource content
     */
    public static ResourceContents text(String uri, String mimeType, String text) {
        return new ResourceContents(uri, mimeType, text, null);
    }

    /**
     * Create a JSON resource content
     */
    public static ResourceContents json(String uri, String text) {
        return new ResourceContents(uri, "application/json", text, null);
    }

    /**
     * Create a blob resource content
     */
    public static ResourceContents blob(String uri, String mimeType, String base64Blob) {
        return new ResourceContents(uri, mimeType, null, base64Blob);
    }
}


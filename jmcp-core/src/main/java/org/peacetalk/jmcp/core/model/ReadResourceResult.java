package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Result of reading a resource in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ReadResourceResult {
 *   contents: ResourceContents[];
 * }
 *
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
 * @param contents List of resource contents
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadResourceResult(
    @JsonProperty("contents")
    @NotNull(message = "Contents list is required")
    @Valid
    List<ResourceContents> contents
) {
    public ReadResourceResult {
        if (contents == null) {
            contents = List.of();
        }
    }

    /**
     * Create a result with a single text content
     */
    public static ReadResourceResult text(String uri, String mimeType, String text) {
        return new ReadResourceResult(List.of(ResourceContents.text(uri, mimeType, text)));
    }

    /**
     * Create a result with a single text content (JSON mime type)
     */
    public static ReadResourceResult json(String uri, String text) {
        return text(uri, "application/json", text);
    }
}


package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to read a resource in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ReadResourceRequest {
 *   uri: string;
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/resources/">MCP Resources Specification</a>
 *
 * @param uri The URI of the resource to read
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadResourceRequest(
    @JsonProperty("uri")
    @NotBlank(message = "Resource URI is required")
    String uri
) {
    public ReadResourceRequest {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Resource URI cannot be null or blank");
        }
    }
}


package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Client information in the Model Context Protocol initialization.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface Implementation {
 *   name: string;
 *   version: string;
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/basic/lifecycle/">MCP Lifecycle Specification</a>
 *
 * @param name Name of the MCP client implementation
 * @param version Version of the client implementation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Implementation(
    @JsonProperty("name")
    @NotBlank(message = "Implementation name is required")
    String name,

    @JsonProperty("version")
    @NotBlank(message = "Implementation version is required")
    String version
) {
    public Implementation {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Implementation name cannot be null or blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Implementation version cannot be null or blank");
        }
    }
}


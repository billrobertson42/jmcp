package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

/**
 * Request to call a tool in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface CallToolRequest {
 *   method: "tools/call";
 *   params: {
 *     name: string;
 *     arguments?: { [key: string]: unknown };
 *   };
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools Specification</a>
 *
 * @param name The name of the tool to call
 * @param arguments Optional arguments to pass to the tool
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallToolRequest(
    @JsonProperty("name")
    @NotBlank(message = "Tool name is required")
    String name,

    @JsonProperty("arguments")
    JsonNode arguments
) {
    public CallToolRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be null or blank");
        }
    }
}


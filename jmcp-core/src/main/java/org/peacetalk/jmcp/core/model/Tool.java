package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * Tool definition in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface Tool {
 *   name: string;
 *   description?: string;
 *   inputSchema: {
 *     type: "object";
 *     properties?: { [key: string]: unknown };
 *     required?: string[];
 *   };
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools Specification</a>
 *
 * @param name The name of the tool
 * @param description Optional description of what the tool does
 * @param inputSchema JSON Schema for the tool's input parameters (must be an object schema)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Tool(
    @JsonProperty("name")
    @NotBlank(message = "Tool name is required")
    String name,

    @JsonProperty("description")
    String description,

    @JsonProperty("inputSchema")
    @NotNull(message = "Input schema is required")
    JsonNode inputSchema
) {
    public Tool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be null or blank");
        }
        if (inputSchema == null) {
            throw new IllegalArgumentException("Tool inputSchema cannot be null");
        }
    }
}


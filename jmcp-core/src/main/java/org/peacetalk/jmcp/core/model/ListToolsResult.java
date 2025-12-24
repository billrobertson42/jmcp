package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Result of listing available tools in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ListToolsResult {
 *   tools: Tool[];
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools Specification</a>
 *
 * @param tools List of available tools
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListToolsResult(
    @JsonProperty("tools")
    @NotNull(message = "Tools list is required")
    @Valid
    List<Tool> tools
) {
    public ListToolsResult {
        if (tools == null) {
            tools = List.of();
        }
    }
}


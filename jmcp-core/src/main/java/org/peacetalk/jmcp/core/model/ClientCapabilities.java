package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client capabilities in the Model Context Protocol initialization.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ClientCapabilities {
 *   experimental?: { [key: string]: unknown };
 *   sampling?: {};
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/basic/lifecycle/">MCP Lifecycle Specification</a>
 *
 * @param experimental Optional experimental capabilities
 * @param sampling Optional sampling capability marker
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCapabilities(
    @JsonProperty("experimental") Object experimental,
    @JsonProperty("sampling") Object sampling
) {
}


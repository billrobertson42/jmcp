package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Initialize result in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface InitializeResult {
 *   protocolVersion: string;
 *   capabilities: ServerCapabilities;
 *   serverInfo: Implementation;
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/basic/lifecycle/">MCP Lifecycle Specification</a>
 *
 * @param protocolVersion The version of the MCP protocol the server supports
 * @param capabilities Server capabilities
 * @param serverInfo Information about the server implementation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitializeResult(
    @JsonProperty("protocolVersion")
    @NotBlank(message = "Protocol version is required")
    String protocolVersion,

    @JsonProperty("capabilities")
    @NotNull(message = "Capabilities are required")
    @Valid
    ServerCapabilities capabilities,

    @JsonProperty("serverInfo")
    @NotNull(message = "Server info is required")
    @Valid
    Implementation serverInfo
) {
    public InitializeResult {
        if (protocolVersion == null || protocolVersion.isBlank()) {
            throw new IllegalArgumentException("Protocol version cannot be null or blank");
        }
        if (capabilities == null) {
            capabilities = new ServerCapabilities(null, null, null, null, null);
        }
        if (serverInfo == null) {
            throw new IllegalArgumentException("Server info cannot be null");
        }
    }
}


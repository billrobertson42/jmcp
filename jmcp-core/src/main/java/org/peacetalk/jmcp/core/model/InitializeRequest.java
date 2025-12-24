package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Initialize request parameters in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface InitializeRequest {
 *   method: "initialize";
 *   params: {
 *     protocolVersion: string;
 *     capabilities: ClientCapabilities;
 *     clientInfo: Implementation;
 *   };
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/basic/lifecycle/">MCP Lifecycle Specification</a>
 *
 * @param protocolVersion The version of the MCP protocol
 * @param capabilities Client capabilities
 * @param clientInfo Information about the client implementation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitializeRequest(
    @JsonProperty("protocolVersion")
    @NotBlank(message = "Protocol version is required")
    String protocolVersion,

    @JsonProperty("capabilities")
    @NotNull(message = "Capabilities are required")
    @Valid
    ClientCapabilities capabilities,

    @JsonProperty("clientInfo")
    @NotNull(message = "Client info is required")
    @Valid
    Implementation clientInfo
) {
    public InitializeRequest {
        if (protocolVersion == null || protocolVersion.isBlank()) {
            throw new IllegalArgumentException("Protocol version cannot be null or blank");
        }
        if (capabilities == null) {
            capabilities = new ClientCapabilities(null, null);
        }
        if (clientInfo == null) {
            throw new IllegalArgumentException("Client info cannot be null");
        }
    }
}


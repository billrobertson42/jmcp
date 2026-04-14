/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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


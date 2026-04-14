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


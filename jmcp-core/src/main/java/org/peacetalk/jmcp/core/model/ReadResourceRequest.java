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


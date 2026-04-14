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
 * Descriptor for a resource in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface Resource {
 *   uri: string;
 *   name: string;
 *   description?: string;
 *   mimeType?: string;
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/resources/">MCP Resources Specification</a>
 *
 * @param uri The unique URI identifying this resource
 * @param name Human-readable name for the resource
 * @param description Optional description of what the resource represents
 * @param mimeType Optional MIME type of the resource content
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceDescriptor(
    @JsonProperty("uri")
    @NotBlank(message = "Resource URI is required")
    String uri,

    @JsonProperty("name")
    @NotBlank(message = "Resource name is required")
    String name,

    @JsonProperty("description")
    String description,

    @JsonProperty("mimeType")
    String mimeType
) {
    public ResourceDescriptor {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Resource URI cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resource name cannot be null or blank");
        }
    }

    /**
     * Create a descriptor with only required fields
     */
    public static ResourceDescriptor of(String uri, String name) {
        return new ResourceDescriptor(uri, name, null, null);
    }

    /**
     * Create a descriptor with description but no MIME type
     */
    public static ResourceDescriptor of(String uri, String name, String description) {
        return new ResourceDescriptor(uri, name, description, null);
    }
}


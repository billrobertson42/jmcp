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
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Result of reading a resource in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ReadResourceResult {
 *   contents: ResourceContents[];
 * }
 *
 * interface TextResourceContents {
 *   uri: string;
 *   mimeType?: string;
 *   text: string;
 * }
 *
 * interface BlobResourceContents {
 *   uri: string;
 *   mimeType?: string;
 *   blob: string; // base64-encoded
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/resources/">MCP Resources Specification</a>
 *
 * @param contents List of resource contents
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadResourceResult(
    @JsonProperty("contents")
    @NotNull(message = "Contents list is required")
    @Valid
    List<ResourceContents> contents
) {
    public ReadResourceResult {
        if (contents == null) {
            contents = List.of();
        }
    }

    /**
     * Create a result with a single text content
     */
    public static ReadResourceResult text(String uri, String mimeType, String text) {
        return new ReadResourceResult(List.of(ResourceContents.text(uri, mimeType, text)));
    }

    /**
     * Create a result with a single text content (JSON mime type)
     */
    public static ReadResourceResult json(String uri, String text) {
        return text(uri, "application/json", text);
    }
}


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
 * Resource contents in the Model Context Protocol.
 *
 * Corresponds to TypeScript definitions:
 * <pre>
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
 * @param uri The URI of the resource
 * @param mimeType Optional MIME type of the content
 * @param text Text content (for text resources)
 * @param blob Base64-encoded binary content (for binary resources)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceContents(
    @JsonProperty("uri")
    @NotBlank(message = "Resource URI is required")
    String uri,

    @JsonProperty("mimeType")
    String mimeType,

    @JsonProperty("text")
    String text,

    @JsonProperty("blob")
    String blob
) {
    public ResourceContents {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Resource URI cannot be null or blank");
        }
        // Either text or blob must be present, but not both
        if (text == null && blob == null) {
            throw new IllegalArgumentException("Either text or blob must be provided");
        }
        if (text != null && blob != null) {
            throw new IllegalArgumentException("Cannot have both text and blob content");
        }
    }

    /**
     * Create a text resource content
     */
    public static ResourceContents text(String uri, String mimeType, String text) {
        return new ResourceContents(uri, mimeType, text, null);
    }

    /**
     * Create a JSON resource content
     */
    public static ResourceContents json(String uri, String text) {
        return new ResourceContents(uri, "application/json", text, null);
    }

    /**
     * Create a blob resource content
     */
    public static ResourceContents blob(String uri, String mimeType, String base64Blob) {
        return new ResourceContents(uri, mimeType, null, base64Blob);
    }
}


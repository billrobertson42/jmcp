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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Content item in a tool response in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * type TextContent = {
 *   type: "text";
 *   text: string;
 * };
 *
 * type ImageContent = {
 *   type: "image";
 *   data: string;
 *   mimeType: string;
 * };
 *
 * type Content = TextContent | ImageContent;
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools Specification</a>
 *
 * @param type The type of content ("text" or "image")
 * @param text The text content (for type "text")
 * @param data The base64-encoded image data (for type "image")
 * @param mimeType The MIME type of the image (for type "image")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Content(
    @JsonProperty("type")
    @NotBlank(message = "Content type is required")
    @Pattern(regexp = "text|image", message = "Type must be 'text' or 'image'")
    String type,  // TypeScript literal union: "text" | "image"

    @JsonProperty("text")
    String text,

    @JsonProperty("data")
    String data,

    @JsonProperty("mimeType")
    String mimeType
) {
    public Content {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Content type cannot be null or blank");
        }
        if (!type.matches("text|image")) {
            throw new IllegalArgumentException("Content type must be 'text' or 'image', got: " + type);
        }
        if ("text".equals(type) && text == null) {
            throw new IllegalArgumentException("Text content must have text field");
        }
        if ("image".equals(type) && (data == null || mimeType == null)) {
            throw new IllegalArgumentException("Image content must have data and mimeType fields");
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Text content must have text field")
    public boolean isValidTextContent() {
        return !"text".equals(type) || text != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Image content must have data and mimeType")
    public boolean isValidImageContent() {
        return !"image".equals(type) || (data != null && mimeType != null);
    }

    /**
     * Create text content
     */
    public static Content text(String text) {
        return new Content("text", text, null, null);
    }

    /**
     * Create image content
     */
    public static Content image(String data, String mimeType) {
        return new Content("image", null, data, mimeType);
    }
}


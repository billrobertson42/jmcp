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
 * Result of calling a tool in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface CallToolResult {
 *   content: Content[];
 *   isError?: boolean;
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools Specification</a>
 *
 * @param content Array of content items returned by the tool
 * @param isError Whether the tool execution resulted in an error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallToolResult(
    @JsonProperty("content")
    @NotNull(message = "Content array is required")
    @Valid
    List<Content> content,

    @JsonProperty("isError")
    Boolean isError
) {
    public CallToolResult {
        if (content == null) {
            content = List.of();
        }
    }

    /**
     * Create a successful tool result with a single text content
     */
    public static CallToolResult text(String text) {
        return new CallToolResult(List.of(Content.text(text)), null);
    }

    /**
     * Create a successful tool result with multiple content items
     */
    public static CallToolResult of(List<Content> content) {
        return new CallToolResult(content, null);
    }

    /**
     * Create an error tool result
     */
    public static CallToolResult error(String errorMessage) {
        return new CallToolResult(List.of(Content.text(errorMessage)), true);
    }
}


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

/**
 * Client capabilities in the Model Context Protocol initialization.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ClientCapabilities {
 *   experimental?: { [key: string]: unknown };
 *   sampling?: {};
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/basic/lifecycle/">MCP Lifecycle Specification</a>
 *
 * @param experimental Optional experimental capabilities
 * @param sampling Optional sampling capability marker
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCapabilities(
    @JsonProperty("experimental") Object experimental,
    @JsonProperty("sampling") Object sampling
) {
}


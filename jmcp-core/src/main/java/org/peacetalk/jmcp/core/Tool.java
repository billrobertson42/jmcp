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

package org.peacetalk.jmcp.core;

import tools.jackson.databind.JsonNode;

/**
 * Generic interface for MCP tools
 */
public interface Tool {
    /**
     * Get the unique name of this tool
     */
    String getName();

    /**
     * Get a human-readable description of what this tool does
     */
    String getDescription();

    /**
     * Get the JSON schema describing the input parameters for this tool
     */
    JsonNode getInputSchema();

    /**
     * Execute the tool with the given parameters
     *
     * @param params the input parameters as a JsonNode
     * @return the result of tool execution (will be serialized to JSON)
     * @throws Exception if tool execution fails
     */
    Object execute(JsonNode params) throws Exception;
}


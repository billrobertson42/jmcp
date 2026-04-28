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

package org.peacetalk.jmcp.jdbc;


import tools.jackson.databind.JsonNode;

/**
 * Definition of a JDBC tool for MCP
 */
public interface JdbcTool {
    /**
     * Get the tool name
     */
    String getName();

    /**
     * Get the tool description
     */
    String getDescription();

    /**
     * Get the JSON schema for tool input parameters
     */
    JsonNode getInputSchema();

    /**
     * Execute the tool with given parameters and connection
     */
    Object execute(JsonNode params, ConnectionSupplier context) throws Exception;
}


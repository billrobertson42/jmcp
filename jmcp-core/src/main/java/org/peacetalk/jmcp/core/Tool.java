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


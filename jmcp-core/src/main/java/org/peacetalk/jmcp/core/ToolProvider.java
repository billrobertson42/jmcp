package org.peacetalk.jmcp.core;

import java.util.List;

/**
 * Interface for components that provide MCP tools.
 *
 * Tool providers are responsible for:
 * - Initializing their tools and dependencies
 * - Providing access to the tools they manage
 * - Cleaning up resources when no longer needed
 */
public interface ToolProvider {
    /**
     * Initialize the tool provider with the given configuration.
     *
     * @throws Exception if initialization fails
     */
    void initialize() throws Exception;

    /**
     * Get all tools provided by this provider
     *
     * @return list of tools
     */
    List<Tool> getTools();

    /**
     * Clean up resources used by this provider
     */
    void shutdown();

    /**
     * Get a human-readable name for this provider
     *
     * @return provider name
     */
    String getName();
}


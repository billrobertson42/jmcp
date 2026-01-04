package org.peacetalk.jmcp.core;

import java.util.List;

/**
 * Interface for components that provide MCP resources.
 *
 * Resource providers support a navigational/HATEOAS-like approach where:
 * - The provider can list root resources
 * - Resources can be looked up by URI
 * - Resource URIs can include path segments and parameters
 *
 * URI patterns follow a hierarchical structure:
 * - scheme://root-collection
 * - scheme://root-collection/{id}
 * - scheme://root-collection/{id}/sub-collection
 */
public interface ResourceProvider {
    /**
     * Initialize the resource provider.
     *
     * @throws Exception if initialization fails
     */
    void initialize() throws Exception;

    /**
     * List resources, optionally filtered by a URI pattern.
     *
     * If no cursor is provided, returns all root-level resources.
     * If a cursor is provided, returns resources starting from that point
     * (for pagination support).
     *
     * @param cursor Optional pagination cursor
     * @return list of available resources
     */
    List<Resource> listResources(String cursor);

    /**
     * Get a resource by its URI.
     *
     * URIs follow a hierarchical pattern like:
     * - db://connections
     * - db://connection/mydb
     * - db://connection/mydb/schemas
     * - db://connection/mydb/schema/public/tables
     * - db://connection/mydb/schema/public/table/users
     *
     * @param uri The resource URI
     * @return The resource, or null if not found
     */
    Resource getResource(String uri);

    /**
     * Check if this provider can handle resources with the given URI scheme.
     *
     * @param scheme The URI scheme (e.g., "jdbc")
     * @return true if this provider handles the scheme
     */
    boolean supportsScheme(String scheme);

    /**
     * Clean up resources used by this provider.
     */
    void shutdown();

    /**
     * Get a human-readable name for this provider.
     *
     * @return provider name
     */
    String getName();
}


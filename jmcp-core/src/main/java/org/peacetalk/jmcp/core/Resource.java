package org.peacetalk.jmcp.core;

/**
 * Interface for MCP resources.
 *
 * Resources in MCP represent data that can be read by clients.
 * Resources support a navigational/HATEOAS-like approach where
 * reading a resource may return URIs to related resources.
 */
public interface Resource {
    /**
     * Get the unique URI for this resource.
     * URIs follow the format: scheme://path
     * For example: db://connections, db://connection/{id}/schemas
     *
     * @return the resource URI
     */
    String getUri();

    /**
     * Get a human-readable name for this resource
     *
     * @return the resource name
     */
    String getName();

    /**
     * Get a description of what this resource represents
     *
     * @return the resource description, or null if not available
     */
    String getDescription();

    /**
     * Get the MIME type of the resource content
     *
     * @return MIME type (e.g., "application/json", "text/plain")
     */
    String getMimeType();

    /**
     * Read the resource content.
     *
     * @return the resource content as a string
     * @throws Exception if reading fails
     */
    String read() throws Exception;
}


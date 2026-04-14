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


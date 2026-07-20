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

package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;

import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.*;

/**
 * Resource providing foreign key relationships for a specific schema.
 *
 * URI: db://connection/{id}/schema/{schema}/relationships
 *
 * This resource provides all foreign key relationships where at least one
 * of the tables (source or target) is in the specified schema. This is useful
 * for projects that work within a single schema and want to understand
 * dependencies without the noise of unrelated schemas.
 *
 * Includes:
 * - Relationships between tables within the schema
 * - Relationships from tables in this schema to tables in other schemas
 * - Relationships from tables in other schemas to tables in this schema
 */
public class SchemaRelationshipsResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final ConnectionManager connectionManager;

    public SchemaRelationshipsResource(String connectionId, String schemaName,
                                      ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return schemaRelationshipsUri(connectionId, schemaName);
    }

    @Override
    public String getName() {
        return "Relationships for schema: " + schemaName;
    }

    @Override
    public String getDescription() {
        return "FK relationships involving this schema with copyOrder for dependency-safe operations.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);
        RelationshipGraphBuilder.Graph graph = RelationshipGraphBuilder.build(connectionId, context, schemaName);

        SchemaRelationshipsResponse response = new SchemaRelationshipsResponse(
            connectionId,
            schemaName,
            graph.relationships(),
            graph.copyOrder(),
            graph.cyclesDetected(),
            new NavigationLinks(
                schemaUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record SchemaRelationshipsResponse(
        String connectionId,
        String schema,
        List<Relationship> relationships,
        List<String> copyOrder,
        List<String> cyclesDetected,
        NavigationLinks links
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}

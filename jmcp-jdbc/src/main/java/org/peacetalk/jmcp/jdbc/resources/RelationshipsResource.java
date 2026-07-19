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
 * Resource providing a complete foreign key relationship graph for a database connection.
 *
 * URI: db://connection/{id}/relationships
 *
 * This resource provides a comprehensive view of all foreign key relationships across
 * all schemas in the database. Useful for understanding database structure, generating
 * ER diagrams, and analyzing table dependencies.
 */
public class RelationshipsResource implements Resource {
    private final String connectionId;
    private final ConnectionManager connectionManager;

    public RelationshipsResource(String connectionId, ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return relationshipsUri(connectionId);
    }

    @Override
    public String getName() {
        return "Relationships for connection: " + connectionId;
    }

    @Override
    public String getDescription() {
        return "FK relationship graph with copyOrder (topological sort for dependency-safe data operations).";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);
        RelationshipGraphBuilder.Graph graph = RelationshipGraphBuilder.build(connectionId, context, null);

        RelationshipsResponse response = new RelationshipsResponse(
            connectionId,
            graph.relationships(),
            graph.copyOrder(),
            graph.cyclesDetected(),
            new NavigationLinks(
                connectionUri(connectionId)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record RelationshipsResponse(
        String connectionId,
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

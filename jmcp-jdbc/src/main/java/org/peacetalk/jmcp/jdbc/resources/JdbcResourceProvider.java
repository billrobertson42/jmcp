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
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.routing.ResourceRoutes;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource provider for JDBC database resources.
 *
 * Provides a navigational/HATEOAS-like resource hierarchy:
 * <ul>
 *   <li>db://connections - List of all database connections</li>
 *   <li>db://connection/{id} - Details of a specific connection with links to sub-resources</li>
 *   <li>db://connection/{id}/schemas - List of schemas in the connection</li>
 *   <li>db://connection/{id}/schema/{schema} - Schema details with links to tables/views</li>
 *   <li>db://connection/{id}/schema/{schema}/tables - Tables in the schema</li>
 *   <li>db://connection/{id}/schema/{schema}/table/{table} - Table structure details</li>
 * </ul>
 */
public class JdbcResourceProvider implements ResourceProvider {

    private ConnectionManager connectionManager;
    private ResourceRoutes routes;

    /**
     * Set the connection manager to use for database operations
     */
    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void initialize() throws Exception {
        // Connection manager is set externally
        if (connectionManager == null) {
            throw new IllegalStateException("ConnectionManager must be set before initialization");
        }
        routes = buildRoutes();
    }

    /**
     * Declares every {@code db://...} URI shape this provider serves. Built only after
     * {@link #connectionManager} is set (see {@link #initialize()}), since every route factory
     * closes over it and the {@code id} guard calls {@link #connectionExists(String)}.
     */
    private ResourceRoutes buildRoutes() {
        return ResourceRoutes.forScheme(Util.SCHEME)
                .guard("id", this::connectionExists)

                .route("context", p -> new ContextResource(connectionManager))
                .route("connections", p -> new ConnectionsListResource(connectionManager))

                .route("connection/:id",
                        p -> new ConnectionResource(p.get("id"), connectionManager))
                .route("connection/:id/schemas",
                        p -> new SchemasListResource(p.get("id"), connectionManager))
                .route("connection/:id/relationships",
                        p -> new RelationshipsResource(p.get("id"), connectionManager))

                .route("connection/:id/schema/:schema",
                        p -> new SchemaResource(p.get("id"), p.get("schema"), connectionManager))
                .route("connection/:id/schema/:schema/relationships",
                        p -> new SchemaRelationshipsResource(p.get("id"), p.get("schema"), connectionManager))
                .route("connection/:id/schema/:schema/tables",
                        p -> new TablesListResource(p.get("id"), p.get("schema"), connectionManager))
                .route("connection/:id/schema/:schema/views",
                        p -> new ViewsListResource(p.get("id"), p.get("schema"), connectionManager))

                .route("connection/:id/schema/:schema/table/:table",
                        p -> new TableResource(p.get("id"), p.get("schema"), p.get("table"), connectionManager))
                .route("connection/:id/schema/:schema/view/:view",
                        p -> new ViewResource(p.get("id"), p.get("schema"), p.get("view"), connectionManager, Util.MAPPER))
                .route("connection/:id/schema/:schema/procedure/:proc",
                        p -> new ProcedureResource(p.get("id"), p.get("schema"), p.get("proc"), connectionManager))

                .build();
    }

    @Override
    public List<Resource> listResources(String cursor) {
        // Return root-level resources - the list of connections
        List<Resource> resources = new ArrayList<>();

        // Add the context resource first - this is the primary resource for LLM consumption
        // It provides a comprehensive summary of all connections, schemas, tables, and available tools
        resources.add(new ContextResource(connectionManager));

        // Add the connections list resource for HATEOAS-style navigation
        resources.add(new ConnectionsListResource(connectionManager));

        // Add individual connection resources
        for (ConnectionInfo conn : connectionManager.listConnections()) {
            resources.add(new ConnectionResource(conn.id(), connectionManager));
        }

        return resources;
    }

    @Override
    public Resource getResource(String uri) {
        return routes.resolve(uri);
    }

    private boolean connectionExists(String connectionId) {
        return connectionManager.listConnections().stream()
            .anyMatch(conn -> conn.id().equals(connectionId));
    }

    @Override
    public boolean supportsScheme(String scheme) {
        return routes.scheme().equals(scheme);
    }

    @Override
    public void shutdown() {
        // Connection manager shutdown is handled by JdbcToolProvider
    }

    @Override
    public String getName() {
        return "JDBC Database Resources";
    }
}


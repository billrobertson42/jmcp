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
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.peacetalk.jmcp.jdbc.resources.Util.MAPPER;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionsUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionSchemasUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.relationshipsUri;

/**
 * Resource representing a specific database connection.
 *
 * URI: db://connection/{id}
 *
 * Returns connection details with navigation links to schemas and other sub-resources.
 */
public class ConnectionResource implements Resource {
    private final String connectionId;
    private final ConnectionManager connectionManager;

    public ConnectionResource(String connectionId, ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return connectionUri(connectionId);
    }

    @Override
    public String getName() {
        return "Connection: " + connectionId;
    }

    @Override
    public String getDescription() {
        return "Connection metadata with navigation to schemas and relationships.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionInfo info = connectionManager.listConnections().stream()
            .filter(c -> c.id().equals(connectionId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

        ConnectionContext context = connectionManager.getContext(connectionId);
        DatabaseInfo dbInfo;

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            dbInfo = new DatabaseInfo(
                metaData.getDatabaseProductName(),
                metaData.getDatabaseProductVersion(),
                metaData.getDriverName(),
                metaData.getDriverVersion(),
                conn.getCatalog(),
                conn.getSchema()
            );
        }

        ConnectionResponse response = new ConnectionResponse(
            info.id(),
            info.databaseType(),
            info.url(),
            info.username(),
            dbInfo,
            new NavigationLinks(
                connectionsUri(),
                connectionSchemasUri(connectionId),
                relationshipsUri(connectionId)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record ConnectionResponse(
        String id,
        String databaseType,
        String url,
        String username,
        DatabaseInfo database,
        NavigationLinks links
    ) {}

    /**
     * Database metadata information
     */
    public record DatabaseInfo(
        String productName,
        String productVersion,
        String driverName,
        String driverVersion,
        String currentCatalog,
        String currentSchema
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent,
        String schemas,
        String relationships
    ) {}
}


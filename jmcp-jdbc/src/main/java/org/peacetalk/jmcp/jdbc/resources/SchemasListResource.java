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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.*;

/**
 * Resource representing the list of schemas in a database connection.
 *
 * URI: db://connection/{id}/schemas
 *
 * Returns a navigable list of schemas with links to each schema resource.
 */
public class SchemasListResource implements Resource {
    private final String connectionId;
    private final ConnectionManager connectionManager;

    public SchemasListResource(String connectionId, ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return connectionSchemasUri(connectionId);
    }

    @Override
    public String getName() {
        return "Schemas in " + connectionId;
    }

    @Override
    public String getDescription() {
        return "List of schemas with navigation links and metadata.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<SchemaEntry> schemas = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String defaultSchema = conn.getSchema();

            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    String catalogName = rs.getString("TABLE_CATALOG");
                    boolean isDefault = schemaName != null && schemaName.equals(defaultSchema);

                    schemas.add(new SchemaEntry(
                        schemaName,
                        catalogName,
                        isDefault,
                        schemaUri(connectionId, schemaName),
                        schemaTablesUri(connectionId, schemaName)
                    ));
                }
            }
        }

        SchemasResponse response = new SchemasResponse(
            schemas,
            new NavigationLinks(
                connectionUri(connectionId)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record SchemasResponse(
        List<SchemaEntry> schemas,
        NavigationLinks links
    ) {}

    /**
     * Schema entry with navigation links
     */
    public record SchemaEntry(
        String name,
        String catalog,
        boolean isDefault,
        String resourceUri,
        String tablesUri
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}

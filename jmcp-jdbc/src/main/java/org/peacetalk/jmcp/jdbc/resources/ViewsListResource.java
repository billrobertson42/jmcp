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
 * Resource representing the list of views in a schema.
 *
 * URI: db://connection/{id}/schema/{schema}/views
 *
 * Returns a navigable list of views with links to each view resource.
 */
public class ViewsListResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final ConnectionManager connectionManager;

    public ViewsListResource(String connectionId, String schemaName,
                             ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return schemaViewsUri(connectionId, schemaName);
    }

    @Override
    public String getName() {
        return "Views in " + schemaName;
    }

    @Override
    public String getDescription() {
        return "List of views in the schema. Navigate to individual views for column details.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<ViewEntry> views = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet rs = metaData.getTables(null, schemaName, "%", new String[]{"VIEW"})) {
                while (rs.next()) {
                    String viewName = rs.getString("TABLE_NAME");
                    String remarks = rs.getString("REMARKS");
                    String tableType = rs.getString("TABLE_TYPE");

                    views.add(new ViewEntry(
                        viewName,
                        tableType,
                        remarks,
                        viewUri(connectionId, schemaName, viewName)
                    ));
                }
            }
        }

        ViewsResponse response = new ViewsResponse(
            views,
            new NavigationLinks(
                schemaUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record ViewsResponse(
        List<ViewEntry> views,
        NavigationLinks links
    ) {}

    /**
     * View entry with navigation links
     */
    public record ViewEntry(
        String name,
        String type,
        String remarks,
        String resourceUri
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}


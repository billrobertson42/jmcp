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
 * Resource representing the list of tables in a schema.
 *
 * URI: db://connection/{id}/schema/{schema}/tables
 *
 * Returns a navigable list of tables with links to each table resource.
 */
public class TablesListResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final ConnectionManager connectionManager;

    public TablesListResource(String connectionId, String schemaName,
                              ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return schemaTablesUri(connectionId, schemaName);
    }

    @Override
    public String getName() {
        return "Tables in " + schemaName;
    }

    @Override
    public String getDescription() {
        return "List of tables in the schema. Navigate to individual tables for column and constraint details.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<TableEntry> tables = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet rs = metaData.getTables(null, schemaName, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String remarks = rs.getString("REMARKS");
                    String tableType = rs.getString("TABLE_TYPE");

                    tables.add(new TableEntry(
                        tableName,
                        tableType,
                        remarks,
                        tableUri(connectionId, schemaName, tableName)
                    ));
                }
            }
        }

        TablesResponse response = new TablesResponse(
            tables,
            tables.size(),
            new NavigationLinks(
                schemaUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record TablesResponse(
        List<TableEntry> tables,
        int count,
        NavigationLinks links
    ) {}

    /**
     * Table entry with navigation links
     */
    public record TableEntry(
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


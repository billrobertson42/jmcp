package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.MAPPER;
import static org.peacetalk.jmcp.jdbc.resources.Util.SCHEME;

/**
 * Resource representing a specific view in a schema.
 *
 * URI: db://connection/{id}/schema/{schema}/view/{view}
 *
 * Returns detailed view information including columns.
 */
public class ViewResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final String viewName;
    private final ConnectionManager connectionManager;

    public ViewResource(String connectionId, String schemaName, String viewName,
                        ConnectionManager connectionManager, ObjectMapper mapper) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.viewName = viewName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/view/" + viewName;
    }

    @Override
    public String getName() {
        return "View: " + viewName;
    }

    @Override
    public String getDescription() {
        return "View structure details including columns.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<ColumnInfo> columns = new ArrayList<>();
        String viewRemarks = null;

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get view remarks
            try (ResultSet rs = metaData.getTables(null, schemaName, viewName, new String[]{"VIEW"})) {
                if (rs.next()) {
                    viewRemarks = rs.getString("REMARKS");
                }
            }

            // Get columns
            try (ResultSet rs = metaData.getColumns(null, schemaName, viewName, "%")) {
                while (rs.next()) {
                    columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS"),
                        "YES".equals(rs.getString("IS_NULLABLE")),
                        rs.getString("REMARKS")
                    ));
                }
            }
        }

        ViewResponse response = new ViewResponse(
            viewName,
            schemaName,
            connectionId,
            viewRemarks,
            columns,
            new NavigationLinks(
                SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/views"
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record ViewResponse(
        String name,
        String schema,
        String connectionId,
        String remarks,
        List<ColumnInfo> columns,
        NavigationLinks links
    ) {}

    /**
     * Column information
     */
    public record ColumnInfo(
        String name,
        String dataType,
        int size,
        int decimalDigits,
        boolean nullable,
        String remarks
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}


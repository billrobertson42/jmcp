package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.JdbcUrlSanitizer;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.*;

/**
 * Resource providing a comprehensive context summary for LLM consumption.
 *
 * URI: db://context
 *
 * This resource is designed to give an LLM all the context it needs in a single
 * request to understand the available databases, their structure, and which
 * tools can be used to interact with them.
 *
 * Unlike the HATEOAS-style navigational resources, this resource provides a
 * flattened, comprehensive view optimized for LLM context windows.
 */
public class ContextResource implements Resource {
    private final ConnectionManager connectionManager;

    public ContextResource(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return contextUri();
    }

    @Override
    public String getName() {
        return "Database Context Summary";
    }

    @Override
    public String getDescription() {
        return "Comprehensive summary of all database connections, schemas, and tables. " +
               "Use this resource to understand what databases are available and how to query them.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        List<ConnectionSummary> connections = new ArrayList<>();

        for (ConnectionInfo info : connectionManager.listConnections()) {
            ConnectionContext context = connectionManager.getContext(info.id());
            List<SchemaSummary> schemas = new ArrayList<>();

            try (Connection conn = context.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String defaultSchema = conn.getSchema();

                // Get all schemas
                try (ResultSet rs = metaData.getSchemas()) {
                    while (rs.next()) {
                        String schemaName = rs.getString("TABLE_SCHEM");
                        if (schemaName == null) continue;

                        // Count tables and views for this schema
                        int tableCount = 0;
                        int viewCount = 0;
                        List<String> tableNames = new ArrayList<>();
                        List<String> viewNames = new ArrayList<>();

                        try (ResultSet tables = metaData.getTables(null, schemaName, "%", new String[]{"TABLE"})) {
                            while (tables.next()) {
                                tableCount++;
                                if (tableCount <= 20) { // Limit to first 20 table names
                                    tableNames.add(tables.getString("TABLE_NAME"));
                                }
                            }
                        }

                        try (ResultSet views = metaData.getTables(null, schemaName, "%", new String[]{"VIEW"})) {
                            while (views.next()) {
                                viewCount++;
                                if (viewCount <= 10) { // Limit to first 10 view names
                                    viewNames.add(views.getString("TABLE_NAME"));
                                }
                            }
                        }

                        boolean isDefault = schemaName.equals(defaultSchema);
                        schemas.add(new SchemaSummary(
                            schemaName,
                            isDefault,
                            tableCount,
                            viewCount,
                            tableNames,
                            viewNames,
                            schemaUri(info.id(), schemaName)
                        ));
                    }
                }
            }

            connections.add(new ConnectionSummary(
                info.id(),
                info.databaseType(),
                JdbcUrlSanitizer.getExposableUrl(info.url(), connectionManager.isExposeUrls()),
                info.username(),
                schemas
            ));
        }

        ContextResponse response = new ContextResponse(
            connections,
            getToolsInfo(),
            getResourceInfo(),
            getUsageHints()
        );

        return MAPPER.writeValueAsString(response);
    }

    private List<ToolInfo> getToolsInfo() {
        return List.of(
            new ToolInfo(
                "query",
                "Execute a read-only SQL SELECT query",
                "Use database_id from connections list. Returns tabular data.",
                List.of("database_id (required)", "sql (required)", "max_rows (optional, default 100)")
            ),
            new ToolInfo(
                "list-tables",
                "List all tables in a schema",
                "Returns table names with row counts. Use to discover available tables.",
                List.of("database_id (required)", "schema (optional, uses default)")
            ),
            new ToolInfo(
                "describe-table",
                "Get detailed table structure",
                "Returns columns, types, primary keys, foreign keys, indexes.",
                List.of("database_id (required)", "table (required)", "schema (optional)")
            ),
            new ToolInfo(
                "list-schemas",
                "List all schemas in a database",
                "Returns schema names. Use to discover available schemas.",
                List.of("database_id (required)")
            ),
            new ToolInfo(
                "list-views",
                "List all views in a schema",
                "Returns view names. Views are virtual tables defined by queries.",
                List.of("database_id (required)", "schema (optional)")
            ),
            new ToolInfo(
                "get-row-count",
                "Get the number of rows in a table",
                "Fast count of table rows.",
                List.of("database_id (required)", "table (required)", "schema (optional)")
            ),
            new ToolInfo(
                "explain-query",
                "Get the execution plan for a SQL query",
                "Shows how the database will execute a query. Useful for optimization.",
                List.of("database_id (required)", "sql (required)")
            )
        );
    }

    private ResourceNavigationInfo getResourceInfo() {
        List<ResourceTemplate> templates = List.of(
            new ResourceTemplate(
                "db://connections",
                "List of all database connections",
                "Root resource listing all available connections"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}",
                "Connection details",
                "Database metadata and navigation links"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/relationships",
                "Complete FK relationship graph",
                "All foreign key relationships across all schemas in the database"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schemas",
                "List of schemas",
                "All schemas in this database connection"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}",
                "Schema details",
                "Schema with lists of all tables and views (includes URIs for direct navigation)"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}/relationships",
                "Schema FK relationships",
                "All foreign key relationships involving tables in this schema (including cross-schema FKs)"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}/table/{table_name}",
                "Table structure",
                "Detailed table metadata: columns, PKs, FKs (both directions), indexes"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}/view/{view_name}",
                "View definition",
                "View structure and SQL definition"
            )
        );

        List<String> navigationExamples = new ArrayList<>();
        for (ConnectionInfo info : connectionManager.listConnections()) {
            navigationExamples.add("db://connection/" + info.id() + "/relationships");
            break; // Just show one example
        }

        return new ResourceNavigationInfo(
            templates,
            navigationExamples,
            List.of(
                "Resources are cacheable - they represent database metadata that changes infrequently",
                "Use db://context (this resource) for a complete overview without navigation",
                "For specific details, navigate the resource hierarchy or use tools",
                "Table resources include both imported FKs (this table references X) and exported FKs (X references this table)",
                "The relationships resource provides the complete FK graph across all schemas"
            )
        );
    }

    private List<String> getUsageHints() {
        return List.of(
            "Use database_id (not connection name) when calling tools",
            "If schema is not specified, the database's default schema is used",
            "The query tool only allows SELECT statements for safety",
            "Use describe-table to understand table structure before writing queries",
            "Foreign keys in describe-table output show relationships between tables",
            "Resources provide cacheable metadata; tools perform operations"
        );
    }

    /**
     * Complete context response
     */
    public record ContextResponse(
        List<ConnectionSummary> connections,
        List<ToolInfo> availableTools,
        ResourceNavigationInfo resources,
        List<String> usageHints
    ) {}

    /**
     * Resource navigation information
     */
    public record ResourceNavigationInfo(
        List<ResourceTemplate> uriTemplates,
        List<String> examples,
        List<String> navigationHints
    ) {}

    /**
     * Resource URI template with description
     */
    public record ResourceTemplate(
        String uriPattern,
        String name,
        String description
    ) {}

    /**
     * Summary of a database connection
     */
    public record ConnectionSummary(
        String databaseId,
        String databaseType,
        String url,
        String username,
        List<SchemaSummary> schemas
    ) {}

    /**
     * Summary of a schema with table/view counts and names
     */
    public record SchemaSummary(
        String name,
        boolean isDefault,
        int tableCount,
        int viewCount,
        List<String> tables,
        List<String> views,
        String uri
    ) {}

    /**
     * Tool information for LLM context
     */
    public record ToolInfo(
        String name,
        String description,
        String usage,
        List<String> parameters
    ) {}
}


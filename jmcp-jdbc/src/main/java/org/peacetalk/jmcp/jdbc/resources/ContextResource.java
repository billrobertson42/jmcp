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
        return "Complete overview: all connections, schemas, tables, tools, and resources.";
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
                "Execute read-only SELECT query",
                "Returns up to 1000 rows in compact array format.",
                List.of("sql", "parameters", "database_id")
            ),
            new ToolInfo(
                "get-row-count",
                "Get exact row count for a table",
                "Returns exact number of rows.",
                List.of("table", "schema", "database_id")
            ),
            new ToolInfo(
                "sample-data",
                "Get sample rows from a table",
                "Strategies: 'first', 'random', 'last'. Max 100 rows.",
                List.of("table", "schema", "sample_size", "strategy", "columns", "database_id")
            ),
            new ToolInfo(
                "analyze-column",
                "Analyze column data distribution",
                "Returns distinct count, nulls, min/max, top values.",
                List.of("table", "column", "schema", "top_values", "database_id")
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
                "Resources are cacheable metadata",
                "Table resources include FKs in both directions",
                "Relationships resource provides complete FK graph"
            )
        );
    }

    private List<String> getUsageHints() {
        return List.of(
            "Start with db://context for complete overview",
            "Use resources for structure (cacheable metadata)",
            "Use tools for operations (query, count, sample, analyze)",
            "Navigate: db://connections → db://connection/{id} → schema/{schema} → table/{table}",
            "Query tool accepts SELECT only",
            "Schema defaults to connection's default if not specified"
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


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
import org.peacetalk.jmcp.jdbc.ConnectionSupplier;
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
            ConnectionSupplier context = connectionManager.getContext(info.id());
            List<SchemaSummary> schemas = new ArrayList<>();

            try (Connection conn = context.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String defaultSchema = conn.getSchema();

                // Get all schemas
                try (ResultSet rs = metaData.getSchemas()) {
                    while (rs.next()) {
                        String schemaName = rs.getString("TABLE_SCHEM");
                        if (schemaName == null) continue;

                        // Count tables, views, and procedures for this schema
                        int tableCount = 0;
                        int viewCount = 0;
                        int procedureCount = 0;
                        List<String> tableNames = new ArrayList<>();
                        List<String> viewNames = new ArrayList<>();
                        List<String> procedureNames = new ArrayList<>();

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

                        try (ResultSet procs = metaData.getProcedures(null, schemaName, "%")) {
                            while (procs.next()) {
                                procedureCount++;
                                if (procedureCount <= 10) { // Limit to first 10 procedure names
                                    procedureNames.add(procs.getString("PROCEDURE_NAME"));
                                }
                            }
                        }

                        boolean isDefault = schemaName.equals(defaultSchema);
                        schemas.add(new SchemaSummary(
                            schemaName,
                            isDefault,
                            tableCount,
                            viewCount,
                            procedureCount,
                            tableNames,
                            viewNames,
                            procedureNames,
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
                "Returns up to 1000 rows. Use validate_only=true to check syntax.",
                List.of("sql", "parameters", "validate_only", "database_id")
            ),
            new ToolInfo(
                "explain-query",
                "Get query execution plan",
                "Shows how database will execute the query.",
                List.of("sql", "database_id")
            ),
            new ToolInfo(
                "get-row-count",
                "Get exact row count for a table",
                "Returns exact total row count. For filtered counts, use query tool with SELECT COUNT(*).",
                List.of("table", "schema", "database_id")
            ),
            new ToolInfo(
                "sample-data",
                "Get sample rows from a table",
                "Preview actual data values. Strategies: 'first', 'random', 'last'. Max 100 rows.",
                List.of("table", "schema", "sample_size", "strategy", "columns", "database_id")
            ),
            new ToolInfo(
                "analyze-column",
                "Analyze column data distribution",
                "Data profiling: distinct count, nulls, min/max, top values with frequencies.",
                List.of("table", "column", "schema", "top_values", "database_id")
            )
        );
    }

    private ResourceNavigationInfo getResourceInfo() {
        List<ResourceTemplate> templates = List.of(
            new ResourceTemplate(
                "db://connections",
                "List of all database connections",
                "Root resource listing all available connections with navigation URIs"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}",
                "Connection details",
                "Database metadata with links to schemas and relationships"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/relationships",
                "Complete FK relationship graph",
                "All FK relationships with topological copy order for dependency-safe data operations"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schemas",
                "List of schemas",
                "All schemas in this database connection"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}",
                "Schema details",
                "Lists all tables, views, procedures with navigation URIs"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}/relationships",
                "Schema FK relationships",
                "FK relationships within and across schema boundaries with copy order"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}/table/{table_name}",
                "Table structure",
                "Columns, PKs, indexes, foreignKeys (what this table references), reverseForeignKeys (what references this table)"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}/view/{view_name}",
                "View definition",
                "View columns and SQL definition"
            ),
            new ResourceTemplate(
                "db://connection/{database_id}/schema/{schema_name}/procedure/{procedure_name}",
                "Procedure/Function details",
                "Parameters, return type, definition"
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
                "Table.foreignKeys = tables THIS table references",
                "Table.reverseForeignKeys = tables that REFERENCE this table",
                "Relationships resource includes copy order for data operations"
            )
        );
    }

    private List<String> getUsageHints() {
        return List.of(
            "db://context provides complete overview in one request",
            "For FK discovery: use table resource (foreignKeys + reverseForeignKeys)",
            "For copy order: use relationships resource (topologically sorted)",
            "For filtered counts: use query tool with SELECT COUNT(*) ... WHERE",
            "Resources = schema structure; Tools = data operations",
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
     * Summary of a schema with table/view/procedure counts and names
     */
    public record SchemaSummary(
        String name,
        boolean isDefault,
        int tableCount,
        int viewCount,
        int procedureCount,
        List<String> tables,
        List<String> views,
        List<String> procedures,
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


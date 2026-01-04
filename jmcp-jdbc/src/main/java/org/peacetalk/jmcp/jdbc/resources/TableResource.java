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
 * Resource representing a specific table in a schema.
 *
 * URI: db://connection/{id}/schema/{schema}/table/{table}
 *
 * Returns detailed table information including columns, primary keys, indexes, and foreign keys.
 */
public class TableResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final String tableName;
    private final ConnectionManager connectionManager;

    public TableResource(String connectionId, String schemaName, String tableName,
                         ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/table/" + tableName;
    }

    @Override
    public String getName() {
        return "Table: " + tableName;
    }

    @Override
    public String getDescription() {
        return "Table structure details including columns, primary keys, indexes, and foreign keys.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<ColumnInfo> columns = new ArrayList<>();
        List<String> primaryKey = new ArrayList<>();
        List<IndexInfo> indexes = new ArrayList<>();
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get columns
            try (ResultSet rs = metaData.getColumns(null, schemaName, tableName, "%")) {
                while (rs.next()) {
                    columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS"),
                        "YES".equals(rs.getString("IS_NULLABLE")),
                        rs.getString("COLUMN_DEF"),
                        "YES".equals(rs.getString("IS_AUTOINCREMENT")),
                        rs.getString("REMARKS")
                    ));
                }
            }

            // Get primary key columns
            try (ResultSet rs = metaData.getPrimaryKeys(null, schemaName, tableName)) {
                while (rs.next()) {
                    primaryKey.add(rs.getString("COLUMN_NAME"));
                }
            }

            // Get indexes
            try (ResultSet rs = metaData.getIndexInfo(null, schemaName, tableName, false, false)) {
                String currentIndexName = null;
                List<String> currentColumns = new ArrayList<>();
                boolean currentUnique = false;

                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue;

                    String columnName = rs.getString("COLUMN_NAME");
                    boolean unique = !rs.getBoolean("NON_UNIQUE");

                    if (!indexName.equals(currentIndexName)) {
                        if (currentIndexName != null) {
                            indexes.add(new IndexInfo(currentIndexName, new ArrayList<>(currentColumns), currentUnique));
                        }
                        currentIndexName = indexName;
                        currentColumns.clear();
                        currentUnique = unique;
                    }
                    currentColumns.add(columnName);
                }

                if (currentIndexName != null) {
                    indexes.add(new IndexInfo(currentIndexName, currentColumns, currentUnique));
                }
            }

            // Get foreign keys
            try (ResultSet rs = metaData.getImportedKeys(null, schemaName, tableName)) {
                String currentFkName = null;
                List<ColumnMapping> currentMappings = new ArrayList<>();
                String currentPkTable = null;
                String currentPkSchema = null;

                while (rs.next()) {
                    String fkName = rs.getString("FK_NAME");
                    String fkColumn = rs.getString("FKCOLUMN_NAME");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    String pkSchema = rs.getString("PKTABLE_SCHEM");
                    String pkColumn = rs.getString("PKCOLUMN_NAME");

                    if (fkName == null) continue;

                    if (!fkName.equals(currentFkName)) {
                        if (currentFkName != null) {
                            foreignKeys.add(new ForeignKeyInfo(
                                currentFkName,
                                currentPkSchema,
                                currentPkTable,
                                new ArrayList<>(currentMappings),
                                SCHEME + "://connection/" + connectionId + "/schema/" + currentPkSchema + "/table/" + currentPkTable
                            ));
                        }
                        currentFkName = fkName;
                        currentMappings.clear();
                        currentPkTable = pkTable;
                        currentPkSchema = pkSchema;
                    }
                    currentMappings.add(new ColumnMapping(fkColumn, pkColumn));
                }

                if (currentFkName != null) {
                    foreignKeys.add(new ForeignKeyInfo(
                        currentFkName,
                        currentPkSchema,
                        currentPkTable,
                        currentMappings,
                        SCHEME + "://connection/" + connectionId + "/schema/" + currentPkSchema + "/table/" + currentPkTable
                    ));
                }
            }
        }

        TableResponse response = new TableResponse(
            tableName,
            schemaName,
            connectionId,
            columns,
            primaryKey,
            indexes,
            foreignKeys,
            new NavigationLinks(
                SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/tables"
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record TableResponse(
        String name,
        String schema,
        String connectionId,
        List<ColumnInfo> columns,
        List<String> primaryKey,
        List<IndexInfo> indexes,
        List<ForeignKeyInfo> foreignKeys,
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
        String defaultValue,
        boolean autoIncrement,
        String remarks
    ) {}

    /**
     * Index information
     */
    public record IndexInfo(
        String name,
        List<String> columns,
        boolean unique
    ) {}

    /**
     * Foreign key information with navigation link to referenced table
     */
    public record ForeignKeyInfo(
        String name,
        String referencedSchema,
        String referencedTable,
        List<ColumnMapping> columns,
        String referencedTableUri
    ) {}

    /**
     * Column mapping for foreign keys
     */
    public record ColumnMapping(
        String sourceColumn,
        String targetColumn
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}


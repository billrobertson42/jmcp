package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.BooleanProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for describing table structure (columns, types, constraints)
 */
public class DescribeTableTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "describe-table";
    }

    @Override
    public String getDescription() {
        return "Get table information: columns, and types. Optionally include constraints statistics, partitions, extended column details";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "table", new StringProperty("The table name to describe"),
                "schema", new StringProperty("Optional schema name"),
                "includeTriggers", new BooleanProperty("Include trigger information (default: false)"),
                "includeCheckConstraints", new BooleanProperty("Include check constraints (default: false)"),
                "includeStatistics", new BooleanProperty("Include table statistics (default: false)"),
                "includePartitions", new BooleanProperty("Include partition information (default: false)"),
                "includeExtendedColumns", new BooleanProperty("Include extended column info like auto-increment and generated columns (default: false)"),
                "database_id", new StringProperty("Optional database connection ID. If not provided, uses the default connection.")
            ),
            List.of("table")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String tableName = params.get("table").asString();
        String schemaName = params.has("schema") ? params.get("schema").asString() : null;

        // Optional flags
        boolean includeTriggers = params.has("includeTriggers") && params.get("includeTriggers").asBoolean();
        boolean includeCheckConstraints = params.has("includeCheckConstraints") && params.get("includeCheckConstraints").asBoolean();
        boolean includeStatistics = params.has("includeStatistics") && params.get("includeStatistics").asBoolean();
        boolean includePartitions = params.has("includePartitions") && params.get("includePartitions").asBoolean();
        boolean includeExtendedColumns = params.has("includeExtendedColumns") && params.get("includeExtendedColumns").asBoolean();

        try (Connection conn = context.getConnection()) {
            // Resolve schema name (use provided or default)
            schemaName = JdbcToolUtils.resolveSchemaName(conn, schemaName);

            // Validate table exists to prevent potential SQL injection
            JdbcToolUtils.validateTableExists(conn, schemaName, tableName);

            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();

            // Get columns
            List<ColumnMetadata> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(null, schemaName, tableName, "%")) {
                while (rs.next()) {
                    String defaultValue = rs.getString("COLUMN_DEF");
                    String remarks = rs.getString("REMARKS");

                    Boolean isAutoIncrement = null;
                    Boolean isGenerated = null;
                    String generationExpression = null;

                    if (includeExtendedColumns) {
                        try {
                            isAutoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
                        } catch (Exception e) {
                            // Column not available in this database
                        }
                        try {
                            isGenerated = "YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN"));
                        } catch (Exception e) {
                            // Column not available in this database
                        }
                    }

                    columns.add(new ColumnMetadata(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        defaultValue,
                        remarks != null && !remarks.isEmpty() ? remarks : null,
                        isAutoIncrement,
                        isGenerated,
                        generationExpression
                    ));
                }
            }

            // Get primary keys
            List<String> primaryKeys = new ArrayList<>();
            try (ResultSet rs = metaData.getPrimaryKeys(null, schemaName, tableName)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }

            // Get foreign keys (grouped by constraint name for multi-column FKs)
            Map<String, ForeignKeyBuilder> fkBuilders = new HashMap<>();
            try (ResultSet rs = metaData.getImportedKeys(null, schemaName, tableName)) {
                while (rs.next()) {
                    String fkName = rs.getString("FK_NAME");
                    String fkColumn = rs.getString("FKCOLUMN_NAME");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    String pkColumn = rs.getString("PKCOLUMN_NAME");
                    String pkSchema = rs.getString("PKTABLE_SCHEM");

                    // Get referential actions (ON DELETE, ON UPDATE)
                    short deleteRule = rs.getShort("DELETE_RULE");
                    short updateRule = rs.getShort("UPDATE_RULE");
                    short deferrability = rs.getShort("DEFERRABILITY");

                    String onDelete = getReferentialAction(deleteRule);
                    String onUpdate = getReferentialAction(updateRule);

                    // Group by FK name to handle multi-column foreign keys
                    ForeignKeyBuilder builder = fkBuilders.computeIfAbsent(fkName,
                        k -> new ForeignKeyBuilder(fkName, pkTable, pkSchema, onDelete, onUpdate, deferrability));

                    builder.addColumn(fkColumn, pkColumn);
                }
            }

            // Convert builders to ForeignKeyInfo list
            List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
            for (ForeignKeyBuilder builder : fkBuilders.values()) {
                foreignKeys.add(builder.build());
            }

            // Get indexes (enhanced with unique constraints)
            List<IndexInfo> indexes = new ArrayList<>();
            try (ResultSet rs = metaData.getIndexInfo(null, schemaName, tableName, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName != null) {
                        boolean isUnique = !rs.getBoolean("NON_UNIQUE");
                        indexes.add(new IndexInfo(
                            indexName,
                            rs.getString("COLUMN_NAME"),
                            isUnique,
                            isUnique ? "UNIQUE_INDEX" : "INDEX",
                            null
                        ));
                    }
                }
            }

            // Optional: Get triggers
            List<TriggerInfo> triggers = null;
            if (includeTriggers) {
                triggers = getTriggers(conn, productName, schemaName, tableName);
            }

            // Optional: Get check constraints
            List<CheckConstraintInfo> checkConstraints = null;
            if (includeCheckConstraints) {
                checkConstraints = getCheckConstraints(conn, productName, schemaName, tableName);
            }

            // Optional: Get statistics
            TableStatistics statistics = null;
            if (includeStatistics) {
                statistics = getTableStatistics(conn, productName, schemaName, tableName);
            }

            // Optional: Get partitions
            PartitionInfo partitions = null;
            if (includePartitions) {
                partitions = getPartitionInfo(conn, productName, schemaName, tableName);
            }

            return new TableDescription(tableName, schemaName, columns, primaryKeys, foreignKeys, indexes,
                triggers, checkConstraints, statistics, partitions);
        }
    }

    /**
     * Convert JDBC referential action code to readable string
     */
    private static String getReferentialAction(short actionCode) {
        return switch (actionCode) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            default -> null;
        };
    }

    /**
     * Get trigger information for a table
     */
    private List<TriggerInfo> getTriggers(Connection conn, String productName, String schema, String tableName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT t.tgname, t.tgtype, pg_get_triggerdef(t.oid) AS definition " +
                   "FROM pg_trigger t JOIN pg_class c ON t.tgrelid = c.oid " +
                   "JOIN pg_namespace n ON c.relnamespace = n.oid " +
                   "WHERE n.nspname = ? AND c.relname = ? AND NOT t.tgisinternal";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT TRIGGER_NAME, ACTION_TIMING, EVENT_MANIPULATION, ACTION_STATEMENT " +
                   "FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ? AND EVENT_OBJECT_TABLE = ?";
        } else if (productName.contains("oracle")) {
            query = "SELECT TRIGGER_NAME, TRIGGERING_EVENT, TRIGGER_TYPE, TRIGGER_BODY " +
                   "FROM ALL_TRIGGERS WHERE OWNER = ? AND TABLE_NAME = ?";
        } else if (productName.contains("sql server") || productName.contains("microsoft")) {
            query = "SELECT t.name, OBJECTPROPERTY(t.object_id, 'ExecIsAfterTrigger') AS is_after, " +
                   "OBJECT_DEFINITION(t.object_id) AS definition " +
                   "FROM sys.triggers t JOIN sys.tables tb ON t.parent_id = tb.object_id " +
                   "JOIN sys.schemas s ON tb.schema_id = s.schema_id " +
                   "WHERE s.name = ? AND tb.name = ?";
        } else {
            return null;
        }

        List<TriggerInfo> triggers = new ArrayList<>();
        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String timing = null;
                    List<String> events = new ArrayList<>();
                    String orientation = "ROW";
                    String definition = null;

                    if (productName.contains("postgresql")) {
                        int tgtype = rs.getInt(2);
                        timing = ((tgtype & 2) != 0) ? "BEFORE" : "AFTER";
                        if ((tgtype & 4) != 0) events.add("INSERT");
                        if ((tgtype & 8) != 0) events.add("DELETE");
                        if ((tgtype & 16) != 0) events.add("UPDATE");
                        orientation = ((tgtype & 1) != 0) ? "ROW" : "STATEMENT";
                        definition = rs.getString(3);
                    } else if (productName.contains("mysql") || productName.contains("mariadb")) {
                        timing = rs.getString(2);
                        events.add(rs.getString(3));
                        definition = rs.getString(4);
                    } else if (productName.contains("oracle")) {
                        String trigType = rs.getString(3);
                        timing = trigType.contains("BEFORE") ? "BEFORE" : "AFTER";
                        events.add(rs.getString(2));
                        definition = rs.getString(4);
                    } else if (productName.contains("sql server")) {
                        timing = rs.getBoolean(2) ? "AFTER" : "INSTEAD OF";
                        definition = rs.getString(3);
                        events.add("MODIFY");  // SQL Server doesn't separate INSERT/UPDATE/DELETE easily
                    }

                    triggers.add(new TriggerInfo(name, timing, events, orientation, definition));
                }
            }
        } catch (Exception e) {
            // Silently ignore if triggers not available
        }

        return triggers.isEmpty() ? null : triggers;
    }

    /**
     * Get check constraints for a table
     */
    private List<CheckConstraintInfo> getCheckConstraints(Connection conn, String productName, String schema, String tableName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT c.conname, pg_get_constraintdef(c.oid), c.convalidated " +
                   "FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid " +
                   "JOIN pg_namespace n ON t.relnamespace = n.oid " +
                   "WHERE n.nspname = ? AND t.relname = ? AND c.contype = 'c'";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT CONSTRAINT_NAME, CHECK_CLAUSE, 'YES' AS enforced " +
                   "FROM information_schema.CHECK_CONSTRAINTS " +
                   "WHERE CONSTRAINT_SCHEMA = ? AND TABLE_NAME = ?";
        } else if (productName.contains("oracle")) {
            query = "SELECT CONSTRAINT_NAME, SEARCH_CONDITION, STATUS " +
                   "FROM ALL_CONSTRAINTS WHERE OWNER = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'C'";
        } else if (productName.contains("sql server") || productName.contains("microsoft")) {
            query = "SELECT cc.name, cc.definition, cc.is_disabled " +
                   "FROM sys.check_constraints cc JOIN sys.tables t ON cc.parent_object_id = t.object_id " +
                   "JOIN sys.schemas s ON t.schema_id = s.schema_id " +
                   "WHERE s.name = ? AND t.name = ?";
        } else if (productName.contains("h2")) {
            query = "SELECT CONSTRAINT_NAME, CHECK_EXPRESSION, 'YES' " +
                   "FROM information_schema.CONSTRAINTS " +
                   "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'CHECK'";
        } else {
            return null;
        }

        List<CheckConstraintInfo> constraints = new ArrayList<>();
        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String definition = rs.getString(2);
                    Boolean isEnforced = null;

                    if (productName.contains("postgresql")) {
                        isEnforced = rs.getBoolean(3);
                    } else if (productName.contains("sql server")) {
                        isEnforced = !rs.getBoolean(3);  // is_disabled, so invert
                    } else {
                        String status = rs.getString(3);
                        isEnforced = "YES".equalsIgnoreCase(status) || "ENABLED".equalsIgnoreCase(status);
                    }

                    constraints.add(new CheckConstraintInfo(name, definition, isEnforced));
                }
            }
        } catch (Exception e) {
            // Silently ignore if check constraints not available
        }

        return constraints.isEmpty() ? null : constraints;
    }

    /**
     * Get table statistics
     */
    private TableStatistics getTableStatistics(Connection conn, String productName, String schema, String tableName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT n_live_tup, pg_size_pretty(pg_table_size(? || '.' || ?)), " +
                   "pg_size_pretty(pg_indexes_size(? || '.' || ?)), " +
                   "pg_size_pretty(pg_total_relation_size(? || '.' || ?)) " +
                   "FROM pg_stat_user_tables WHERE schemaname = ? AND relname = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT TABLE_ROWS, " +
                   "CONCAT(ROUND(DATA_LENGTH / 1024 / 1024, 2), ' MB'), " +
                   "CONCAT(ROUND(INDEX_LENGTH / 1024 / 1024, 2), ' MB'), " +
                   "CONCAT(ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2), ' MB') " +
                   "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        } else if (productName.contains("oracle")) {
            query = "SELECT NUM_ROWS, " +
                   "ROUND(SUM(BYTES) / 1024 / 1024, 2) || ' MB' AS data_size " +
                   "FROM ALL_TABLES t LEFT JOIN USER_SEGMENTS s ON t.TABLE_NAME = s.SEGMENT_NAME " +
                   "WHERE t.OWNER = ? AND t.TABLE_NAME = ? GROUP BY NUM_ROWS";
        } else {
            return null;
        }

        try (var stmt = conn.prepareStatement(query)) {
            if (productName.contains("postgresql")) {
                String fullName = schema + "." + tableName;
                stmt.setString(1, schema);
                stmt.setString(2, tableName);
                stmt.setString(3, schema);
                stmt.setString(4, tableName);
                stmt.setString(5, schema);
                stmt.setString(6, tableName);
                stmt.setString(7, schema);
                stmt.setString(8, tableName);
            } else {
                stmt.setString(1, schema);
                stmt.setString(2, tableName);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long rowEstimate = rs.getLong(1);
                    String diskSize = rs.getString(2);
                    String indexSize = productName.contains("oracle") ? null : rs.getString(3);
                    String totalSize = productName.contains("oracle") ? diskSize : rs.getString(4);

                    return new TableStatistics(rowEstimate, diskSize, indexSize, totalSize);
                }
            }
        } catch (Exception e) {
            // Silently ignore if statistics not available
        }

        return null;
    }

    /**
     * Get partition information
     */
    private PartitionInfo getPartitionInfo(Connection conn, String productName, String schema, String tableName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT partstrat, partkey, " +
                   "(SELECT string_agg(attname, ',') FROM pg_attribute " +
                   " WHERE attrelid = c.oid AND attnum = ANY(p.partkey)) AS partition_cols " +
                   "FROM pg_partitioned_table p JOIN pg_class c ON p.partrelid = c.oid " +
                   "JOIN pg_namespace n ON c.relnamespace = n.oid " +
                   "WHERE n.nspname = ? AND c.relname = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT PARTITION_METHOD, PARTITION_EXPRESSION " +
                   "FROM information_schema.PARTITIONS " +
                   "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL LIMIT 1";
        } else if (productName.contains("oracle")) {
            query = "SELECT PARTITIONING_TYPE, (SELECT COLUMN_NAME FROM ALL_PART_KEY_COLUMNS " +
                   "WHERE OWNER = ? AND NAME = ?) " +
                   "FROM ALL_PART_TABLES WHERE OWNER = ? AND TABLE_NAME = ?";
        } else {
            return null;
        }

        try (var stmt = conn.prepareStatement(query)) {
            if (productName.contains("oracle")) {
                stmt.setString(1, schema);
                stmt.setString(2, tableName);
                stmt.setString(3, schema);
                stmt.setString(4, tableName);
            } else {
                stmt.setString(1, schema);
                stmt.setString(2, tableName);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String partType = rs.getString(1);
                    String partKeys = rs.getString(2);

                    List<String> keyList = partKeys != null ?
                        List.of(partKeys.split(",")) : List.of();

                    // Get partition details
                    List<PartitionInfo.PartitionDetail> partitions = getPartitionDetails(
                        conn, productName, schema, tableName);

                    return new PartitionInfo(partType, keyList, partitions);
                }
            }
        } catch (Exception e) {
            // Silently ignore if partitioning not available
        }

        return null;
    }

    /**
     * Get details of individual partitions
     */
    private List<PartitionInfo.PartitionDetail> getPartitionDetails(Connection conn, String productName,
                                                                     String schema, String tableName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT c.relname, pg_get_expr(c.relpartbound, c.oid), " +
                   "(SELECT n_live_tup FROM pg_stat_user_tables WHERE schemaname = ? AND relname = c.relname) " +
                   "FROM pg_class c JOIN pg_inherits i ON c.oid = i.inhrelid " +
                   "JOIN pg_class p ON i.inhparent = p.oid " +
                   "JOIN pg_namespace n ON p.relnamespace = n.oid " +
                   "WHERE n.nspname = ? AND p.relname = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT PARTITION_NAME, PARTITION_EXPRESSION, TABLE_ROWS " +
                   "FROM information_schema.PARTITIONS " +
                   "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL";
        } else {
            return null;
        }

        List<PartitionInfo.PartitionDetail> partitions = new ArrayList<>();
        try (var stmt = conn.prepareStatement(query)) {
            if (productName.contains("postgresql")) {
                stmt.setString(1, schema);
                stmt.setString(2, schema);
                stmt.setString(3, tableName);
            } else {
                stmt.setString(1, schema);
                stmt.setString(2, tableName);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String expression = rs.getString(2);
                    Long rowCount = rs.getLong(3);

                    partitions.add(new PartitionInfo.PartitionDetail(name, expression, rowCount));
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return partitions.isEmpty() ? null : partitions;
    }

    /**
     * Helper class to build ForeignKeyInfo with support for multi-column FKs
     */
    private static class ForeignKeyBuilder {
        private final String name;
        private final String referencedTable;
        private final String referencedSchema;
        private final String onDelete;
        private final String onUpdate;
        private final Short deferrability;
        private final List<String> columns = new ArrayList<>();
        private final List<String> referencedColumns = new ArrayList<>();

        public ForeignKeyBuilder(String name, String referencedTable, String referencedSchema,
                                String onDelete, String onUpdate, Short deferrability) {
            this.name = name;
            this.referencedTable = referencedTable;
            this.referencedSchema = referencedSchema;
            this.onDelete = onDelete;
            this.onUpdate = onUpdate;
            this.deferrability = deferrability;
        }

        public void addColumn(String column, String referencedColumn) {
            columns.add(column);
            referencedColumns.add(referencedColumn);
        }

        public ForeignKeyInfo build() {
            return new ForeignKeyInfo(
                name,
                List.copyOf(columns),
                referencedTable,
                List.copyOf(referencedColumns),
                referencedSchema,
                onDelete,
                onUpdate,
                deferrability
            );
        }
    }
}


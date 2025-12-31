package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Information about a foreign key constraint.
 * Enhanced to support multi-column foreign keys, referential actions, and deferrability.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForeignKeyInfo(
    String name,                    // Foreign key constraint name
    List<String> columns,           // Column(s) in this table (supports multi-column FKs)
    String referencedTable,         // Referenced (parent) table name
    List<String> referencedColumns, // Referenced column(s) in parent table
    String referencedSchema,        // Schema of referenced table (null if not available)
    String onDelete,                // ON DELETE action (CASCADE, SET NULL, RESTRICT, NO ACTION)
    String onUpdate,                // ON UPDATE action (CASCADE, SET NULL, RESTRICT, NO ACTION)
    Short deferrability             // Deferrability: importedKeyInitiallyDeferred, importedKeyInitiallyImmediate, importedKeyNotDeferrable
) {
    /**
     * Get human-readable deferrability status
     */
    public String getDeferrabilityDescription() {
        if (deferrability == null) return null;
        return switch (deferrability) {
            case 5 -> "INITIALLY_DEFERRED";    // DatabaseMetaData.importedKeyInitiallyDeferred
            case 6 -> "INITIALLY_IMMEDIATE";   // DatabaseMetaData.importedKeyInitiallyImmediate
            case 7 -> "NOT_DEFERRABLE";        // DatabaseMetaData.importedKeyNotDeferrable
            default -> "UNKNOWN";
        };
    }
}


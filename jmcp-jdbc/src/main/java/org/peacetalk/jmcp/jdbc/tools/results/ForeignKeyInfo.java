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


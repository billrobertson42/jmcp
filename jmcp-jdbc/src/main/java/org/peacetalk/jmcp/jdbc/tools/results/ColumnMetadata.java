package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for a database column
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnMetadata(
    String name,
    String type,
    Integer size,
    Boolean nullable,
    String defaultValue,
    String remarks,
    // Extended information (optional)
    Boolean isAutoIncrement,
    Boolean isGenerated,
    String generationExpression
) {
    // Backward-compatible constructor (basic)
    public ColumnMetadata(String name, String type) {
        this(name, type, null, null, null, null, null, null, null);
    }

    // Backward-compatible constructor (standard)
    public ColumnMetadata(String name, String type, Integer size, Boolean nullable, String defaultValue, String remarks) {
        this(name, type, size, nullable, defaultValue, remarks, null, null, null);
    }
}


package org.peacetalk.jmcp.jdbc.tools.results;

/**
 * Metadata for a database column
 */
public record ColumnMetadata(
    String name,
    String type,
    Integer size,
    Boolean nullable,
    String defaultValue,
    String remarks
) {
    public ColumnMetadata(String name, String type) {
        this(name, type, null, null, null, null);
    }
}


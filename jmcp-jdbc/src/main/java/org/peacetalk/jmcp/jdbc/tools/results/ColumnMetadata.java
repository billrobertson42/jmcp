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
    String remarks
) {
    public ColumnMetadata(String name, String type) {
        this(name, type, null, null, null, null);
    }
}


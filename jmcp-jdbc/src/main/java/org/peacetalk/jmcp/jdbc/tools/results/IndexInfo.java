package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a database index
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndexInfo(
    String name,
    String column,
    boolean unique,
    String type,  // UNIQUE_CONSTRAINT, INDEX, PRIMARY_KEY
    Boolean isDeferrable
) {
    // Backward-compatible constructor
    public IndexInfo(String name, String column, boolean unique) {
        this(name, column, unique, unique ? "UNIQUE_INDEX" : "INDEX", null);
    }
}


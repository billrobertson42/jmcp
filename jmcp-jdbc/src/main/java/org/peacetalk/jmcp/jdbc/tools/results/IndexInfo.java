package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a database index
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndexInfo(
    String name,
    String column,
    boolean unique
) {
}


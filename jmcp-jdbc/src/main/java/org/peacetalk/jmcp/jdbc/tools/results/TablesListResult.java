package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of listing tables in a database
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TablesListResult(
    List<TableInfo> tables,
    int count
) {
}


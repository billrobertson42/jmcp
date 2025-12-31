package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of explaining a query execution plan
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExplainQueryResult(
    String sql,
    String plan,
    String format
) {
}


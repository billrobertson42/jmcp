package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a database table
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableInfo(
    String schema,
    String name,
    String type,
    String remarks
) {

}


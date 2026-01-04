package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
/**
 * Information about a database view
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewInfo(
    String name,
    String schema,
    String definition,
    List<String> columns,
    Boolean isUpdatable,
    String checkOption
) {
}

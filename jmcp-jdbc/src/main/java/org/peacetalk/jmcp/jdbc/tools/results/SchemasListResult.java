package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of listing schemas in a database
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchemasListResult(
    List<SchemaInfo> schemas,
    int count
) {
}


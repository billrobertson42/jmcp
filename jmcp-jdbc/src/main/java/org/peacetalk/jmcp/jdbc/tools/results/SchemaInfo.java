package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a database schema
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchemaInfo(
    String name,
    String catalog
) {
}


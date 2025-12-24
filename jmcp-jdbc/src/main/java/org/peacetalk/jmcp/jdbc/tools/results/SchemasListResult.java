package org.peacetalk.jmcp.jdbc.tools.results;

import java.util.List;

/**
 * Result of listing schemas in a database
 */
public record SchemasListResult(
    List<SchemaInfo> schemas,
    int count
) {
}


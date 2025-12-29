package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a database connection
 *
 * @param id Connection identifier
 * @param url JDBC URL
 * @param username Database username
 * @param databaseType Type of database (postgresql, mysql, etc.)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectionInfo(
    String id,
    String url,
    String username,
    String databaseType
) {
}


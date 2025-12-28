package org.peacetalk.jmcp.jdbc.tools.results;

/**
 * Information about a database connection
 *
 * @param id Connection identifier
 * @param url JDBC URL
 * @param username Database username
 * @param databaseType Type of database (postgresql, mysql, etc.)
 */
public record ConnectionInfo(
    String id,
    String url,
    String username,
    String databaseType
) {
}


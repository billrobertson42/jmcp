package org.peacetalk.jmcp.jdbc.tools.results;

import java.util.List;

/**
 * Result of listing database connections
 *
 * @param connections List of available connections
 * @param defaultId The ID of the default connection
 */
public record ListConnectionsResult(
    List<ConnectionInfo> connections,
    String defaultId
) {
}


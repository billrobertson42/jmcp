package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of listing database connections
 *
 * @param connections List of available connections
 * @param defaultId The ID of the default connection
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListConnectionsResult(
    List<ConnectionInfo> connections,
    String defaultId
) {
}


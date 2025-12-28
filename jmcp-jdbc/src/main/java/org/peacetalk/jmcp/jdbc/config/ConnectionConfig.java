package org.peacetalk.jmcp.jdbc.config;

public record ConnectionConfig(
    String id,
    String databaseType,
    String jdbcUrl,
    String username,
    String password
) {
}


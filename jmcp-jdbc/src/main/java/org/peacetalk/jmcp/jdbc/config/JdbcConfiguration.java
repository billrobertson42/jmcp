package org.peacetalk.jmcp.jdbc.config;

public record JdbcConfiguration(
    String default_id,
    Boolean expose_urls,
    ConnectionConfig[] connections
) {
    public JdbcConfiguration {
        // Set default if not provided
        if (default_id == null || default_id.isBlank()) {
            default_id = "default";
        }
        // Default to false for security
        if (expose_urls == null) {
            expose_urls = false;
        }
    }
}


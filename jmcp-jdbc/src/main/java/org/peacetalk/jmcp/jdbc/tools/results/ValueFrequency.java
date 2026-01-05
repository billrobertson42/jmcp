package org.peacetalk.jmcp.jdbc.tools.results;

/**
 * Represents a value and its frequency count in a column analysis.
 */
public record ValueFrequency(
    String value,
    long frequency
) {}


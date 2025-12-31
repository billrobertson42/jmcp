package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a check constraint
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckConstraintInfo(
    String name,
    String definition,
    Boolean isEnforced
) {
}


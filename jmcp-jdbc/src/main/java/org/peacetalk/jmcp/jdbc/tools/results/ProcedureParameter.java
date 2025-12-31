package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a stored procedure or function parameter
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcedureParameter(
    String name,
    String type,
    String mode,  // IN, OUT, INOUT
    Integer position
) {
}


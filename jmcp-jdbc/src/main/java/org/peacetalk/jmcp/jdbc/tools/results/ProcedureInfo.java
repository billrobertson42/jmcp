package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Information about a stored procedure or function
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcedureInfo(
    String name,
    String schema,
    String type,  // PROCEDURE or FUNCTION
    List<ProcedureParameter> parameters,
    String returnType,
    String definition,
    String language,
    Boolean isDeterministic,
    String remarks
) {
}


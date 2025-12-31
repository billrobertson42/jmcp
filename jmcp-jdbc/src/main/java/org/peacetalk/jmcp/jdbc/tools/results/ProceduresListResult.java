package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of listing procedures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProceduresListResult(
    List<ProcedureInfo> procedures,
    int count
) {
}


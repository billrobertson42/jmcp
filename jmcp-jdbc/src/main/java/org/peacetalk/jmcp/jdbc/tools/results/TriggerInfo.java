package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Information about a trigger
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TriggerInfo(
    String name,
    String timing,  // BEFORE, AFTER, INSTEAD OF
    List<String> events,  // INSERT, UPDATE, DELETE
    String orientation,  // ROW, STATEMENT
    String definition
) {
}


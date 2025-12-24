package org.peacetalk.jmcp.core.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an array property in JSON Schema.
 *
 * <p>Used to define array-typed properties in tool input schemas with item schema definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArrayProperty(
    String type,
    String description,
    Object items
) {
    public ArrayProperty(String description, Object items) {
        this("array", description, items);
    }
}


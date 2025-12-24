package org.peacetalk.jmcp.core.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an integer property in JSON Schema.
 *
 * <p>Used to define integer-typed properties in tool input schemas with optional min/max constraints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntegerProperty(
    String type,
    String description,
    Integer minimum,
    Integer maximum
) {
    public IntegerProperty(String description) {
        this("integer", description, null, null);
    }

    public IntegerProperty(String description, Integer minimum, Integer maximum) {
        this("integer", description, minimum, maximum);
    }
}


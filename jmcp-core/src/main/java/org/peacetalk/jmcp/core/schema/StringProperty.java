package org.peacetalk.jmcp.core.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a string property in JSON Schema.
 *
 * <p>Used to define string-typed properties in tool input schemas with optional length constraints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StringProperty(
    String type,
    String description,
    Integer minLength,
    Integer maxLength
) {
    public StringProperty(String description) {
        this("string", description, null, null);
    }

    public StringProperty(String description, Integer minLength, Integer maxLength) {
        this("string", description, minLength, maxLength);
    }
}


package org.peacetalk.jmcp.core.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a boolean property in JSON Schema.
 *
 * <p>Used to define boolean-typed properties in tool input schemas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BooleanProperty(
    String type,
    String description
) {
    public BooleanProperty(String description) {
        this("boolean", description);
    }
}


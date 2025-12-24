package org.peacetalk.jmcp.core.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents an object schema in JSON Schema.
 *
 * <p>Used to build type-safe JSON Schema objects for MCP Tool input schemas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObjectSchema(
    String type,
    Map<String, Object> properties,
    List<String> required
) {
    public ObjectSchema(Map<String, Object> properties, List<String> required) {
        this("object", properties, required);
    }

    public ObjectSchema(Map<String, Object> properties) {
        this("object", properties, List.of());
    }
}


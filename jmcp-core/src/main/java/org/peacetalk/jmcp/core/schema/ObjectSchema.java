/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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


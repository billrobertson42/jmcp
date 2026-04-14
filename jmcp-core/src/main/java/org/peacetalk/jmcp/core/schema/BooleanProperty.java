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


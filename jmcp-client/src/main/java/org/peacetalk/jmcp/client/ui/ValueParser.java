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

package org.peacetalk.jmcp.client.ui;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;

/**
 * Coerces string form input into Java values according to the field's declared
 * JSON-schema type. No type guessing: a {@code string}-typed field stays a
 * string even if it looks like a number or boolean, and a value that cannot be
 * coerced to its declared type is an error surfaced to the caller as an
 * {@link IllegalArgumentException}, never silently passed through as a string.
 */
public class ValueParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Coerce a raw form value to the field's declared JSON-schema type.
     *
     * @param fieldName The field name, used in error messages
     * @param schemaType The declared JSON-schema type ("string", "integer",
     *        "number", "boolean", "array", "object"), or null if the schema
     *        declares no type
     * @param value The non-empty raw text entered by the user
     * @return The coerced value
     * @throws IllegalArgumentException if the value cannot be coerced to the
     *         declared type
     */
    public Object coerce(String fieldName, String schemaType, String value) {
        if (schemaType == null) {
            return parseJsonOrString(value);
        }
        return switch (schemaType) {
            case "string" -> value;
            case "integer" -> coerceInteger(fieldName, value);
            case "number" -> coerceNumber(fieldName, value);
            case "boolean" -> coerceBoolean(fieldName, value);
            case "array", "object" -> coerceJson(fieldName, schemaType, value);
            default -> parseJsonOrString(value);
        };
    }

    /**
     * Coerce to a whole number: Long, widening to BigInteger on overflow.
     */
    private Object coerceInteger(String fieldName, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            try {
                return new BigInteger(value);
            } catch (NumberFormatException e2) {
                throw new IllegalArgumentException(
                        "Field '" + fieldName + "': '" + value + "' is not a valid integer");
            }
        }
    }

    private Object coerceNumber(String fieldName, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "': '" + value + "' is not a valid number");
        }
    }

    private Object coerceBoolean(String fieldName, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException(
                "Field '" + fieldName + "': '" + value + "' is not a valid boolean (expected true or false)");
    }

    private Object coerceJson(String fieldName, String schemaType, String value) {
        try {
            return MAPPER.readValue(value, Object.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "': '" + value + "' is not valid JSON for type "
                            + schemaType + ": " + e.getMessage());
        }
    }

    /**
     * For fields with no (or an unrecognized) declared type: one JSON parse
     * attempt, falling back to the raw string.
     */
    private Object parseJsonOrString(String value) {
        try {
            return MAPPER.readValue(value, Object.class);
        } catch (JacksonException e) {
            return value;
        }
    }
}

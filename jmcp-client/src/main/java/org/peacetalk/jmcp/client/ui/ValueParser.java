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

import tools.jackson.databind.ObjectMapper;

/**
 * Parses string values into appropriate Java types.
 * Handles numbers, booleans, JSON arrays, and strings.
 */
public class ValueParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parse a string value into the most appropriate type.
     *
     * @param value The string value to parse
     * @return The parsed value (Integer, Double, Boolean, Object, or String)
     */
    public Object parse(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        // Try to parse as number
        Object number = parseNumber(value);
        if (number != null) {
            return number;
        }

        // Try to parse as boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return parseBoolean(value);
        }

        // Try to parse as JSON array
        if (value.startsWith("[") && value.endsWith("]")) {
            Object json = parseJson(value);
            if (json != null) {
                return json;
            }
        }

        // Default to string
        return value;
    }

    /**
     * Parse a string as a number (Integer or Double).
     *
     * @param value The string to parse
     * @return Integer, Double, or null if not a number
     */
    public Number parseNumber(String value) {
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a string as a boolean.
     *
     * @param value The string to parse
     * @return Boolean value
     */
    public Boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value);
    }

    /**
     * Parse a string as JSON.
     *
     * @param value The JSON string to parse
     * @return Parsed object or null if parsing fails
     */
    public Object parseJson(String value) {
        try {
            return MAPPER.readValue(value, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}


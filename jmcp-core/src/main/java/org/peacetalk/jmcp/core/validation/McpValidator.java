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

package org.peacetalk.jmcp.core.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for validating MCP protocol objects using JSR-380 Bean Validation.
 *
 * Uses Hibernate Validator as the implementation.
 *
 * <p>Example usage:
 * <pre>
 * Tool tool = new Tool("my-tool", "Description", schema);
 * Set&lt;String&gt; violations = McpValidator.validate(tool);
 * if (!violations.isEmpty()) {
 *     throw new IllegalArgumentException("Validation failed: " + violations);
 * }
 * </pre>
 */
public final class McpValidator {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private McpValidator() {
        // Utility class
    }

    /**
     * Validate an object against its JSR-380 constraints.
     *
     * @param object The object to validate
     * @param <T> The type of object
     * @return Set of validation error messages (empty if valid)
     */
    public static <T> Set<String> validate(T object) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);
        return violations.stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.toSet());
    }

    /**
     * Validate an object and throw an exception if invalid.
     *
     * @param object The object to validate
     * @param <T> The type of object
     * @throws IllegalArgumentException if validation fails
     */
    public static <T> void validateAndThrow(T object) {
        Set<String> violations = validate(object);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                "Validation failed: " + String.join(", ", violations)
            );
        }
    }

    /**
     * Check if an object is valid.
     *
     * @param object The object to check
     * @param <T> The type of object
     * @return true if valid, false otherwise
     */
    public static <T> boolean isValid(T object) {
        return VALIDATOR.validate(object).isEmpty();
    }

    /**
     * Get the underlying validator instance.
     *
     * @return The validator
     */
    public static Validator getValidator() {
        return VALIDATOR;
    }
}


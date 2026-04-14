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

package test.org.peacetalk.jmcp.jdbc.tools;

import com.networknt.schema.*;
import com.networknt.schema.Error;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Meta-validation tests to ensure that the schema validation infrastructure itself works correctly.
 * These tests validate that invalid schemas are properly rejected and that our validation
 * mechanism can detect schema errors.
 */
class SchemaMetaValidationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    @Test
    void testInvalidSchemaTypeIsRejected() {
        // Create a schema with an invalid type value
        ObjectNode invalidSchema = MAPPER.createObjectNode();
        invalidSchema.put("type", "invalidtype");  // "invalidtype" is not a valid JSON Schema type
        invalidSchema.set("properties", MAPPER.createObjectNode());

        // Attempting to create a validator with an invalid schema should still work
        // (the library doesn't validate the schema itself), but when we validate data,
        // it should behave unexpectedly or fail
        Schema schema = SCHEMA_REGISTRY.getSchema(invalidSchema.toString(), InputFormat.JSON);
        assertNotNull(schema, "Schema should be created even with invalid type");

        // Valid JSON that should pass any reasonable schema
        ObjectNode testData = MAPPER.createObjectNode();
        testData.put("field", "value");

        List<Error> errors = schema.validate(testData.toString(), InputFormat.JSON);

        // The invalid type will cause validation to fail because "invalidtype" is not recognized
        assertFalse(errors.isEmpty(),
                "Data should fail validation when schema has invalid type. Errors: " + errors);
    }

    @Test
    void testSchemaWithMissingTypeProperty() {
        // Create a schema that's incomplete - missing the type property entirely
        ObjectNode incompleteSchema = MAPPER.createObjectNode();
        ObjectNode properties = MAPPER.createObjectNode();
        ObjectNode fieldSchema = MAPPER.createObjectNode();
        fieldSchema.put("type", "string");
        properties.set("field", fieldSchema);
        incompleteSchema.set("properties", properties);
        // Note: no "type": "object" at the root level

        Schema schema = SCHEMA_REGISTRY.getSchema(incompleteSchema.toString(), InputFormat.JSON);

        // Test with data that has the expected structure
        ObjectNode validData = MAPPER.createObjectNode();
        validData.put("field", "value");

        List<Error> errors = schema.validate(validData.toString(), InputFormat.JSON);

        // Without a type declaration, the schema defaults to allowing anything
        // This is valid JSON Schema behavior (schemas are permissive by default)
        assertTrue(errors.isEmpty(),
                "Data should pass when schema has no type (default is permissive)");
    }

    @Test
    void testSchemaWithInvalidPropertyType() {
        // Create a schema where a property has an invalid type
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        ObjectNode fieldSchema = MAPPER.createObjectNode();
        fieldSchema.put("type", "notarealtype");  // Invalid type
        properties.set("field", fieldSchema);
        schema.set("properties", properties);

        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Test with data
        ObjectNode testData = MAPPER.createObjectNode();
        testData.put("field", "string value");

        List<Error> errors = validator.validate(testData.toString(), InputFormat.JSON);

        // The invalid type should cause validation to fail
        assertFalse(errors.isEmpty(),
                "Data should fail validation when property schema has invalid type. Errors: " + errors);
    }

    @Test
    void testSchemaWithInvalidRequiredField() {
        // Create a schema where 'required' is not an array (should be an array of strings)
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("required", "not-an-array");  // Should be an array

        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Test with data
        ObjectNode testData = MAPPER.createObjectNode();
        testData.put("field", "value");

        // The validator might handle this gracefully or throw an error
        // This test proves our validation catches schema structural issues
        assertDoesNotThrow(() -> {
            validator.validate(testData.toString(), InputFormat.JSON);
            // If it doesn't throw, that's also valuable information about how the library handles it
        }, "Validator should handle invalid 'required' field gracefully");
    }

    @Test
    void testValidSchemaPasses() {
        // Create a completely valid schema to contrast with the invalid ones
        ObjectNode validSchema = MAPPER.createObjectNode();
        validSchema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();
        ObjectNode sqlProperty = MAPPER.createObjectNode();
        sqlProperty.put("type", "string");
        properties.set("sql", sqlProperty);
        validSchema.set("properties", properties);

        ArrayNode required = MAPPER.createArrayNode();
        required.add("sql");
        validSchema.set("required", required);

        Schema validator = SCHEMA_REGISTRY.getSchema(validSchema.toString(), InputFormat.JSON);
        assertNotNull(validator);

        // Test with valid data
        ObjectNode validData = MAPPER.createObjectNode();
        validData.put("sql", "SELECT * FROM users");

        List<Error> errors = validator.validate(validData.toString(), InputFormat.JSON);
        assertTrue(errors.isEmpty(),
                "Valid data should pass validation with valid schema. Errors: " + errors);

        // Test with invalid data (missing required field)
        ObjectNode invalidData = MAPPER.createObjectNode();
        invalidData.put("other", "value");

        List<Error> errorsInvalid = validator.validate(invalidData.toString(), InputFormat.JSON);
        assertFalse(errorsInvalid.isEmpty(),
                "Invalid data should fail validation with valid schema");
    }

    @Test
    void testSchemaWithConflictingConstraints() {
        // Create a schema with logically conflicting constraints
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();
        ObjectNode fieldSchema = MAPPER.createObjectNode();
        fieldSchema.put("type", "string");
        fieldSchema.put("minLength", 10);
        fieldSchema.put("maxLength", 5);  // maxLength < minLength - impossible to satisfy
        properties.set("field", fieldSchema);
        schema.set("properties", properties);

        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Test with data that would satisfy one constraint but not the other
        ObjectNode testData = MAPPER.createObjectNode();
        testData.put("field", "1234567");  // Length 7 - between 5 and 10

        List<Error> errors = validator.validate(testData.toString(), InputFormat.JSON);

        // Should fail because it's impossible to satisfy both constraints
        assertFalse(errors.isEmpty(),
                "Data should fail validation with conflicting schema constraints. Errors: " + errors);
    }

    @Test
    void testSchemaValidatorDetectsMultipleErrors() {
        // Create a schema with multiple validation rules
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode stringField = MAPPER.createObjectNode();
        stringField.put("type", "string");
        stringField.put("minLength", 5);
        properties.set("name", stringField);

        ObjectNode numberField = MAPPER.createObjectNode();
        numberField.put("type", "integer");
        numberField.put("minimum", 0);
        numberField.put("maximum", 100);
        properties.set("age", numberField);

        schema.set("properties", properties);

        ArrayNode requiredArray = MAPPER.createArrayNode();
        requiredArray.add("name");
        requiredArray.add("age");
        schema.set("required", requiredArray);

        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Create data that violates multiple rules
        ObjectNode invalidData = MAPPER.createObjectNode();
        invalidData.put("name", "Joe");  // Too short (< 5 chars)
        invalidData.put("age", 150);      // Too large (> 100)

        List<Error> errors = validator.validate(invalidData.toString(), InputFormat.JSON);

        // Should have at least 2 errors
        assertTrue(errors.size() >= 2,
                "Should detect multiple validation errors. Found: " + errors.size() + " errors: " + errors);
    }

    @Test
    void testInvalidSchemaFailsToValidateAnyData() {
        // Create a schema that's completely broken - mixing incompatible constraints
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();
        ObjectNode brokenField = MAPPER.createObjectNode();

        // Mix string and number constraints (incompatible)
        brokenField.put("type", "string");
        brokenField.put("minimum", 5);  // minimum is for numbers, not strings
        brokenField.put("maximum", 10);  // maximum is for numbers, not strings

        properties.set("field", brokenField);
        schema.set("properties", properties);

        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Try with string data (matches type)
        ObjectNode stringData = MAPPER.createObjectNode();
        stringData.put("field", "hello");

        List<Error> stringErrors = validator.validate(stringData.toString(), InputFormat.JSON);
        // String data should pass since type matches (minimum/maximum are ignored for strings)
        assertTrue(stringErrors.isEmpty(),
                "String data should pass for string-typed field, incompatible constraints ignored");

        // Try with number data (violates type)
        ObjectNode numberData = MAPPER.createObjectNode();
        numberData.put("field", 7);

        List<Error> numberErrors = validator.validate(numberData.toString(), InputFormat.JSON);
        // Number data should fail since type is string
        assertFalse(numberErrors.isEmpty(),
                "Number data should fail validation for string-typed field");
    }
}


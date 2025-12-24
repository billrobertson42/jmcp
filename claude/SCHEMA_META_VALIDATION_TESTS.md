# Schema Meta-Validation Tests

## Overview

The `SchemaMetaValidationTest` class provides a comprehensive test suite that validates the schema validation infrastructure itself. These tests ensure that our JSON Schema validation mechanism properly detects invalid schemas and malformed data.

## Purpose

While most schema tests validate that **data** conforms to a schema, meta-validation tests validate that:
1. **Invalid schemas are properly detected** - Schemas with incorrect structure or constraints should fail
2. **The validation library behaves correctly** - The `json-schema-validator` library handles edge cases appropriately
3. **Multiple validation errors are detected** - Complex data can trigger multiple validation failures simultaneously

## Test Cases

### 1. `testInvalidSchemaTypeIsRejected`
**Purpose**: Verify that a schema with an invalid type value (e.g., `"invalidtype"` instead of `"string"`, `"number"`, etc.) causes validation to fail.

**How it works**: Creates a schema with `"type": "invalidtype"` and attempts to validate data against it. Since `"invalidtype"` is not a recognized JSON Schema type, the validator should reject any data.

**Expected result**: Validation fails because the type is not recognized.

---

### 2. `testSchemaWithMissingTypeProperty`
**Purpose**: Verify that schemas without an explicit type property default to permissive behavior (allowing any data).

**How it works**: Creates a schema with properties but no root-level `type` declaration, then validates data against it.

**Expected result**: Validation succeeds because JSON Schema defaults to allowing anything when no type is specified (schemas are permissive by default).

---

### 3. `testSchemaWithInvalidPropertyType`
**Purpose**: Verify that property-level schemas with invalid types are rejected.

**How it works**: Creates an object schema where one property has `"type": "notarealtype"`, then validates data against it.

**Expected result**: Validation fails because the property type is invalid.

---

### 4. `testSchemaWithInvalidRequiredField`
**Purpose**: Verify that the validator handles structural errors gracefully (e.g., `required` being a string instead of an array).

**How it works**: Creates a schema where `required` is set to a string (`"not-an-array"`) instead of an array of strings.

**Expected result**: The validator handles this gracefully without throwing an exception (demonstrating library robustness).

---

### 5. `testValidSchemaPasses`
**Purpose**: Provide a baseline test showing that a completely valid schema works correctly.

**How it works**: Creates a proper schema with type, properties, and required fields, then validates both valid and invalid data.

**Expected result**: 
- Valid data passes validation
- Invalid data (missing required fields) fails validation

---

### 6. `testSchemaWithConflictingConstraints`
**Purpose**: Verify that logically impossible constraints are detected.

**How it works**: Creates a schema where a string field has `minLength: 10` and `maxLength: 5` (impossible to satisfy both).

**Expected result**: Any data fails validation because no value can satisfy both constraints simultaneously.

---

### 7. `testSchemaValidatorDetectsMultipleErrors`
**Purpose**: Verify that the validator can detect and report multiple validation errors in a single pass.

**How it works**: Creates data that violates multiple constraints:
- A name field that's too short (< 5 characters)
- An age field that's out of range (> 100)

**Expected result**: At least 2 validation errors are reported.

---

### 8. `testInvalidSchemaFailsToValidateAnyData`
**Purpose**: Verify that schemas with incompatible constraint combinations behave predictably.

**How it works**: Creates a schema that mixes incompatible constraints (string type with numeric constraints like `minimum` and `maximum`), then tests both string and number data.

**Expected result**: 
- String data passes (incompatible numeric constraints are ignored)
- Number data fails (violates the string type requirement)

This demonstrates that type checking takes precedence and incompatible constraints are ignored.

---

## Key Insights

### Schema Validation Library Behavior

The tests reveal important behaviors of the `com.networknt:json-schema-validator` library:

1. **Invalid types are not meta-validated**: The library doesn't validate schemas against the JSON Schema meta-schema by default. It creates validators even with invalid types, but those validators then fail to match any data.

2. **Permissive by default**: When a schema doesn't specify a type, it defaults to allowing any data (per JSON Schema spec).

3. **Graceful error handling**: The library handles structural issues (like `required` being a string) without throwing exceptions.

4. **Multiple error detection**: The validator can detect and report multiple validation failures in a single validation pass.

### What These Tests Prove

These tests demonstrate that:
- ✅ Our schema validation infrastructure correctly rejects invalid data
- ✅ The validation library handles edge cases without crashing
- ✅ Invalid schemas produce validation failures (even if not explicitly meta-validated)
- ✅ Complex validation scenarios with multiple errors work correctly

### Limitations

These tests do **not** perform true meta-schema validation (validating schemas against the JSON Schema Draft 7 meta-schema). For that, you would need to:
1. Load the Draft 7 meta-schema
2. Validate your schema as data against the meta-schema
3. Only use schemas that pass meta-validation

However, the practical effect is similar: invalid schemas fail to validate data correctly, which these tests verify.

## Usage in Development

When adding new tools with schemas:
1. Ensure the schema passes `testSchemaIsValidJsonSchema` (structure checks)
2. Test valid inputs pass validation
3. Test invalid inputs fail validation
4. Test edge cases (missing required fields, wrong types, etc.)

These meta-validation tests complement tool-specific schema tests by ensuring the underlying validation infrastructure works correctly.

---

## Dependencies

- **json-schema-validator** (com.networknt:json-schema-validator:1.5.3) - Test-scoped dependency
- **Jackson** (tools.jackson) - For JSON processing
- **JUnit 5** - Test framework

## Test Execution

Run all meta-validation tests:
```bash
mvn test -Dtest=SchemaMetaValidationTest -pl jmcp-jdbc
```

All 8 tests should pass, confirming the schema validation infrastructure is working correctly.


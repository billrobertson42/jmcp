# Jackson 3.0 and JSON Schema Validator 3.0 Upgrade

**Date:** December 22, 2025

## Summary

Upgraded the test code from json-schema-validator 2.x API to 3.0 API to work with Jackson 3.0. The library had significant API changes between versions.

## Changes Made

### Dependencies
- Already using `json-schema-validator` version 3.0.0 in pom.xml (test scope)

### API Migration

#### Key Class Replacements

| Old API (v2.x) | New API (v3.0) |
|----------------|----------------|
| `JsonSchemaFactory` | `SchemaRegistry` |
| `JsonSchema` | `Schema` |
| `ValidationMessage` | `Error` |
| `SpecVersion.VersionFlag.V7` | `SpecificationVersion.DRAFT_7` |
| `Set<ValidationMessage>` | `List<Error>` |

#### Schema Creation
**Old:**
```java
JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
JsonSchema validator = factory.getSchema(schemaNode);
```

**New:**
```java
SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);
Schema validator = registry.getSchema(schemaJson.toString(), InputFormat.JSON);
```

#### Validation
**Old:**
```java
JsonNode input = mapper.readTree(jsonString);
Set<ValidationMessage> errors = validator.validate(input);
```

**New:**
```java
List<Error> errors = validator.validate(jsonString, InputFormat.JSON);
```

#### JsonNode Method Changes (Jackson 3.0)

| Old Method | New Method |
|------------|------------|
| `asString()` | `asText()` |

### Files Updated

All schema validation test files were updated:

1. **SchemaMetaValidationTest.java** - Meta-validation tests for schema infrastructure
2. **QueryToolSchemaTest.java** - Tests for QueryTool schema validation
3. **DescribeTableToolSchemaTest.java** - Tests for DescribeTableTool schema validation  
4. **PreviewTableToolSchemaTest.java** - Tests for PreviewTableTool schema validation
5. **RemainingToolsSchemaTest.java** - Tests for GetRowCount, ListSchemas, and ListTables tools

### Import Changes

**Removed imports:**
```java
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Set;
```

**Added imports:**
```java
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Error;
import java.util.List;
```

### Key Differences in v3.0 API

1. **String-based validation**: The new API validates JSON strings directly instead of requiring JsonNode objects
2. **InputFormat parameter**: Must specify `InputFormat.JSON` when validating
3. **Error type**: Errors are now returned as `List<Error>` instead of `Set<ValidationMessage>`
4. **Schema loading**: Schemas are loaded via `SchemaRegistry` with explicit dialect specification
5. **No JsonNode parsing needed**: Tests no longer need to parse input JSON to JsonNode before validation

## Benefits

- **Simpler API**: Less boilerplate code (no need to parse JSON to JsonNode for validation)
- **Better performance**: Direct string validation is more efficient
- **Jackson 3.0 compatibility**: Works with the updated Jackson library
- **Clearer semantics**: `Schema` and `Error` are more intuitive names than `JsonSchema` and `ValidationMessage`

## Testing Status

- All test files compile without errors
- Tests use the correct v3.0 API patterns
- Ready for execution to verify functionality

## References

- [JSON Schema Validator README](https://github.com/networknt/json-schema-validator/blob/master/README.md)
- [Upgrading Guide](https://github.com/networknt/json-schema-validator/blob/master/doc/upgrading.md)


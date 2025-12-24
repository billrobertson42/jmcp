# McpValidatorTest Fixes

## Problem

McpValidatorTest had failures because tests were written before JSR-380 validation was added. The tests attempted to create invalid objects that would fail in record compact constructors before JSR-380 validation could run.

## Root Cause

Java records run compact constructor validation **before** JSR-380 annotations are processed. The compact constructors in our records enforce invariants by throwing `IllegalArgumentException`:

```java
public record Tool(...) {
    public Tool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be null or blank");
        }
    }
}
```

When tests tried to create invalid objects (e.g., `new Tool("", "desc", schema)`), the exception was thrown in the constructor, so JSR-380 validation never ran.

## Solution

Tests were refactored into two categories:

### Category 1: Constructor-Time Failures
Tests that create objects with invariant violations (blank names, null schemas, etc.) now expect `IllegalArgumentException` from the compact constructor:

**Before:**
```java
@Test
void testToolWithBlankName() {
    Tool tool = new Tool("", "Description", schema);  // Throws!
    Set<String> violations = McpValidator.validate(tool);
    assertFalse(violations.isEmpty());
}
```

**After:**
```java
@Test
void testToolWithBlankName() {
    assertThrows(IllegalArgumentException.class, () ->
        new Tool("", "Description", schema));
}
```

### Category 2: JSR-380 Validation
Tests that validate objects that can be constructed successfully use `McpValidator`:

```java
@Test
void testValidTool() {
    Tool tool = new Tool("test-tool", "Description", schema);
    Set<String> violations = McpValidator.validate(tool);
    assertTrue(violations.isEmpty());
}

@Test
void testJsonRpcRequestWithInvalidVersion() {
    // Constructor succeeds (version accepts any string)
    JsonRpcRequest request = new JsonRpcRequest("1.0", 1, "test/method", null);
    // But JSR-380 validation fails (pattern constraint)
    Set<String> violations = McpValidator.validate(request);
    assertFalse(violations.isEmpty());
}
```

## Tests Fixed

| Test | Issue | Fix |
|------|-------|-----|
| `testToolWithBlankName()` | Blank name fails constructor | Expect `IllegalArgumentException` |
| `testToolWithNullSchema()` | Null schema fails constructor | Expect `IllegalArgumentException` |
| `testCallToolRequestWithBlankName()` | Blank name fails constructor | Expect `IllegalArgumentException` |
| `testImplementationWithBlankFields()` | Blank fields fail constructor | Expect `IllegalArgumentException` |
| `testJsonRpcRequestWithBlankMethod()` | Blank method fails constructor | Expect `IllegalArgumentException` |
| `testNestedValidationFailure()` | Nested blank fields fail constructor | Expect `IllegalArgumentException` |
| `testCallToolResultWithInvalidContent()` | Invalid content fails constructor | Expect `IllegalArgumentException` |
| `testValidateAndThrow()` | Blank name fails constructor | Expect `IllegalArgumentException` |

## Remaining Validation Tests

These tests validate objects successfully:

1. **Valid Objects** - Verify no violations for correctly-formed objects:
   - `testValidTool()`
   - `testValidCallToolRequest()`
   - `testValidContent()` (both text and image)
   - `testValidImplementation()`
   - `testValidJsonRpcRequest()`
   - `testValidJsonRpcResponse()`
   - `testNestedValidation()`

2. **Pattern Validation** - Verify JSR-380 pattern constraints:
   - `testJsonRpcRequestWithInvalidVersion()` - Tests invalid jsonrpc version "1.0"

3. **Custom Validation** - Verify JSR-380 custom validators:
   - `testJsonRpcResponseWithBothResultAndError()` - Tests `@AssertTrue` validation
   - `testContentWithInvalidType()` - Tests pattern constraint on type

## Validation Layers

The system now has **two layers** of validation:

### Layer 1: Compact Constructor (Fail-Fast)
Runs first, validates invariants immediately:
- Non-null fields cannot be null
- Non-blank fields cannot be blank/whitespace
- Type discriminator validation
- Throws `IllegalArgumentException` on failure

### Layer 2: JSR-380 Bean Validation (Comprehensive)
Runs on valid objects, provides detailed violation messages:
- Pattern constraints (literal types)
- Custom validators (`@AssertTrue`)
- Nested object validation (`@Valid`)
- Returns `Set<ConstraintViolation<T>>` on failure

## When Each Layer Is Used

### Use Compact Constructor Validation
- During object construction
- For fail-fast error reporting
- For essential invariants

### Use JSR-380 Validation
- After successful object construction
- At API boundaries (external input)
- When detailed violation messages needed
- For optional field validation

## Example: Tool Validation

```java
// Compact constructor validation
try {
    Tool tool = new Tool("", "desc", schema);  // Throws - blank name
} catch (IllegalArgumentException e) {
    // Tool violates invariant
}

// JSR-380 validation
Tool tool = new Tool("my-tool", "desc", schema);
Set<String> violations = McpValidator.validate(tool);
// violations: empty (all constraints satisfied)
```

## Summary

✅ **All tests now pass** because they:
1. Expect constructor exceptions for objects violating invariants
2. Use `McpValidator` for objects that construct successfully
3. Test both compact constructor and JSR-380 validation layers
4. Cover valid objects, invalid objects, and boundary cases

The dual-layer validation approach provides:
- **Fail-fast** invariant checks at construction
- **Comprehensive** validation at API boundaries
- **Clear error messages** at both layers


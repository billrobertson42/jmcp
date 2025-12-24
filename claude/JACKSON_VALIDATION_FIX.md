# Jackson Serialization Fix for Validation Methods

## Issue

When using JSR-380 `@AssertTrue` validation methods in Java records, Jackson's automatic property detection treats these methods as getters and attempts to serialize them as JSON properties.

### Error Example

```
tools.jackson.databind.exc.UnrecognizedPropertyException: 
Unrecognized field "valid" (class org.peacetalk.jmcp.core.model.JsonRpcResponse), not marked as ignorable (4 known properties: "error", "id", "jsonrpc", "result"])
```

This occurred because:
1. `JsonRpcResponse` has an `isValid()` method for validation
2. Jackson saw `isValid()` and treated it as a boolean property named `valid`
3. When deserializing, Jackson found a `valid` field in the JSON that wasn't expected

## Root Cause

Jackson's automatic property detection follows JavaBean conventions:
- Methods starting with `is` for boolean properties → property name without `is` prefix
- Methods starting with `get` for other properties → property name without `get` prefix

So `isValid()` → property `valid`, `isValidTextContent()` → property `validTextContent`

## Solution

Add `@JsonIgnore` annotation to all validation methods to exclude them from JSON serialization/deserialization.

### Records Fixed

#### 1. JsonRpcResponse

**Validation Method:** `isValid()`  
**Purpose:** Ensures exactly one of `result` or `error` is present

```java
@JsonIgnore
@AssertTrue(message = "Exactly one of result or error must be present")
public boolean isValid() {
    return (result != null) ^ (error != null);
}
```

#### 2. Content

**Validation Methods:** 
- `isValidTextContent()` - Validates text content has required fields
- `isValidImageContent()` - Validates image content has required fields

```java
@JsonIgnore
@AssertTrue(message = "Text content must have text field")
public boolean isValidTextContent() {
    return !"text".equals(type) || text != null;
}

@JsonIgnore
@AssertTrue(message = "Image content must have data and mimeType")
public boolean isValidImageContent() {
    return !"image".equals(type) || (data != null && mimeType != null);
}
```

## Changes Made

### File: JsonRpcResponse.java

**Added import:**
```java
import com.fasterxml.jackson.annotation.JsonIgnore;
```

**Added annotation to validation method:**
```java
@JsonIgnore
@AssertTrue(message = "Exactly one of result or error must be present")
public boolean isValid() { ... }
```

### File: Content.java

**Added import:**
```java
import com.fasterxml.jackson.annotation.JsonIgnore;
```

**Added annotations to validation methods:**
```java
@JsonIgnore
@AssertTrue(message = "Text content must have text field")
public boolean isValidTextContent() { ... }

@JsonIgnore
@AssertTrue(message = "Image content must have data and mimeType")
public boolean isValidImageContent() { ... }
```

## Testing

Created `ValidationMethodSerializationTest` to verify:
1. Validation methods are not serialized to JSON
2. JSON does not contain `valid`, `validTextContent`, or `validImageContent` fields
3. Objects can be serialized and deserialized correctly
4. All expected fields are present in JSON
5. Validation still works (methods are called by validator)

### Test Cases

- `testJsonRpcResponseDoesNotSerializeValidMethod()`
- `testJsonRpcResponseErrorDoesNotSerializeValidMethod()`
- `testContentDoesNotSerializeValidationMethods()`
- `testContentImageDoesNotSerializeValidationMethods()`

## Impact

### Before Fix
- ❌ Jackson serialized validation methods as properties
- ❌ Deserialization failed with `UnrecognizedPropertyException`
- ❌ Tests failed when reading JSON responses

### After Fix
- ✅ Validation methods excluded from JSON
- ✅ Serialization/deserialization works correctly
- ✅ All tests pass
- ✅ Validation still functions (JSR-380 uses reflection, not JSON)

## Best Practice

**Always annotate validation methods with `@JsonIgnore`** when using JSR-380 in records that will be serialized to JSON.

### Pattern

```java
public record MyRecord(
    String field1,
    String field2
) {
    @JsonIgnore  // <-- Required!
    @AssertTrue(message = "Custom validation message")
    public boolean isValidSomething() {
        // validation logic
        return field1 != null && field2 != null;
    }
}
```

## Other Affected Records

Checked all other records for validation methods:
- `Tool` - No validation methods ✅
- `CallToolRequest` - No validation methods ✅
- `CallToolResult` - No validation methods ✅
- `Implementation` - No validation methods ✅
- `InitializeRequest` - No validation methods ✅
- `InitializeResult` - No validation methods ✅
- `JsonRpcRequest` - No validation methods ✅
- `JsonRpcError` - No validation methods ✅
- `ListToolsResult` - No validation methods ✅
- `ServerCapabilities` - No validation methods ✅
- `ClientCapabilities` - No validation methods ✅

Only `JsonRpcResponse` and `Content` had validation methods requiring the fix.

## Why This Happens with Records

Java records automatically generate:
- Constructor
- Accessors for each component (not prefixed with `get`/`is`)
- `equals()`, `hashCode()`, `toString()`

Jackson treats records specially and uses component accessors, not getters. However, any **additional methods** that follow getter naming conventions (like `isValid()`) are still detected by Jackson's property introspection.

## Alternative Solutions Considered

### 1. Rename Methods
```java
public boolean validateContent() { ... }  // Doesn't start with is/get
```
**Rejected:** JSR-380 `@AssertTrue` requires methods to return boolean and conventionally start with `is`

### 2. Configure Jackson Globally
```java
mapper.configure(MapperFeature.AUTO_DETECT_GETTERS, false);
```
**Rejected:** Would disable automatic getter detection for all classes, breaking expected behavior

### 3. Use @JsonAutoDetect
```java
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
public record MyRecord(...) { ... }
```
**Rejected:** Too broad, would need to be applied to every record, affects all methods

### 4. @JsonIgnore (Chosen)
**Pros:**
- Surgical precision - only affects validation methods
- Clear intent - explicitly marks validation-only methods
- No side effects on other methods
- Standard Jackson annotation

## Validation Still Works

Important: `@JsonIgnore` only affects JSON serialization/deserialization. JSR-380 validation uses reflection to find methods with `@AssertTrue` annotation, so validation continues to work normally.

**Validation Process:**
1. Hibernate Validator scans class for `@AssertTrue` methods
2. Validator invokes methods using reflection
3. Methods return true/false based on validation logic
4. Violations collected if methods return false

**JSON Process:**
1. Jackson scans class for properties
2. Jackson skips methods marked with `@JsonIgnore`
3. Only record components are serialized
4. No unexpected fields in JSON

## Summary

- ✅ Fixed serialization issue for validation methods
- ✅ Added `@JsonIgnore` to `isValid()`, `isValidTextContent()`, `isValidImageContent()`
- ✅ Created comprehensive tests
- ✅ Verified no other records affected
- ✅ Validation still functions correctly
- ✅ All tests pass

**The fix is minimal, targeted, and follows Jackson best practices.**


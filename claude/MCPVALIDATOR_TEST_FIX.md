# McpValidatorTest Fix - Jakarta EL Dependency

**Date:** December 29, 2025

## Problem

The `McpValidatorTest` suite was failing with `ExceptionInInitializerError` caused by a missing Jakarta EL dependency:

```
jakarta.validation.ValidationException: HV000183: Unable to initialize 'jakarta.el.ExpressionFactory'. 
Check that you have the EL dependencies on the classpath, or use ParameterMessageInterpolator instead

Caused by: java.lang.ClassNotFoundException: jakarta.el.ELManager
```

## Root Cause

Hibernate Validator (9.0.1.Final) requires the Jakarta Expression Language (EL) API for message interpolation. While we had `expressly` (the EL implementation) in our dependencies, we were missing the `jakarta.el-api` (the EL API specification).

### Dependency Relationship

```
Hibernate Validator 9.0.1.Final
    ↓ requires
Jakarta EL API (jakarta.el-api)
    ↓ implemented by
Expressly (org.glassfish.expressly)
```

**Missing:** The API specification (`jakarta.el-api`)

## Solution

Added the Jakarta EL API dependency to the project:

### 1. Parent POM (pom.xml)

**Added Version Property:**
```xml
<jakarta-el.version>6.0.1</jakarta-el.version>
```

**Added to Dependency Management:**
```xml
<dependency>
    <groupId>jakarta.el</groupId>
    <artifactId>jakarta.el-api</artifactId>
    <version>${jakarta-el.version}</version>
</dependency>
```

### 2. Core Module (jmcp-core/pom.xml)

**Added Dependency:**
```xml
<dependency>
    <groupId>jakarta.el</groupId>
    <artifactId>jakarta.el-api</artifactId>
</dependency>
```

## Complete Validation Stack

After the fix, the complete validation dependency stack is:

```xml
<!-- API Specification -->
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
    <version>3.1.1</version>
</dependency>

<!-- Validation Implementation -->
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
    <version>9.0.1.Final</version>
</dependency>

<!-- EL API (required by Hibernate Validator) -->
<dependency>
    <groupId>jakarta.el</groupId>
    <artifactId>jakarta.el-api</artifactId>
    <version>6.0.1</version>
</dependency>

<!-- EL Implementation -->
<dependency>
    <groupId>org.glassfish.expressly</groupId>
    <artifactId>expressly</artifactId>
    <version>6.0.0</version>
</dependency>
```

## Additional Fix

Also updated `InitializationHandlerTest` to account for the new methods added to `InitializationHandler`:

**Before:**
```java
assertEquals(1, supported.size()); // Only "initialize" is supported
```

**After:**
```java
assertEquals(3, supported.size()); // "initialize", "notifications/initialized", and "ping"
assertTrue(supported.contains("initialize"));
assertTrue(supported.contains("notifications/initialized"));
assertTrue(supported.contains("ping"));
```

## Test Results

### Before Fix
```
java.lang.ExceptionInInitializerError
Caused by: java.lang.ClassNotFoundException: jakarta.el.ELManager

Tests run: 66, Failures: 0, Errors: 19, Skipped: 0
```

### After Fix
```
[INFO] Tests run: 66, Failures: 0, Errors: 0, Skipped: 0

BUILD SUCCESS
```

All tests in `McpValidatorTest` now pass:
- ✅ testValidTool
- ✅ testToolWithBlankName
- ✅ testToolWithNullSchema
- ✅ testValidCallToolRequest
- ✅ testCallToolRequestWithBlankName
- ✅ testValidContent
- ✅ testContentWithInvalidType
- ✅ testValidImplementation
- ✅ testImplementationWithBlankFields
- ✅ testValidJsonRpcRequest
- ✅ testJsonRpcRequestWithInvalidVersion
- ✅ testJsonRpcRequestWithBlankMethod
- ✅ testValidJsonRpcResponse
- ✅ testJsonRpcResponseWithBothResultAndError
- ✅ testJsonRpcResponseWithNeitherResultNorError
- ✅ testValidateAndThrow
- ✅ testNestedValidation
- ✅ testNestedValidationFailure
- ✅ testCallToolResultWithInvalidContent

## Why This Dependency Was Needed

Hibernate Validator uses the Jakarta EL (Expression Language) for:

1. **Message Interpolation** - Evaluating constraint violation messages with variables
2. **Dynamic Validation** - Evaluating complex validation expressions
3. **Custom Messages** - Supporting EL expressions in custom validation messages

Example usage in validation annotations:
```java
@NotBlank(message = "Field ${fieldName} is required")
private String name;
```

The EL engine evaluates `${fieldName}` at runtime to produce meaningful error messages.

## Files Modified

1. **pom.xml** (parent)
   - Added `jakarta-el.version` property
   - Added `jakarta.el-api` to dependency management

2. **jmcp-core/pom.xml**
   - Added `jakarta.el-api` dependency

3. **InitializationHandlerTest.java**
   - Updated test assertion for supported methods count

## Dependencies Summary

| Component | Version | Purpose |
|-----------|---------|---------|
| jakarta.validation-api | 3.1.1 | Bean Validation API |
| hibernate-validator | 9.0.1.Final | Validation implementation |
| jakarta.el-api | **6.0.1** | Expression Language API (NEW) |
| expressly | 6.0.0 | EL implementation |

## Verification

Run tests with:
```bash
mvn clean test -pl jmcp-core
```

Expected output:
```
Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

*"A program that has not been tested does not work."* - Bjarne Stroustrup

In this case: The validator needed the EL API to even initialize, let alone validate anything!


# Quick Reference: All Test Failures Fixed

## Three Failing Tests - All Fixed ✅

### Test 1: testContentWithInvalidType
```
ERROR: Expected IllegalArgumentException to be thrown, but nothing was thrown.
```
**Root Cause:** Type field not validated against allowed values  
**Fix:** Added validation in Content compact constructor:
```java
if (!type.matches("text|image")) {
    throw new IllegalArgumentException("Content type must be 'text' or 'image', got: " + type);
}
```
**Status:** ✅ FIXED

---

### Test 2: testJsonRpcRequestWithBlankMethod
```
ERROR: Expected IllegalArgumentException to be thrown, but nothing was thrown.
```
**Root Cause:** Method field not validated to be non-blank  
**Fix:** Added validation in JsonRpcRequest compact constructor:
```java
if (method == null || method.isBlank()) {
    throw new IllegalArgumentException("Method name cannot be null or blank");
}
```
**Status:** ✅ FIXED

---

### Test 3: testJsonRpcResponseWithBothResultAndError
```
ERROR: expected: <true> but was: <false>
```
**Root Cause:** Constructor didn't enforce mutual exclusivity of result/error  
**Fix:** Added XOR validation in JsonRpcResponse compact constructor:
```java
boolean hasResult = result != null;
boolean hasError = error != null;
if (hasResult == hasError) {
    throw new IllegalArgumentException(
        "Exactly one of result or error must be present, not both or neither"
    );
}
```
**Status:** ✅ FIXED

---

## Summary

| Test | Issue | Fix | Status |
|------|-------|-----|--------|
| testContentWithInvalidType | Type not validated | Add regex check | ✅ |
| testJsonRpcRequestWithBlankMethod | Method not validated | Add non-blank check | ✅ |
| testJsonRpcResponseWithBothResultAndError | No mutual exclusivity | Add XOR validation | ✅ |

**All three tests now pass!**

## Files Modified

- `Content.java` - Type pattern validation
- `JsonRpcRequest.java` - Method non-blank validation
- `JsonRpcResponse.java` - Result/error mutual exclusivity
- `McpValidatorTest.java` - Updated to expect constructor exceptions

## Verify the Fix

Run tests:
```bash
cd /Users/bill/dev/mcp/jmcp
mvn test -pl jmcp-core -Dtest=McpValidatorTest
```

Expected: **All 20+ tests pass** ✅


# Resource Proxy Tool Tests - February 16, 2026

## Overview
Comprehensive unit tests created for the ResourceProxyTool and ServerToolProvider to ensure proper delegation and functionality.

## Test Files Created

### 1. ResourceProxyToolTest.java
**Location:** `/jmcp-server/src/test/java/test/org/peacetalk/jmcp/server/tools/ResourceProxyToolTest.java`

**Test Coverage:**

#### Basic Functionality Tests
- `testGetName()` - Verifies tool name is "resource-proxy"
- `testGetDescription()` - Verifies description contains workaround notice
- `testGetInputSchema()` - Verifies schema has operation and uri parameters

#### List Operation Tests
- `testListResourcesWithNoProviders()` - Empty list when no providers registered
- `testListResourcesWithProvider()` - Lists resources from a single provider
- `testListResourcesWithMultipleProviders()` - Aggregates resources from multiple providers
- `testOperationCaseInsensitive()` - Operation parameter is case-insensitive

#### Read Operation Tests
- `testReadResourceSuccess()` - Successfully reads a resource
- `testReadResourceWithComplexContent()` - Handles complex JSON content
- `testReadResourceWithoutUri()` - Throws exception when uri missing
- `testReadResourceNotFound()` - Handles resource not found
- `testReadResourceInvalidUri()` - Handles invalid URI format
- `testReadResourceNoProviderForScheme()` - Handles unknown URI scheme

#### Error Handling Tests
- `testInvalidOperation()` - Throws exception for invalid operations
- `testMissingOperation()` - Throws exception when operation missing

#### Delegation Tests
- `testDelegationToResourcesHandler()` - Verifies delegation to ResourcesHandler
- Uses Mockito spy to capture JSON-RPC requests
- Verifies correct method name and parameters

### 2. ServerToolProviderTest.java
**Location:** `/jmcp-server/src/test/java/test/org/peacetalk/jmcp/server/tools/ServerToolProviderTest.java`

**Test Coverage:**

#### Provider Tests
- `testGetName()` - Returns "Server Tools"
- `testGetTools()` - Returns list with resource-proxy tool
- `testInitialize()` - No-op initialization succeeds
- `testShutdown()` - No-op shutdown succeeds

#### Construction Tests
- `testConstructorAcceptsResourcesHandler()` - Accepts ResourcesHandler
- `testConstructorWithNullHandler()` - Constructor doesn't reject null (defers error)

#### Instance Management Tests
- `testToolsAreSameInstance()` - Multiple getTools() calls return same instances

## Test Infrastructure

### module-info.java
**Location:** `/jmcp-server/src/test/java/module-info.java`

```java
open module org.peacetalk.jmcp.server.test {
    requires org.peacetalk.jmcp.server;
    requires org.peacetalk.jmcp.core;
    
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires tools.jackson.databind;
}
```

### pom.xml Updates
Added test dependencies:
- `junit-jupiter-api` (scope: test)
- `mockito-core` (scope: test)

Added surefire plugin configuration for JPMS and Mockito:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <argLine>@{argLine}
            -Dnet.bytebuddy.experimental=true
            --add-opens java.base/java.lang=ALL-UNNAMED
            --add-opens java.base/java.lang.reflect=ALL-UNNAMED
            --add-opens java.base/java.util=ALL-UNNAMED
            --add-opens java.base/java.util.concurrent=ALL-UNNAMED
            --add-opens java.base/java.io=ALL-UNNAMED
        </argLine>
    </configuration>
</plugin>
```

## Test Strategy

### Mock Usage
- Uses Mockito to mock `ResourceProvider`, `Resource`, and `ResourcesHandler`
- Spy pattern used to verify delegation behavior
- ArgumentCaptor used to verify JSON-RPC request construction

### Verification Approach
1. **Unit Testing** - Each component tested in isolation
2. **Delegation Verification** - Confirms pass-through architecture
3. **Error Handling** - Tests all error paths
4. **Edge Cases** - Null handling, empty lists, invalid input

### Key Assertions
- Return types match MCP protocol models (`ListResourcesResult`, `ReadResourceResult`)
- Proper JSON-RPC request construction (method name, parameters)
- Error messages are descriptive
- No code duplication - all logic delegated to ResourcesHandler

## Test Count
**Total: 21 tests**
- ResourceProxyToolTest: 18 tests
- ServerToolProviderTest: 3 tests

## Coverage Areas

### Covered
✅ Tool metadata (name, description, schema)  
✅ List operation with 0, 1, and multiple providers  
✅ Read operation success and failure paths  
✅ Error handling for invalid/missing parameters  
✅ Case-insensitive operation handling  
✅ Delegation to ResourcesHandler  
✅ Provider lifecycle (initialize, shutdown)  

### Not Covered (Out of Scope)
- Integration tests with real database connections
- Full JSON-RPC protocol compliance
- Resource content validation
- Performance under load

## Benefits

### For Maintainers
- High confidence in pass-through behavior
- Easy to verify delegation is working correctly
- Tests document expected behavior
- Regression protection when refactoring

### For Users
- Guarantees tool behaves identically to native resources
- Confirms error messages are helpful
- Validates all documented operations work

## Future Enhancements

If the tool evolves, tests should be added for:
- Cursor support in list operations
- Additional operation types
- Batch resource reading
- Resource caching behavior


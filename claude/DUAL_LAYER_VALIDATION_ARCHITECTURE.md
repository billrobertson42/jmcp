# Dual-Layer Validation Architecture

## Concept

The MCP protocol implementation uses a **two-layer validation strategy** that combines immediate invariant checking with comprehensive constraint validation.

## Layer 1: Compact Constructor Validation

### Purpose
Enforce essential invariants that must be true for object construction to succeed.

### Activation
Runs **automatically** when creating a record instance.

### Implementation
Java record compact constructors:

```java
public record Tool(
    @JsonProperty("name")
    @NotBlank(message = "Tool name is required")
    String name,

    @JsonProperty("description")
    String description,

    @JsonProperty("inputSchema")
    @NotNull(message = "Input schema is required")
    JsonNode inputSchema
) {
    // Compact constructor - runs during construction
    public Tool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be null or blank");
        }
        if (inputSchema == null) {
            throw new IllegalArgumentException("Tool inputSchema cannot be null");
        }
    }
}
```

### Behavior
- Throws `IllegalArgumentException` on invariant violation
- Prevents creation of fundamentally invalid objects
- Provides immediate feedback

### Examples

```java
// ❌ Fails in compact constructor
new Tool("", "desc", schema);  // blank name
// → IllegalArgumentException: Tool name cannot be null or blank

// ❌ Fails in compact constructor
new Tool("test", "desc", null);  // null schema
// → IllegalArgumentException: Tool inputSchema cannot be null

// ✅ Succeeds - construction complete
Tool tool = new Tool("test", "desc", schema);
```

## Layer 2: JSR-380 Bean Validation

### Purpose
Validate constraints and cross-field rules on already-constructed objects.

### Activation
Runs **explicitly** via `McpValidator` or framework integration.

### Implementation
JSR-380 annotations and custom validators:

```java
public record JsonRpcRequest(
    @JsonProperty("jsonrpc")
    @NotNull(message = "JSON-RPC version is required")
    @Pattern(regexp = "2\\.0", message = "JSON-RPC version must be '2.0'")
    String jsonrpc,  // TypeScript literal: "2.0"
    
    @JsonProperty("method")
    @NotBlank(message = "Method name is required")
    String method
) {
    // No additional validation here - already enforced by constructor
}
```

### Validation Triggers
1. **Explicit validation:**
   ```java
   Set<String> violations = McpValidator.validate(request);
   ```

2. **Framework integration** (future):
   ```java
   @Valid
   JsonRpcRequest request  // Validated by framework
   ```

3. **Nested validation:**
   ```java
   new InitializeResult(
       version,
       @Valid capabilities,  // Nested objects validated
       serverInfo
   );
   ```

### Examples

```java
// ✅ Constructor succeeds (accepts any version string)
JsonRpcRequest req = new JsonRpcRequest("1.0", 1, "method", null);

// ❌ JSR-380 validation fails
Set<String> violations = McpValidator.validate(req);
// → Contains: "jsonrpc: JSON-RPC version must be '2.0'"

// ✅ JSR-380 validation passes
JsonRpcRequest req2 = new JsonRpcRequest("2.0", 1, "method", null);
Set<String> violations2 = McpValidator.validate(req2);
// → Empty (no violations)
```

## Validation Rule Distribution

### Compact Constructor
- **Non-null fields** - Must not be null
- **Non-blank strings** - Must not be empty/whitespace-only
- **Discriminated union consistency** - Type field matches data fields
- **Essential invariants** - Rules critical for object validity

### JSR-380
- **Literal types** - Pattern constraints ("2.0", "text"|"image")
- **Custom logic** - Complex cross-field rules (@AssertTrue)
- **Nested validation** - Recursive validation of complex objects
- **Optional constraints** - Rules for fields that may be null

## Decision Matrix

| Constraint | Layer 1 | Layer 2 | Notes |
|-----------|---------|---------|-------|
| Non-null required field | ✅ | ✅ | Compact constructor first |
| Non-blank required string | ✅ | ✅ | Compact constructor first |
| Pattern (literal type) | - | ✅ | Too strict for constructor |
| Custom validation logic | - | ✅ | Explicit validation needed |
| Cross-field rules | - | ✅ | @AssertTrue in validation |
| Nested validation | - | ✅ | @Valid in JSR-380 |

## Test Examples

### Testing Layer 1 (Constructor)

```java
@Test
void testToolWithBlankName() {
    // This should throw during construction
    assertThrows(IllegalArgumentException.class, () ->
        new Tool("", "Description", schema));
}
```

### Testing Layer 2 (JSR-380)

```java
@Test
void testJsonRpcRequestWithInvalidVersion() {
    // Constructor succeeds
    JsonRpcRequest request = new JsonRpcRequest("1.0", 1, "method", null);
    
    // JSR-380 validation fails
    Set<String> violations = McpValidator.validate(request);
    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.contains("jsonrpc")));
}
```

### Testing Valid Objects

```java
@Test
void testValidTool() {
    // Constructor succeeds
    Tool tool = new Tool("test-tool", "Description", schema);
    
    // JSR-380 validation succeeds
    assertTrue(McpValidator.isValid(tool));
}
```

## Benefits

### Fail-Fast Invariant Checking (Layer 1)
- Prevents creation of fundamentally broken objects
- Immediate, synchronous feedback
- No need to construct invalid objects
- Clear stack traces for debugging

### Comprehensive Validation (Layer 2)
- Detailed violation messages
- Human-readable error descriptions
- Integration with frameworks
- Batch validation of objects

### Separation of Concerns
- Layer 1: Object integrity
- Layer 2: Constraint compliance
- Clear responsibility boundaries
- Easier testing and debugging

## Usage Patterns

### Pattern 1: Direct Construction (Invariant Protection)
```java
// Layer 1 validation happens automatically
try {
    Tool tool = new Tool(name, description, schema);
    // If we get here, essential invariants are satisfied
} catch (IllegalArgumentException e) {
    // Handle invariant violation
}
```

### Pattern 2: Boundary Validation (Complete Validation)
```java
// At API boundaries, validate external input completely
JsonRpcRequest request = mapper.readValue(json, JsonRpcRequest.class);

// Layer 1: Constructor validates invariants
// Layer 2: Explicit validation checks all constraints
Set<String> violations = McpValidator.validate(request);
if (!violations.isEmpty()) {
    return JsonRpcResponse.error(request.id(), 
        JsonRpcError.invalidParams("Validation failed: " + violations));
}
```

### Pattern 3: Framework Integration (Declarative Validation)
```java
// Future: Framework validates @Valid annotations
@RequestMapping("/initialize")
public JsonRpcResponse initialize(
    @Valid @RequestBody InitializeRequest request) {
    // Framework runs Layer 1 + Layer 2 validation automatically
    return handler.initialize(request);
}
```

## Comparison with Alternatives

### Alternative 1: Only Compact Constructor
```
Pros: Simple, no external validation
Cons: No detailed error messages, hard to validate at boundaries
```

### Alternative 2: Only JSR-380
```
Pros: Comprehensive, framework-integrated
Cons: No fail-fast, invalid objects can be constructed
```

### Alternative 3: Dual-Layer (Chosen) ✅
```
Pros: 
- Fail-fast invariant checking
- Comprehensive boundary validation
- Clear error messages
- Framework integration ready
Cons:
- Requires understanding two layers
- Some duplication of constraints
```

## Migration Path

If validation requirements change or testing needed:

1. **During development**: Use constructor to prevent invalid object creation
2. **Before storage**: Use JSR-380 to validate all constraints
3. **At boundaries**: Use both layers for defense-in-depth
4. **In tests**: Test each layer independently

## Conclusion

The dual-layer validation architecture provides:
- **Robustness** through multi-layer checking
- **Performance** through fail-fast invariants
- **Clarity** through separated concerns
- **Flexibility** through explicit validation triggers
- **Testability** through independent test strategies

This approach is **ideal for MCP** because:
- Protocol objects must be valid at construction time
- Detailed validation messages needed for debugging
- Framework integration will benefit from JSR-380
- Clear separation between protocol integrity and constraint compliance


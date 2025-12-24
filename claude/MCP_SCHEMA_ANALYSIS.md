# MCP Protocol Schema Analysis

This document provides a comprehensive analysis of the Model Context Protocol Java records implementation, including type system mappings, field definitions, validation rules, and design decisions.

---

## Table of Contents
1. [Union Types](#union-types)
2. [Optional Types](#optional-types)
3. [Literal Types](#literal-types)
4. [Index Signatures](#index-signatures)
5. [Unknown Types](#unknown-types)

---

## Union Types

Union types represent values that can be one of several different types. TypeScript supports discriminated unions natively; Java does not.

### Content (TextContent | ImageContent)

**Purpose:** Represents content in a tool response, which can be either text or image data.

**TypeScript Definition:**
```typescript
type Content = TextContent | ImageContent;
```

**Java Implementation:** Single record with discriminator field

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `type` | `String` | Discriminator indicating content type ("text" or "image") | Required, non-blank, must be "text" or "image" | `@NotBlank @Pattern(regexp = "text\\|image")` |
| `text` | `String` | Text content (only for type="text") | Required when type="text", null otherwise | `@NotNull(groups = TextContent.class)` |
| `data` | `String` | Base64-encoded image data (only for type="image") | Required when type="image", null otherwise | `@NotNull(groups = ImageContent.class)` |
| `mimeType` | `String` | MIME type of image (only for type="image") | Required when type="image", null otherwise | `@NotNull(groups = ImageContent.class) @Pattern(regexp = "image/.*")` |

**Validation Rules:**
- If `type` is "text", then `text` must be non-null
- If `type` is "image", then both `data` and `mimeType` must be non-null
- Exactly one variant's fields should be populated

**Current Implementation:**
```java
public Content {
    if (type == null || type.isBlank()) {
        throw new IllegalArgumentException("Content type cannot be null or blank");
    }
    if ("text".equals(type) && text == null) {
        throw new IllegalArgumentException("Text content must have text field");
    }
    if ("image".equals(type) && (data == null || mimeType == null)) {
        throw new IllegalArgumentException("Image content must have data and mimeType fields");
    }
}
```

---

## Optional Types

Optional types represent fields that may or may not be present. In TypeScript, these are denoted with `?`. In Java, we use nullable reference types.

### Could we use Java `Optional<T>`?

**Short Answer:** We could, but it's not recommended for this use case.

**Why Not:**
1. **Jackson Default Behavior:** Jackson treats `Optional<T>` as a wrapper and serializes it as an object with `{"present": true, "value": ...}` by default
2. **Module Required:** Need `jackson-datatype-jdk8` to handle `Optional` properly
3. **Anti-Pattern:** Java language architects discourage `Optional` as field types (designed for return types)
4. **Verbosity:** More verbose than nullable fields: `Optional.ofNullable(value).orElse(null)`
5. **Records:** Records with `Optional` fields break compact constructor semantics

**Jackson with Optional (with jackson-datatype-jdk8):**
```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new Jdk8Module());
// Now Optional fields serialize as: "field": value (or omitted if empty)
```

**Recommendation:** Continue using nullable fields with `@JsonInclude(NON_NULL)` - it's simpler, more idiomatic, and works naturally with Jackson.

### Optional Fields by Type

#### Tool
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `name` | `String` | No | - | Tool identifier |
| `description` | `String` | **Yes** | null | Human-readable description |
| `inputSchema` | `JsonNode` | No | - | JSON Schema for parameters |

**JSR 380 Annotations:**
```java
public record Tool(
    @NotBlank String name,
    String description,  // nullable
    @NotNull JsonNode inputSchema
) {}
```

#### CallToolRequest
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `name` | `String` | No | - | Name of tool to invoke |
| `arguments` | `JsonNode` | **Yes** | null | Arguments to pass to tool |

**JSR 380 Annotations:**
```java
public record CallToolRequest(
    @NotBlank String name,
    JsonNode arguments  // nullable
) {}
```

#### CallToolResult
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `content` | `List<Content>` | No | empty list | Content items returned |
| `isError` | `Boolean` | **Yes** | null | Whether execution failed |

**JSR 380 Annotations:**
```java
public record CallToolResult(
    @NotNull @Valid List<Content> content,
    Boolean isError  // nullable
) {}
```

#### ListToolsResult
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `tools` | `List<Tool>` | No | empty list | Available tools |

**JSR 380 Annotations:**
```java
public record ListToolsResult(
    @NotNull @Valid List<Tool> tools
) {}
```

#### InitializeRequest
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `protocolVersion` | `String` | No | - | MCP protocol version |
| `capabilities` | `ClientCapabilities` | No | default | Client capabilities |
| `clientInfo` | `Implementation` | No | - | Client implementation info |

**Note:** `capabilities` has a default value (empty capabilities object) in compact constructor.

**JSR 380 Annotations:**
```java
public record InitializeRequest(
    @NotBlank String protocolVersion,
    @NotNull @Valid ClientCapabilities capabilities,
    @NotNull @Valid Implementation clientInfo
) {}
```

#### InitializeResult
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `protocolVersion` | `String` | No | - | MCP protocol version |
| `capabilities` | `ServerCapabilities` | No | default | Server capabilities |
| `serverInfo` | `Implementation` | No | - | Server implementation info |

**Note:** `capabilities` has a default value in compact constructor.

**JSR 380 Annotations:**
```java
public record InitializeResult(
    @NotBlank String protocolVersion,
    @NotNull @Valid ServerCapabilities capabilities,
    @NotNull @Valid Implementation serverInfo
) {}
```

#### Implementation
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `name` | `String` | No | - | Implementation name |
| `version` | `String` | No | - | Implementation version |

**JSR 380 Annotations:**
```java
public record Implementation(
    @NotBlank String name,
    @NotBlank String version
) {}
```

#### ClientCapabilities
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `experimental` | `Object` | **Yes** | null | Experimental capabilities |
| `sampling` | `Object` | **Yes** | null | Sampling capability marker |

**JSR 380 Annotations:**
```java
public record ClientCapabilities(
    Object experimental,  // nullable
    Object sampling       // nullable
) {}
```

#### ServerCapabilities
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `experimental` | `Object` | **Yes** | null | Experimental capabilities |
| `logging` | `Object` | **Yes** | null | Logging capability marker |
| `prompts` | `PromptsCapability` | **Yes** | null | Prompts capabilities |
| `resources` | `ResourcesCapability` | **Yes** | null | Resources capabilities |
| `tools` | `ToolsCapability` | **Yes** | null | Tools capabilities |

**JSR 380 Annotations:**
```java
public record ServerCapabilities(
    Object experimental,                    // nullable
    Object logging,                         // nullable
    @Valid PromptsCapability prompts,      // nullable
    @Valid ResourcesCapability resources,   // nullable
    @Valid ToolsCapability tools           // nullable
) {}
```

#### ServerCapabilities.PromptsCapability
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `listChanged` | `Boolean` | **Yes** | null | Supports prompts/list notifications |

**JSR 380 Annotations:**
```java
public record PromptsCapability(
    Boolean listChanged  // nullable
) {}
```

#### ServerCapabilities.ResourcesCapability
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `subscribe` | `Boolean` | **Yes** | null | Supports resource subscriptions |
| `listChanged` | `Boolean` | **Yes** | null | Supports resources/list notifications |

**JSR 380 Annotations:**
```java
public record ResourcesCapability(
    Boolean subscribe,    // nullable
    Boolean listChanged   // nullable
) {}
```

#### ServerCapabilities.ToolsCapability
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `listChanged` | `Boolean` | **Yes** | null | Supports tools/list notifications |

**JSR 380 Annotations:**
```java
public record ToolsCapability(
    Boolean listChanged  // nullable
) {}
```

#### JsonRpcRequest
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `jsonrpc` | `String` | No | "2.0" | JSON-RPC version (TypeScript literal) |
| `id` | `Object` | **Yes** | null | Request identifier |
| `method` | `String` | No | - | Method name to invoke |
| `params` | `Object` | **Yes** | null | Method parameters |

**JSR 380 Annotations:**
```java
public record JsonRpcRequest(
    @NotNull @Pattern(regexp = "2\\.0") String jsonrpc,
    Object id,      // nullable
    @NotBlank String method,
    Object params   // nullable
) {}
```

#### JsonRpcResponse
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `jsonrpc` | `String` | No | "2.0" | JSON-RPC version (TypeScript literal) |
| `id` | `Object` | **Yes** | null | Request identifier being responded to |
| `result` | `Object` | **Yes** | null | Success result (mutually exclusive with error) |
| `error` | `JsonRpcError` | **Yes** | null | Error result (mutually exclusive with result) |

**JSR 380 Annotations:**
```java
public record JsonRpcResponse(
    @NotNull @Pattern(regexp = "2\\.0") String jsonrpc,
    Object id,              // nullable
    Object result,          // nullable (but exactly one of result/error must be present)
    @Valid JsonRpcError error  // nullable (but exactly one of result/error must be present)
) {
    // Custom validation: exactly one of result or error must be non-null
    @AssertTrue(message = "Exactly one of result or error must be present")
    public boolean isValid() {
        return (result != null) ^ (error != null);
    }
}
```

#### JsonRpcError
| Field | Type | Optional? | Default | Description |
|-------|------|-----------|---------|-------------|
| `code` | `int` | No | - | Error code |
| `message` | `String` | No | - | Error message |
| `data` | `Object` | **Yes** | null | Additional error data |

**JSR 380 Annotations:**
```java
public record JsonRpcError(
    int code,
    @NotBlank String message,
    Object data  // nullable
) {}
```

### Summary: Optional Fields

**Total Optional Fields:** 20 out of 44 fields (45%)

**Worthwhile to use `Optional<T>`?** **No**, for the following reasons:
1. Current approach is simpler and more idiomatic
2. Jackson support requires additional module
3. `Optional` in fields is an anti-pattern per Java language design
4. No benefit over nullable fields with `@JsonInclude(NON_NULL)`
5. Would make compact constructors more complex

---

## Literal Types

Literal types restrict a value to specific exact values. TypeScript supports this natively; Java requires runtime validation.

### JsonRpcRequest

**Purpose:** JSON-RPC 2.0 request message

| Field | Type | Literal? | Allowed Values | Description |
|-------|------|----------|----------------|-------------|
| `jsonrpc` | `String` | **Yes** | `"2.0"` | JSON-RPC protocol version (TypeScript literal: `"2.0"`) |
| `id` | `Object` | No | any | Request identifier |
| `method` | `String` | No | any | Method name |
| `params` | `Object` | No | any | Method parameters |

**JSR 380 Implementation:**
```java
public record JsonRpcRequest(
    @NotNull @Pattern(regexp = "2\\.0", message = "jsonrpc must be exactly '2.0'") 
    String jsonrpc,  // TypeScript literal: "2.0"
    Object id,
    @NotBlank String method,
    Object params
) {
    public JsonRpcRequest {
        if (jsonrpc == null) {
            jsonrpc = "2.0";  // Default to literal value
        }
    }
}
```

### JsonRpcResponse

**Purpose:** JSON-RPC 2.0 response message

| Field | Type | Literal? | Allowed Values | Description |
|-------|------|----------|----------------|-------------|
| `jsonrpc` | `String` | **Yes** | `"2.0"` | JSON-RPC protocol version (TypeScript literal: `"2.0"`) |
| `id` | `Object` | No | any | Request identifier |
| `result` | `Object` | No | any | Success result |
| `error` | `JsonRpcError` | No | any | Error result |

**JSR 380 Implementation:**
```java
public record JsonRpcResponse(
    @NotNull @Pattern(regexp = "2\\.0", message = "jsonrpc must be exactly '2.0'") 
    String jsonrpc,  // TypeScript literal: "2.0"
    Object id,
    Object result,
    @Valid JsonRpcError error
) {
    public JsonRpcResponse {
        if (jsonrpc == null) {
            jsonrpc = "2.0";  // Default to literal value
        }
    }
}
```

### Content (Discriminator)

**Purpose:** Content type in tool responses

| Field | Type | Literal? | Allowed Values | Description |
|-------|------|----------|----------------|-------------|
| `type` | `String` | **Yes** | `"text"` or `"image"` | Content type discriminator (TypeScript literals: `"text"` \| `"image"`) |
| `text` | `String` | No | any | Text content |
| `data` | `String` | No | any | Image data |
| `mimeType` | `String` | No | any | Image MIME type |

**JSR 380 Implementation:**
```java
public record Content(
    @NotBlank @Pattern(regexp = "text|image", message = "type must be 'text' or 'image'") 
    String type,  // TypeScript literals: "text" | "image"
    String text,
    String data,
    String mimeType
) {}
```

### Summary: Literal Types

**Total Literal Types:** 3 fields across 3 types
- `JsonRpcRequest.jsonrpc`: `"2.0"` (TypeScript literal)
- `JsonRpcResponse.jsonrpc`: `"2.0"` (TypeScript literal)
- `Content.type`: `"text" | "image"` (TypeScript literal union)

**Implementation Strategy:**
- Use `String` type in Java
- Provide default value in compact constructor
- Add `@Pattern` annotation for validation
- Document in comments that field is a TypeScript literal

---

## Index Signatures

Index signatures allow objects with arbitrary string keys. TypeScript syntax: `{ [key: string]: T }`. Java requires choosing between `JsonNode` (preserves structure) or `Object` (presence marker).

### ServerCapabilities.experimental

**Purpose:** Experimental/extension capabilities that are not part of the standard

**TypeScript Definition:**
```typescript
experimental?: { [key: string]: unknown };
```

**Solution Chosen:** `Object`

**Why:** 
- Acts as a presence marker
- Actual structure is opaque to server
- No need to inspect or manipulate contents
- Simpler than `JsonNode`

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `experimental` | `Object` | Arbitrary experimental capabilities | None - any structure allowed | (none) |

### ClientCapabilities.experimental

**Purpose:** Experimental/extension capabilities that client supports

**TypeScript Definition:**
```typescript
experimental?: { [key: string]: unknown };
```

**Solution Chosen:** `Object`

**Why:** 
- Acts as a presence marker
- Actual structure is opaque to server
- No need to inspect contents
- Client defines structure

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `experimental` | `Object` | Arbitrary experimental capabilities | None - any structure allowed | (none) |

### ServerCapabilities.logging

**Purpose:** Empty object marker indicating logging support

**TypeScript Definition:**
```typescript
logging?: {};
```

**Solution Chosen:** `Object`

**Why:** 
- Empty object marker (presence indicates capability)
- No structure to preserve
- Simplest representation

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `logging` | `Object` | Logging capability marker (empty object) | None - presence is what matters | (none) |

### ClientCapabilities.sampling

**Purpose:** Empty object marker indicating sampling support

**TypeScript Definition:**
```typescript
sampling?: {};
```

**Solution Chosen:** `Object`

**Why:** 
- Empty object marker (presence indicates capability)
- No structure to preserve
- Simplest representation

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `sampling` | `Object` | Sampling capability marker (empty object) | None - presence is what matters | (none) |

### Tool.inputSchema

**Purpose:** JSON Schema defining tool's input parameters

**TypeScript Definition:**
```typescript
inputSchema: {
  type: "object";
  properties?: { [key: string]: unknown };
  required?: string[];
};
```

**Solution Chosen:** `JsonNode`

**Why:**
- Structure must be preserved for validation
- Needs to be inspected by clients
- Must support arbitrary JSON Schema constructs
- More complex than presence marker

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `inputSchema` | `JsonNode` | JSON Schema for tool parameters | Must be valid JSON Schema object | `@NotNull` + custom validator |

### CallToolRequest.arguments

**Purpose:** Arguments to pass to tool

**TypeScript Definition:**
```typescript
arguments?: { [key: string]: unknown };
```

**Solution Chosen:** `JsonNode`

**Why:**
- Structure must be preserved
- Needs to be inspected and passed to tool
- Tool needs to extract specific fields
- Can be complex nested structures

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `arguments` | `JsonNode` | Tool invocation arguments | Must match tool's inputSchema | (validated externally) |

### Summary: Index Signatures

| Type | Field | Solution | Reason |
|------|-------|----------|--------|
| `ServerCapabilities` | `experimental` | `Object` | Presence marker, no inspection needed |
| `ClientCapabilities` | `experimental` | `Object` | Presence marker, no inspection needed |
| `ServerCapabilities` | `logging` | `Object` | Empty object marker |
| `ClientCapabilities` | `sampling` | `Object` | Empty object marker |
| `Tool` | `inputSchema` | `JsonNode` | Structure preservation required |
| `CallToolRequest` | `arguments` | `JsonNode` | Structure preservation required |

**Decision Criteria:**
- **Use `Object`**: When field is a presence marker or structure is truly opaque
- **Use `JsonNode`**: When structure needs to be preserved, inspected, or manipulated

---

## Unknown Types

Unknown types represent values of any type. TypeScript's `unknown` is type-safe `any`. Java uses `Object` or `JsonNode` depending on use case.

### JsonRpcRequest.id

**Purpose:** Unique identifier for correlating request and response

**TypeScript Definition:**
```typescript
id?: string | number;
```

**Solution Chosen:** `Object`

**Why:**
- Can be string or number
- No need to inspect or manipulate
- Just needs to be echoed back in response
- Opaque to protocol layer

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `id` | `Object` | Request identifier (string or number) | None - any type allowed | (none) |

### JsonRpcResponse.id

**Purpose:** Request identifier being responded to

**TypeScript Definition:**
```typescript
id?: string | number;
```

**Solution Chosen:** `Object`

**Why:**
- Can be string or number
- Must match request id exactly
- Opaque to protocol layer

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `id` | `Object` | Request identifier (string or number) | Should match corresponding request | (none) |

### JsonRpcRequest.params

**Purpose:** Parameters for the RPC method

**TypeScript Definition:**
```typescript
params?: unknown;
```

**Solution Chosen:** `Object`

**Why:**
- Structure depends on method
- Handled by method-specific code
- Protocol layer doesn't inspect

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `params` | `Object` | Method-specific parameters | Validated by method handler | (none) |

### JsonRpcResponse.result

**Purpose:** Success result of RPC call

**TypeScript Definition:**
```typescript
result?: unknown;
```

**Solution Chosen:** `Object`

**Why:**
- Structure depends on method
- Protocol layer doesn't inspect
- Client interprets based on method

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `result` | `Object` | Method-specific result | Mutually exclusive with error | (none) |

### JsonRpcError.data

**Purpose:** Additional error information

**TypeScript Definition:**
```typescript
data?: unknown;
```

**Solution Chosen:** `Object`

**Why:**
- Structure is application-specific
- Optional error details
- No standard structure

| Field | Type | Description | Validation | JSR 380 |
|-------|------|-------------|------------|---------|
| `data` | `Object` | Additional error details | None - any structure allowed | (none) |

### Summary: Unknown Types

| Type | Field | Solution | Reason |
|------|-------|----------|--------|
| `JsonRpcRequest` | `id` | `Object` | Opaque identifier (string \| number) |
| `JsonRpcResponse` | `id` | `Object` | Opaque identifier (string \| number) |
| `JsonRpcRequest` | `params` | `Object` | Method-specific structure |
| `JsonRpcResponse` | `result` | `Object` | Method-specific structure |
| `JsonRpcError` | `data` | `Object` | Application-specific error details |

**All unknown types use `Object`** because:
1. Protocol layer doesn't need to inspect structure
2. Simpler than `JsonNode`
3. Values are opaque at this layer
4. Specific handlers deal with actual structure

**No `JsonNode` usage for unknown types** because:
- These fields are truly opaque at the protocol layer
- No need for generic structure inspection
- Type-specific handlers deserialize to proper types

---

## Validation Summary

### Complete JSR 380 Annotated Records

#### Tool
```java
public record Tool(
    @NotBlank(message = "Tool name is required")
    String name,
    
    String description,  // Optional
    
    @NotNull(message = "Input schema is required")
    JsonNode inputSchema
) {}
```

#### CallToolRequest
```java
public record CallToolRequest(
    @NotBlank(message = "Tool name is required")
    String name,
    
    JsonNode arguments  // Optional
) {}
```

#### CallToolResult
```java
public record CallToolResult(
    @NotNull(message = "Content array is required")
    @Valid
    List<Content> content,
    
    Boolean isError  // Optional
) {}
```

#### Content
```java
public record Content(
    @NotBlank(message = "Content type is required")
    @Pattern(regexp = "text|image", message = "Type must be 'text' or 'image'")
    String type,  // TypeScript literal: "text" | "image"
    
    String text,
    String data,
    String mimeType
) {
    @AssertTrue(message = "Text content must have text field")
    public boolean isValidTextContent() {
        return !"text".equals(type) || text != null;
    }
    
    @AssertTrue(message = "Image content must have data and mimeType")
    public boolean isValidImageContent() {
        return !"image".equals(type) || (data != null && mimeType != null);
    }
}
```

#### Implementation
```java
public record Implementation(
    @NotBlank(message = "Implementation name is required")
    String name,
    
    @NotBlank(message = "Implementation version is required")
    String version
) {}
```

#### InitializeRequest
```java
public record InitializeRequest(
    @NotBlank(message = "Protocol version is required")
    String protocolVersion,
    
    @NotNull(message = "Capabilities are required")
    @Valid
    ClientCapabilities capabilities,
    
    @NotNull(message = "Client info is required")
    @Valid
    Implementation clientInfo
) {}
```

#### InitializeResult
```java
public record InitializeResult(
    @NotBlank(message = "Protocol version is required")
    String protocolVersion,
    
    @NotNull(message = "Capabilities are required")
    @Valid
    ServerCapabilities capabilities,
    
    @NotNull(message = "Server info is required")
    @Valid
    Implementation serverInfo
) {}
```

#### JsonRpcRequest
```java
public record JsonRpcRequest(
    @NotNull(message = "JSON-RPC version is required")
    @Pattern(regexp = "2\\.0", message = "JSON-RPC version must be '2.0'")
    String jsonrpc,  // TypeScript literal: "2.0"
    
    Object id,  // Optional
    
    @NotBlank(message = "Method name is required")
    String method,
    
    Object params  // Optional
) {}
```

#### JsonRpcResponse
```java
public record JsonRpcResponse(
    @NotNull(message = "JSON-RPC version is required")
    @Pattern(regexp = "2\\.0", message = "JSON-RPC version must be '2.0'")
    String jsonrpc,  // TypeScript literal: "2.0"
    
    Object id,  // Optional
    
    Object result,  // Optional (but mutually exclusive with error)
    
    @Valid
    JsonRpcError error  // Optional (but mutually exclusive with result)
) {
    @AssertTrue(message = "Exactly one of result or error must be present")
    public boolean isValid() {
        return (result != null) ^ (error != null);
    }
}
```

#### JsonRpcError
```java
public record JsonRpcError(
    int code,
    
    @NotBlank(message = "Error message is required")
    String message,
    
    Object data  // Optional
) {}
```

---

## Quick Reference Tables

### Type Categories

| Category | Count | Examples |
|----------|-------|----------|
| Union Types | 1 | `Content` |
| Optional Fields | 20 | `Tool.description`, `CallToolRequest.arguments`, etc. |
| Literal Types | 3 | `JsonRpcRequest.jsonrpc`, `Content.type` |
| Index Signatures | 6 | `Tool.inputSchema`, `ServerCapabilities.experimental` |
| Unknown Types | 5 | `JsonRpcRequest.id`, `JsonRpcResponse.result` |

### Solution Choices

| Pattern | Java Solution | Count | Rationale |
|---------|---------------|-------|-----------|
| Union Types | Single record + discriminator | 1 | Simple, Jackson-friendly |
| Optional Fields | Nullable + `@JsonInclude(NON_NULL)` | 20 | Idiomatic, works naturally |
| Literal Types | `String` + validation | 3 | Runtime check, defaults |
| Index Signatures (opaque) | `Object` | 4 | Presence marker |
| Index Signatures (structural) | `JsonNode` | 2 | Structure preservation |
| Unknown Types | `Object` | 5 | Opaque at protocol layer |

---

*"There are only two hard things in Computer Science: cache invalidation and naming things... and off-by-one errors."* — Phil Karlton (attributed, with the classic addition)


# MCP Protocol Type Mapping: TypeScript to Java

This document describes how the Model Context Protocol (MCP) TypeScript definitions are mapped to Java records.

## Reference
- **MCP Specification**: https://spec.modelcontextprotocol.io/
- **MCP TypeScript SDK**: https://github.com/modelcontextprotocol/typescript-sdk

## Core JSON-RPC Types

### JsonRpcRequest
**TypeScript:**
```typescript
interface JSONRPCRequest {
  jsonrpc: "2.0";
  id?: string | number;
  method: string;
  params?: unknown;
}
```

**Java:**
```java
public record JsonRpcRequest(
  String jsonrpc,
  Object id,
  String method,
  Object params
)
```

**Notes:**
- TypeScript's `string | number` for `id` maps to Java's `Object`
- TypeScript's `unknown` for `params` maps to Java's `Object`
- Jackson handles serialization/deserialization with proper type preservation

### JsonRpcResponse
**TypeScript:**
```typescript
interface JSONRPCResponse {
  jsonrpc: "2.0";
  id?: string | number;
  result?: unknown;
  error?: JSONRPCError;
}
```

**Java:**
```java
public record JsonRpcResponse(
  String jsonrpc,
  Object id,
  Object result,
  JsonRpcError error
)
```

**Notes:**
- Mutual exclusivity of `result` and `error` is enforced by factory methods, not type system

### JsonRpcError
**TypeScript:**
```typescript
interface JSONRPCError {
  code: number;
  message: string;
  data?: unknown;
}
```

**Java:**
```java
public record JsonRpcError(
  int code,
  String message,
  Object data
)
```

**Notes:**
- Perfect 1:1 mapping

## MCP Protocol Types

### Tool
**TypeScript:**
```typescript
interface Tool {
  name: string;
  description?: string;
  inputSchema: {
    type: "object";
    properties?: { [key: string]: unknown };
    required?: string[];
  };
}
```

**Java:**
```java
public record Tool(
  String name,
  String description,
  JsonNode inputSchema
)
```

**Notes:**
- TypeScript's inline object schema maps to `JsonNode` for flexibility
- Jackson preserves the structure when serializing/deserializing
- Validation of schema structure happens at runtime, not compile-time

### ListToolsResult
**TypeScript:**
```typescript
interface ListToolsResult {
  tools: Tool[];
}
```

**Java:**
```java
public record ListToolsResult(
  List<Tool> tools
)
```

**Notes:**
- TypeScript arrays map to Java `List<T>`
- Immutable lists are used in compact constructor

### CallToolRequest
**TypeScript:**
```typescript
interface CallToolRequest {
  method: "tools/call";
  params: {
    name: string;
    arguments?: { [key: string]: unknown };
  };
}
```

**Java:**
```java
public record CallToolRequest(
  String name,
  JsonNode arguments
)
```

**Notes:**
- Only the `params` content is represented (method is handled by handler routing)
- TypeScript's `{ [key: string]: unknown }` maps to `JsonNode`

### Content (Union Type)
**TypeScript:**
```typescript
type TextContent = {
  type: "text";
  text: string;
};

type ImageContent = {
  type: "image";
  data: string;
  mimeType: string;
};

type Content = TextContent | ImageContent;
```

**Java:**
```java
public record Content(
  String type,
  String text,
  String data,
  String mimeType
)
```

**Notes:**
- **REPRESENTATIONAL MISMATCH**: TypeScript uses discriminated unions, Java uses a single record with nullable fields
- The `type` field acts as a discriminator
- Compact constructor validates that correct fields are present based on type
- This is a common pattern in Java for representing union types
- Alternative approaches could use sealed classes (Java 17+), but records are simpler

### CallToolResult
**TypeScript:**
```typescript
interface CallToolResult {
  content: Content[];
  isError?: boolean;
}
```

**Java:**
```java
public record CallToolResult(
  List<Content> content,
  Boolean isError
)
```

**Notes:**
- Using `Boolean` (object) instead of `boolean` (primitive) to allow null (missing field)

### ServerCapabilities (Nested Records)
**TypeScript:**
```typescript
interface ServerCapabilities {
  experimental?: { [key: string]: unknown };
  logging?: {};
  prompts?: {
    listChanged?: boolean;
  };
  resources?: {
    subscribe?: boolean;
    listChanged?: boolean;
  };
  tools?: {
    listChanged?: boolean;
  };
}
```

**Java:**
```java
public record ServerCapabilities(
  Object experimental,
  Object logging,
  PromptsCapability prompts,
  ResourcesCapability resources,
  ToolsCapability tools
) {
  public record PromptsCapability(Boolean listChanged) {}
  public record ResourcesCapability(Boolean subscribe, Boolean listChanged) {}
  public record ToolsCapability(Boolean listChanged) {}
}
```

**Notes:**
- Nested TypeScript interfaces map to nested record declarations
- TypeScript's `{}` (empty object marker) maps to `Object` in Java
- The presence of nested capability objects indicates support, not their content

## Key Differences & Design Decisions

### 1. Optional Fields
- **TypeScript**: Uses `?` for optional fields
- **Java**: Uses nullable reference types (fields can be null)
- **Jackson**: Configured with `@JsonInclude(Include.NON_NULL)` to omit null fields from JSON

### 2. Union Types
- **TypeScript**: Native discriminated unions
- **Java**: Single record with all possible fields, discriminator field, runtime validation
- **Rationale**: Simpler than sealed class hierarchies, works well with Jackson

### 3. Generic Object Types
- **TypeScript**: `unknown`, `{ [key: string]: unknown }`
- **Java**: `Object` or `JsonNode`
- **Rationale**: `JsonNode` is used when structure needs to be preserved; `Object` when it's truly opaque

### 4. Arrays
- **TypeScript**: `T[]`
- **Java**: `List<T>`
- **Rationale**: Lists are more idiomatic in Java and work better with collections framework

### 5. Literal Types
- **TypeScript**: `"2.0"`, `"text"`, `"image"`
- **Java**: `String` with runtime validation
- **Rationale**: Java doesn't have literal types; validation happens in compact constructors

### 6. Empty Object Markers
- **TypeScript**: `{}` indicates presence without structure
- **Java**: `Object` type
- **Rationale**: The non-null presence indicates capability, not the content

## Validation Strategy

### TypeScript
- Compile-time type checking
- Runtime validation via libraries like Zod

### Java
- Compile-time type checking for structure
- Runtime validation in record compact constructors
- Jackson handles JSON schema validation during deserialization

## Example: Full Request/Response Cycle

**TypeScript Request:**
```typescript
{
  jsonrpc: "2.0",
  id: 1,
  method: "tools/call",
  params: {
    name: "query",
    arguments: {
      sql: "SELECT * FROM users"
    }
  }
}
```

**Java Processing:**
```java
JsonRpcRequest request = // deserialized
CallToolRequest callRequest = mapper.convertValue(request.params(), CallToolRequest.class);
String toolName = callRequest.name();
JsonNode arguments = callRequest.arguments();
```

**TypeScript Response:**
```typescript
{
  jsonrpc: "2.0",
  id: 1,
  result: {
    content: [{
      type: "text",
      text: "{...json data...}"
    }],
    isError: false
  }
}
```

**Java Construction:**
```java
CallToolResult result = CallToolResult.text(jsonData);
JsonRpcResponse response = JsonRpcResponse.success(request.id(), result);
```

## Conclusion

The Java implementation maintains semantic equivalence with the TypeScript MCP protocol while adapting to Java's type system and idioms. The main representational differences are:

1. **Union types → Single record with discriminator**: Acceptable tradeoff for simplicity
2. **Optional fields → Nullable fields**: Standard Java pattern with Jackson
3. **Generic types → Object/JsonNode**: Preserves flexibility while maintaining type safety where possible

These mappings provide type-safe, idiomatic Java code that correctly implements the MCP protocol specification.


# Representational Mismatches: TypeScript vs Java

This document highlights areas where the Java implementation differs from TypeScript due to fundamental differences in the type systems.

## 1. Discriminated Union Types

### The Issue
TypeScript has first-class support for discriminated (tagged) unions:

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

// TypeScript compiler knows:
function process(content: Content) {
  if (content.type === "text") {
    console.log(content.text); // OK - compiler knows this exists
  } else {
    console.log(content.mimeType); // OK - compiler knows content is ImageContent
  }
}
```

Java doesn't have this feature.

### Our Solution
Use a single record with all possible fields:

```java
public record Content(
    String type,      // discriminator
    String text,      // only for type="text"
    String data,      // only for type="image"
    String mimeType   // only for type="image"
) {
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
    
    public static Content text(String text) {
        return new Content("text", text, null, null);
    }
    
    public static Content image(String data, String mimeType) {
        return new Content("image", null, data, mimeType);
    }
}
```

### Alternatives Considered

#### A. Sealed Classes (Java 17+)
```java
public sealed interface Content permits TextContent, ImageContent {}

public record TextContent(String text) implements Content {}

public record ImageContent(String data, String mimeType) implements Content {}
```

**Pros:**
- Type-safe pattern matching
- Compiler enforces exhaustiveness
- Closer to TypeScript semantics

**Cons:**
- More verbose
- Requires pattern matching or instanceof checks
- Jackson serialization needs custom handling for type discriminator
- Complicates JSON structure (needs `@JsonTypeInfo`)

#### B. Class Hierarchy with Abstract Base
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image")
})
public abstract class Content {
    public abstract String type();
}

public class TextContent extends Content {
    private final String text;
    // ...
}
```

**Pros:**
- OOP pattern
- Jackson has built-in support

**Cons:**
- Not records (no automatic equals/hashCode)
- More boilerplate
- Less idiomatic in modern Java

### Why We Chose Single Record
1. **Simplicity**: Single type, easy to work with
2. **Jackson**: Works naturally without annotations
3. **MCP Protocol**: Simple wire format matches exactly
4. **Common Pattern**: Used successfully in many APIs (e.g., Slack, Discord)
5. **Validation**: Runtime checks catch errors immediately

### Impact Assessment
**Severity:** LOW

- JSON wire format is identical
- Validation catches type errors at construction
- Factory methods (`Content.text()`, `Content.image()`) guide usage
- Works correctly with Jackson serialization
- Common pattern in Java APIs

## 2. Optional Fields

### The Issue
TypeScript uses `?` for optional fields:

```typescript
interface Tool {
  name: string;
  description?: string;  // optional
  inputSchema: object;
}
```

Java doesn't have optional field syntax.

### Our Solution
Use nullable reference types:

```java
public record Tool(
    String name,
    String description,  // can be null
    JsonNode inputSchema
) {}
```

Combined with Jackson's `@JsonInclude(Include.NON_NULL)`:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Tool(...) {}
```

### Why This Works
- Null represents "not present" (same semantics as TypeScript undefined)
- Jackson omits null fields from JSON (matches TypeScript optional)
- Java's type system doesn't distinguish null from absent (acceptable for this use case)

### Impact Assessment
**Severity:** NONE

- Semantically equivalent
- Standard Java pattern
- JSON output identical to TypeScript

## 3. Literal Types

### The Issue
TypeScript can specify exact string values:

```typescript
interface JSONRPCRequest {
  jsonrpc: "2.0";  // literal type - must be exactly "2.0"
  method: string;
}
```

Java doesn't have literal types.

### Our Solution
Use String with runtime validation:

```java
public record JsonRpcRequest(
    String jsonrpc,
    Object id,
    String method,
    Object params
) {
    public JsonRpcRequest {
        if (jsonrpc == null) {
            jsonrpc = "2.0";  // default
        }
    }
}
```

### Why This Works
- Compact constructor provides default
- Could add validation if needed: `if (!jsonrpc.equals("2.0")) throw ...`
- Callers can't accidentally set wrong value if using factory methods

### Impact Assessment
**Severity:** NONE

- Runtime validation available if needed
- Default value provided
- Standard Java pattern

## 4. Index Signatures (Arbitrary Keys)

### The Issue
TypeScript allows objects with arbitrary keys:

```typescript
interface ServerCapabilities {
  experimental?: { [key: string]: unknown };
  tools?: {
    listChanged?: boolean;
  };
}
```

Java doesn't have index signature syntax.

### Our Solution A: JsonNode (for truly arbitrary structure)
```java
public record ServerCapabilities(
    JsonNode experimental,  // preserves any structure
    ToolsCapability tools
) {}
```

### Our Solution B: Object (for presence marker)
```java
public record ServerCapabilities(
    Object experimental,  // just indicates presence
    ToolsCapability tools
) {}
```

### Why This Works
- `JsonNode`: Use when structure needs to be preserved/inspected
- `Object`: Use when presence is all that matters (empty object marker)
- Both serialize correctly with Jackson

### Impact Assessment
**Severity:** NONE

- Appropriate solution chosen per use case
- JSON structure preserved
- Can access arbitrary fields if needed

## 5. Arrays

### The Issue
TypeScript has array types:

```typescript
interface ListToolsResult {
  tools: Tool[];
}
```

Java has arrays but Lists are more idiomatic.

### Our Solution
Use `List<T>`:

```java
public record ListToolsResult(
    List<Tool> tools
) {
    public ListToolsResult {
        if (tools == null) {
            tools = List.of();  // immutable empty list
        }
    }
}
```

### Why This Works
- `List<T>` is standard Java collections
- Immutable lists (`List.of()`) provide record-like immutability
- Jackson handles List serialization naturally
- More flexible than arrays (can't be resized accidentally)

### Impact Assessment
**Severity:** NONE

- JSON output identical
- More idiomatic in Java
- Better API ergonomics

## 6. Unknown Type

### The Issue
TypeScript has `unknown` type (type-safe any):

```typescript
interface CallToolRequest {
  name: string;
  arguments?: { [key: string]: unknown };
}
```

Java doesn't have `unknown` (closest is `Object`).

### Our Solution A: Object (when truly opaque)
```java
public record InitializeRequest(
    String protocolVersion,
    ClientCapabilities capabilities,
    Implementation clientInfo
) {}
```

### Our Solution B: JsonNode (when structure matters)
```java
public record CallToolRequest(
    String name,
    JsonNode arguments
) {}
```

### Why This Works
- `Object`: For truly opaque data
- `JsonNode`: When we need to inspect/manipulate structure
- Both preserve data correctly through JSON serialization

### Impact Assessment
**Severity:** LOW

- Need to choose appropriate type per use case
- `JsonNode` more common in practice (need to access fields)
- Type safety reduced but manageable

## Summary Table

| Issue | TypeScript | Java | Severity | Impact |
|-------|-----------|------|----------|---------|
| Union Types | `A \| B` | Single record + discriminator | LOW | Works correctly, less type safety |
| Optional Fields | `field?:` | Nullable + `@JsonInclude` | NONE | Semantically equivalent |
| Literal Types | `"2.0"` | String + validation | NONE | Runtime check, standard pattern |
| Index Signatures | `[key: string]: T` | `JsonNode` or `Object` | NONE | Appropriate per use case |
| Arrays | `T[]` | `List<T>` | NONE | More idiomatic in Java |
| Unknown Type | `unknown` | `Object` or `JsonNode` | LOW | Need to choose wisely |

## Conclusion

The representational differences are **minimal and acceptable**:

1. **Union types** are the only significant difference, handled with standard discriminated record pattern
2. All other differences are **standard Java idioms** with identical JSON output
3. Type safety is maintained through **runtime validation** in record compact constructors
4. The implementation is **semantically equivalent** to the TypeScript definitions

The benefits of using records (immutability, simplicity, automatic methods) outweigh the minor loss of compile-time guarantees for discriminated unions.


# MCP Response Null Handling - Elide vs Explicit

**Date:** December 29, 2025

## TL;DR Answer

**✅ ELIDE null values** - Do NOT send `null` explicitly in MCP responses.

## Why Elide Null Values?

### 1. MCP Specification Uses Optional Fields

The MCP TypeScript definitions use the `?` optional operator, which means:

```typescript
interface ServerCapabilities {
  experimental?: { [key: string]: unknown };  // Optional - can be absent
  logging?: {};                               // Optional - can be absent
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

**In TypeScript:**
- `field?: Type` means the field can be **absent** (undefined)
- This is different from `field: Type | null` which would mean the field must be present but can be null

**Correct JSON serialization:**
```json
{
  "tools": {
    "listChanged": false
  }
}
```

**NOT:**
```json
{
  "experimental": null,
  "logging": null,
  "prompts": null,
  "resources": null,
  "tools": {
    "listChanged": false
  }
}
```

### 2. JSON-RPC 2.0 Specification

From the JSON-RPC 2.0 spec:

> A Response is represented by a single object with the following members:
> - `result` - REQUIRED on success, MUST NOT exist if there was an error
> - `error` - REQUIRED on error, MUST NOT exist if there was no error

**Key point:** The spec says fields "MUST NOT exist" - not that they should be null.

**Correct:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {}
}
```

**Incorrect:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {},
  "error": null    // ❌ Should not be present at all
}
```

### 3. Bandwidth and Clarity

**Eliding nulls:**
- ✅ Smaller message size (less bandwidth)
- ✅ Clearer intent (absence means not applicable)
- ✅ Easier to read and debug
- ✅ Matches TypeScript/JavaScript conventions

**Example - Server Capabilities:**

```json
// Elided (Good) - 45 bytes
{
  "tools": {
    "listChanged": false
  }
}

// Explicit nulls (Bad) - 118 bytes
{
  "experimental": null,
  "logging": null,
  "prompts": null,
  "resources": null,
  "tools": {
    "listChanged": false
  }
}
```

**Savings:** 62% smaller message!

### 4. Standard Practice in REST APIs

Industry standard practice (Google JSON Style Guide, JSON:API):

> "Empty values SHOULD be omitted from responses. If a property is optional and not present, it SHOULD NOT be included in the JSON representation."

## Our Implementation ✅

We're already doing this correctly!

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerCapabilities(
    @JsonProperty("experimental") Object experimental,
    @JsonProperty("logging") Object logging,
    @JsonProperty("prompts") PromptsCapability prompts,
    @JsonProperty("resources") ResourcesCapability resources,
    @JsonProperty("tools") ToolsCapability tools
) { }
```

**Effect:**
- When `experimental = null` → field is **omitted** from JSON
- When `experimental = {}` → field is **included** with empty object
- When `experimental = {"foo": "bar"}` → field is **included** with data

## TypeScript Optional vs Null

### Optional Field (`?`)
```typescript
interface Example {
  optionalField?: string;
}

// Valid instances:
const a: Example = {};                           // ✅ Field absent
const b: Example = { optionalField: "value" };   // ✅ Field present
const c: Example = { optionalField: undefined }; // ✅ Explicitly undefined
```

**JSON serialization:**
```json
{}                           // Field omitted (preferred)
{"optionalField": "value"}   // Field present
```

### Nullable Field
```typescript
interface Example {
  nullableField: string | null;
}

// Valid instances:
const a: Example = { nullableField: null };    // ✅ Must be present
const b: Example = { nullableField: "value" }; // ✅ Can have value
// const c: Example = {};                      // ❌ Field required!
```

**JSON serialization:**
```json
{"nullableField": null}      // Field explicitly null
{"nullableField": "value"}   // Field has value
```

## MCP Examples

### Initialize Response

**TypeScript:**
```typescript
interface InitializeResult {
  protocolVersion: string;
  capabilities: ServerCapabilities;
  serverInfo: Implementation;
}
```

**Good Response (elided nulls):**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {
        "listChanged": false
      }
    },
    "serverInfo": {
      "name": "jmcp",
      "version": "1.0.0"
    }
  }
}
```

**Bad Response (explicit nulls):**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "experimental": null,        // ❌ Unnecessary
      "logging": null,              // ❌ Unnecessary
      "prompts": null,              // ❌ Unnecessary
      "resources": null,            // ❌ Unnecessary
      "tools": {
        "listChanged": false
      }
    },
    "serverInfo": {
      "name": "jmcp",
      "version": "1.0.0"
    }
  },
  "error": null                    // ❌ Should not exist!
}
```

### Tools List Response

**TypeScript:**
```typescript
interface ListToolsResult {
  tools: Tool[];
  nextCursor?: string;  // Optional pagination cursor
}
```

**Good Response (no cursor):**
```json
{
  "tools": [
    {"name": "query", "description": "Execute SQL", "inputSchema": {...}}
  ]
}
```

**Bad Response (explicit null cursor):**
```json
{
  "tools": [
    {"name": "query", "description": "Execute SQL", "inputSchema": {...}}
  ],
  "nextCursor": null    // ❌ Should be omitted
}
```

## When to Include Null

There are **very rare** cases where you might want to explicitly include null:

### 1. Semantic Difference
If absence and null have different meanings:

```typescript
interface User {
  name: string;
  middleName?: string | null;  // undefined = unknown, null = no middle name
}
```

But this is **NOT** the case in MCP - absence always means "not applicable/not supported".

### 2. Partial Updates (PATCH)
In REST PATCH operations:
- Field absent = don't change
- Field null = clear/delete

But MCP doesn't have PATCH-like operations.

### 3. Required Nullable Fields
```typescript
interface Example {
  requiredButNullable: string | null;  // MUST be present
}
```

**MCP doesn't use this pattern** - optional fields are truly optional.

## Jackson Configuration

Our current configuration is correct:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
```

**Options:**
- `NON_NULL` - Omit fields with null values ✅ (what we use)
- `ALWAYS` - Include all fields even if null ❌
- `NON_ABSENT` - Omit Optional.empty() as well
- `NON_EMPTY` - Omit empty collections/strings

**We use NON_NULL because:**
- Matches MCP TypeScript optional semantics
- Follows JSON-RPC 2.0 spec
- Industry best practice
- Smaller messages
- Clearer intent

## Testing Our Implementation

Let's verify with an example:

```java
ServerCapabilities caps = new ServerCapabilities(
    null,  // experimental
    null,  // logging
    null,  // prompts
    null,  // resources
    new ServerCapabilities.ToolsCapability(false)  // tools
);

String json = objectMapper.writeValueAsString(caps);
```

**Output:**
```json
{
  "tools": {
    "listChanged": false
  }
}
```

**NOT:**
```json
{
  "experimental": null,
  "logging": null,
  "prompts": null,
  "resources": null,
  "tools": {
    "listChanged": false
  }
}
```

✅ Perfect! Nulls are elided.

## Summary Table

| Aspect | Elide Nulls | Explicit Nulls |
|--------|-------------|----------------|
| **MCP Spec Compliance** | ✅ Yes (optional fields) | ❌ No |
| **JSON-RPC 2.0 Compliance** | ✅ Yes | ❌ No (error/result) |
| **Message Size** | ✅ Smaller | ❌ Larger |
| **Readability** | ✅ Clearer | ❌ Cluttered |
| **TypeScript Semantics** | ✅ Matches `?` | ❌ Wrong |
| **Industry Standard** | ✅ Yes | ❌ No |
| **Our Implementation** | ✅ Correct | N/A |

## Recommendation

**Continue using `@JsonInclude(JsonInclude.Include.NON_NULL)` on all model classes.**

This is:
- ✅ Spec-compliant
- ✅ Best practice
- ✅ More efficient
- ✅ More readable
- ✅ What we're already doing

## References

1. **MCP Specification**
   - https://spec.modelcontextprotocol.io/
   - Uses TypeScript optional fields (`?`)

2. **JSON-RPC 2.0 Specification**
   - https://www.jsonrpc.org/specification
   - "MUST NOT exist" for unused fields

3. **Google JSON Style Guide**
   - https://google.github.io/styleguide/jsoncstyleguide.xml
   - Recommends omitting empty/null values

4. **JSON:API Specification**
   - https://jsonapi.org/
   - Omits null values by default

5. **Jackson Documentation**
   - `@JsonInclude(Include.NON_NULL)` is recommended for optional fields

---

*"Perfection is achieved, not when there is nothing more to add, but when there is nothing left to take away."* - Antoine de Saint-Exupéry

In JSON responses: Omit the nulls - there's nothing left to take away!


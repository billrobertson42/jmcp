# MCP Protocol Schema Data Dictionary

**Version:** 2024-11-05  
**Date:** November 23, 2025  
**Protocol:** Model Context Protocol (MCP)  
**Specification:** https://spec.modelcontextprotocol.io/

---

## Table of Contents

1. [JSON-RPC Base Types](#json-rpc-base-types)
   - [JsonRpcRequest](#jsonrpcrequest)
   - [JsonRpcResponse](#jsonrpcresponse)
   - [JsonRpcError](#jsonrpcerror)
2. [MCP Initialization Types](#mcp-initialization-types)
   - [InitializeRequest](#initializerequest)
   - [InitializeResult](#initializeresult)
   - [Implementation](#implementation)
   - [ClientCapabilities](#clientcapabilities)
   - [ServerCapabilities](#servercapabilities)
3. [MCP Tool Types](#mcp-tool-types)
   - [Tool](#tool)
   - [ListToolsResult](#listtoolsresult)
   - [CallToolRequest](#calltoolrequest)
   - [CallToolResult](#calltoolresult)
   - [Content](#content)

---

## JSON-RPC Base Types

### JsonRpcRequest

**Purpose:** Represents a JSON-RPC 2.0 request message sent from client to server.

**TypeScript Definition:**
```typescript
interface JSONRPCRequest {
  jsonrpc: "2.0";
  id?: string | number;
  method: string;
  params?: unknown;
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.JsonRpcRequest`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `jsonrpc` | String | Yes | JSON-RPC protocol version. Must always be "2.0" per JSON-RPC 2.0 specification. | `@NotNull`, `@Pattern(regexp = "2\\.0")` | "2.0" | TypeScript literal type. Automatically set to "2.0" if null. |
| `id` | Object | No | Unique identifier for this request, used to correlate with response. Can be string, number, or null. Client chooses format. | None | null | Opaque to protocol layer. Server must echo back in response. |
| `method` | String | Yes | Name of the method to invoke on the server. Format varies by protocol (e.g., "initialize", "tools/list", "tools/call"). | `@NotBlank` | - | Case-sensitive. Defines which handler processes request. |
| `params` | Object | No | Parameters for the method being invoked. Structure depends on the method. May be object, array, or primitive. | None | null | Method-specific structure. Parsed by method handler. |

**Validation Summary:**
- Must have valid JSON-RPC version ("2.0")
- Method name cannot be empty or whitespace-only
- No validation on params structure (method-specific)

**Example:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "query",
    "arguments": {
      "sql": "SELECT * FROM users"
    }
  }
}
```

---

### JsonRpcResponse

**Purpose:** Represents a JSON-RPC 2.0 response message sent from server to client.

**TypeScript Definition:**
```typescript
interface JSONRPCResponse {
  jsonrpc: "2.0";
  id?: string | number;
  result?: unknown;
  error?: JSONRPCError;
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.JsonRpcResponse`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `jsonrpc` | String | Yes | JSON-RPC protocol version. Must always be "2.0". | `@NotNull`, `@Pattern(regexp = "2\\.0")` | "2.0" | TypeScript literal type. |
| `id` | Object | No | Request identifier from the corresponding request. Must match the request's id. | None | null | Echo of request id. |
| `result` | Object | Conditional | Success result of the method invocation. Present if and only if error is absent. | `@AssertTrue(isValid)` | null | Structure varies by method. Mutually exclusive with error. |
| `error` | JsonRpcError | Conditional | Error information if method invocation failed. Present if and only if result is absent. | `@Valid`, `@AssertTrue(isValid)` | null | See JsonRpcError. Mutually exclusive with result. |

**Validation Summary:**
- Must have valid JSON-RPC version ("2.0")
- Exactly one of `result` or `error` must be present (mutually exclusive)
- If error is present, it must be a valid JsonRpcError object

**Cross-Field Validation:**
- `isValid()`: Returns true if exactly one of result or error is non-null

**Example (Success):**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"rows\": [...]}"
      }
    ]
  }
}
```

**Example (Error):**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "Invalid params"
  }
}
```

---

### JsonRpcError

**Purpose:** Represents error information in a JSON-RPC 2.0 response.

**TypeScript Definition:**
```typescript
interface JSONRPCError {
  code: number;
  message: string;
  data?: unknown;
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.JsonRpcError`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `code` | int | Yes | Error code indicating the type of error. Standard codes defined by JSON-RPC 2.0 spec. | None (primitive) | - | See standard error codes below. |
| `message` | String | Yes | Human-readable description of the error. Should be concise and informative. | `@NotBlank` | - | Required and non-empty. |
| `data` | Object | No | Additional information about the error. Structure is application-defined. | None | null | Optional error details. |

**Standard Error Codes:**
- `-32700` - Parse error: Invalid JSON received
- `-32600` - Invalid request: JSON is not valid request object
- `-32601` - Method not found: Method does not exist
- `-32602` - Invalid params: Invalid method parameters
- `-32603` - Internal error: Internal server error

**Custom Error Codes:**
- `-32000` to `-32099` - Reserved for implementation-defined server errors

**Validation Summary:**
- Message must not be empty or whitespace-only
- Code must be an integer (can be negative)
- Data field is unconstrained

**Example:**
```json
{
  "code": -32602,
  "message": "Invalid params: Missing required field 'name'",
  "data": {
    "field": "name",
    "constraint": "required"
  }
}
```

---

## MCP Initialization Types

### InitializeRequest

**Purpose:** Request sent by client to initialize MCP session. First message in MCP protocol lifecycle.

**TypeScript Definition:**
```typescript
interface InitializeRequest {
  method: "initialize";
  params: {
    protocolVersion: string;
    capabilities: ClientCapabilities;
    clientInfo: Implementation;
  };
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.InitializeRequest`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `protocolVersion` | String | Yes | MCP protocol version the client wants to use. Format: "YYYY-MM-DD". | `@NotBlank` | - | Currently "2024-11-05". Server may negotiate. |
| `capabilities` | ClientCapabilities | Yes | Features and extensions the client supports. | `@NotNull`, `@Valid` | - | See ClientCapabilities. |
| `clientInfo` | Implementation | Yes | Information about the client implementation (name and version). | `@NotNull`, `@Valid` | - | See Implementation. |

**Validation Summary:**
- All fields are required
- Protocol version must be non-empty string
- Nested objects must be valid

**Lifecycle:** This is always the first request sent after connection establishment.

**Example:**
```json
{
  "protocolVersion": "2024-11-05",
  "capabilities": {
    "sampling": {}
  },
  "clientInfo": {
    "name": "example-client",
    "version": "1.0.0"
  }
}
```

---

### InitializeResult

**Purpose:** Response sent by server to complete initialization. Contains server capabilities.

**TypeScript Definition:**
```typescript
interface InitializeResult {
  protocolVersion: string;
  capabilities: ServerCapabilities;
  serverInfo: Implementation;
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.InitializeResult`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `protocolVersion` | String | Yes | MCP protocol version the server will use. Should match or be compatible with client request. | `@NotBlank` | - | Currently "2024-11-05". |
| `capabilities` | ServerCapabilities | Yes | Features and extensions the server supports. Informs client what operations are available. | `@NotNull`, `@Valid` | - | See ServerCapabilities. |
| `serverInfo` | Implementation | Yes | Information about the server implementation (name and version). | `@NotNull`, `@Valid` | - | See Implementation. |

**Validation Summary:**
- All fields are required
- Protocol version must be non-empty string
- Nested objects must be valid

**Lifecycle:** Sent in response to initialize request. After this, session is active.

**Example:**
```json
{
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
```

---

### Implementation

**Purpose:** Information about a client or server implementation.

**TypeScript Definition:**
```typescript
interface Implementation {
  name: string;
  version: string;
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.Implementation`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `name` | String | Yes | Name of the implementation. Should be unique and descriptive. | `@NotBlank` | - | E.g., "jmcp", "claude-desktop" |
| `version` | String | Yes | Version of the implementation. Should follow semantic versioning. | `@NotBlank` | - | E.g., "1.0.0", "2.3.1-beta" |

**Validation Summary:**
- Both fields required and non-empty
- No specific format enforced, but semantic versioning recommended

**Usage:** Used in both `clientInfo` and `serverInfo` fields.

**Example:**
```json
{
  "name": "jmcp",
  "version": "1.0.0"
}
```

---

### ClientCapabilities

**Purpose:** Declares what features the client supports.

**TypeScript Definition:**
```typescript
interface ClientCapabilities {
  experimental?: { [key: string]: unknown };
  sampling?: {};
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.ClientCapabilities`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `experimental` | Object | No | Arbitrary experimental or extension capabilities. Structure is implementation-defined. | None | null | For future extensions. Opaque to protocol. |
| `sampling` | Object | No | Empty object marker indicating client supports sampling feature. Presence indicates support. | None | null | Currently an empty object marker. |

**Validation Summary:**
- All fields optional
- No constraints on field values
- Presence of field indicates capability support

**Design Note:** Fields are presence markers. Non-null value indicates capability, structure is secondary.

**Example:**
```json
{
  "experimental": {
    "customFeature": true
  },
  "sampling": {}
}
```

---

### ServerCapabilities

**Purpose:** Declares what features the server supports. Informs client of available operations.

**TypeScript Definition:**
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

**Java Type:** `org.peacetalk.jmcp.core.model.ServerCapabilities`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `experimental` | Object | No | Arbitrary experimental or extension capabilities. | None | null | For future extensions. |
| `logging` | Object | No | Empty object marker indicating server supports logging. | None | null | Presence indicates support. |
| `prompts` | PromptsCapability | No | Prompts feature support and sub-capabilities. | `@Valid` | null | See nested type below. |
| `resources` | ResourcesCapability | No | Resources feature support and sub-capabilities. | `@Valid` | null | See nested type below. |
| `tools` | ToolsCapability | No | Tools feature support and sub-capabilities. | `@Valid` | null | See nested type below. |

**Validation Summary:**
- All fields optional
- Nested capability objects validated if present

#### Nested Type: PromptsCapability

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `listChanged` | Boolean | No | Whether server sends notifications when prompts list changes. | None | null | Dynamic prompt discovery. |

#### Nested Type: ResourcesCapability

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `subscribe` | Boolean | No | Whether server supports subscribing to resource updates. | None | null | Real-time resource updates. |
| `listChanged` | Boolean | No | Whether server sends notifications when resources list changes. | None | null | Dynamic resource discovery. |

#### Nested Type: ToolsCapability

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `listChanged` | Boolean | No | Whether server sends notifications when tools list changes. | None | null | Dynamic tool discovery. |

**Design Note:** Each capability object's presence indicates feature support. Nested booleans indicate sub-features.

**Example:**
```json
{
  "tools": {
    "listChanged": false
  },
  "resources": {
    "subscribe": true,
    "listChanged": true
  }
}
```

---

## MCP Tool Types

### Tool

**Purpose:** Definition of a tool that can be invoked by the client. Tools are functions the server exposes.

**TypeScript Definition:**
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

**Java Type:** `org.peacetalk.jmcp.core.model.Tool`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `name` | String | Yes | Unique identifier for the tool. Used when calling the tool. | `@NotBlank` | - | Should be kebab-case. E.g., "query", "list-tables" |
| `description` | String | No | Human-readable description of what the tool does. Helps users understand tool purpose. | None | null | Should be concise but informative. |
| `inputSchema` | JsonNode | Yes | JSON Schema (draft 7) describing the tool's input parameters. Must be object type. | `@NotNull` | - | Defines parameter structure and validation. |

**Input Schema Requirements:**
- Must be a valid JSON Schema
- Root type must be "object"
- Should include property definitions and required fields
- Used to validate tool invocation arguments

**Validation Summary:**
- Name is required and non-empty
- Description is optional
- Input schema is required and must be valid JSON

**Naming Convention:** Use kebab-case (e.g., "list-tables", "get-row-count")

**Example:**
```json
{
  "name": "query",
  "description": "Execute a read-only SQL query",
  "inputSchema": {
    "type": "object",
    "properties": {
      "sql": {
        "type": "string",
        "description": "SQL query to execute"
      },
      "parameters": {
        "type": "array",
        "items": {
          "type": "string"
        },
        "description": "Query parameters"
      }
    },
    "required": ["sql"]
  }
}
```

---

### ListToolsResult

**Purpose:** Result of listing available tools. Returned by "tools/list" method.

**TypeScript Definition:**
```typescript
interface ListToolsResult {
  tools: Tool[];
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.ListToolsResult`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `tools` | List&lt;Tool&gt; | Yes | Array of all tools the server provides. May be empty if no tools available. | `@NotNull`, `@Valid` | empty list | Each tool validated individually. |

**Validation Summary:**
- Tools array is required (but can be empty)
- Each tool in array must be valid

**Usage:** Client calls "tools/list" to discover available tools. Server returns this structure.

**Example:**
```json
{
  "tools": [
    {
      "name": "query",
      "description": "Execute SQL query",
      "inputSchema": { ... }
    },
    {
      "name": "list-tables",
      "description": "List all tables",
      "inputSchema": { ... }
    }
  ]
}
```

---

### CallToolRequest

**Purpose:** Request to invoke a specific tool. Parameters for "tools/call" method.

**TypeScript Definition:**
```typescript
interface CallToolRequest {
  method: "tools/call";
  params: {
    name: string;
    arguments?: { [key: string]: unknown };
  };
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.CallToolRequest`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `name` | String | Yes | Name of the tool to invoke. Must match a tool name from tools/list. | `@NotBlank` | - | Case-sensitive. |
| `arguments` | JsonNode | No | Arguments to pass to the tool. Structure must match tool's inputSchema. | None | null | Validated against tool's schema. |

**Validation Summary:**
- Tool name is required and non-empty
- Arguments structure validated by tool (not at protocol level)

**Execution Flow:**
1. Client calls "tools/call" with tool name and arguments
2. Server validates arguments against tool's inputSchema
3. Server executes tool
4. Server returns CallToolResult

**Example:**
```json
{
  "name": "query",
  "arguments": {
    "sql": "SELECT * FROM users WHERE age > 25",
    "parameters": []
  }
}
```

---

### CallToolResult

**Purpose:** Result of tool execution. Returned by "tools/call" method.

**TypeScript Definition:**
```typescript
interface CallToolResult {
  content: Content[];
  isError?: boolean;
}
```

**Java Type:** `org.peacetalk.jmcp.core.model.CallToolResult`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `content` | List&lt;Content&gt; | Yes | Array of content items returned by the tool. Can contain text, images, or mix. | `@NotNull`, `@Valid` | empty list | Each content item validated. |
| `isError` | Boolean | No | Whether the tool execution resulted in an error. If true, content contains error details. | None | null | Distinguishes success from failure. |

**Validation Summary:**
- Content array is required (but can be empty)
- Each content item must be valid
- isError defaults to false (success) if absent

**Success vs Error:**
- Success: `isError` is null or false, content has result data
- Error: `isError` is true, content has error message(s)

**Design Note:** Unlike JSON-RPC errors, tool errors are still successful RPC calls. The tool ran, but reported an error.

**Example (Success):**
```json
{
  "content": [
    {
      "type": "text",
      "text": "{\"rows\": [{\"id\": 1, \"name\": \"Alice\"}]}"
    }
  ],
  "isError": false
}
```

**Example (Error):**
```json
{
  "content": [
    {
      "type": "text",
      "text": "SQL Error: Table 'users' does not exist"
    }
  ],
  "isError": true
}
```

---

### Content

**Purpose:** Content item in a tool result. Can be text or image data. Discriminated union type.

**TypeScript Definition:**
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

**Java Type:** `org.peacetalk.jmcp.core.model.Content`

#### Fields

| Field | Type | Required | Description | Validation Rules | Default | Notes |
|-------|------|----------|-------------|------------------|---------|-------|
| `type` | String | Yes | Discriminator indicating content type. Must be "text" or "image". | `@NotBlank`, `@Pattern(regexp = "text\\|image")` | - | TypeScript literal union. Determines which other fields are present. |
| `text` | String | Conditional | Text content. Required when type="text", null otherwise. | `@AssertTrue(isValidTextContent)` | null | Plain text or JSON string. |
| `data` | String | Conditional | Base64-encoded image data. Required when type="image", null otherwise. | `@AssertTrue(isValidImageContent)` | null | Base64 string. |
| `mimeType` | String | Conditional | MIME type of the image. Required when type="image", null otherwise. | `@AssertTrue(isValidImageContent)` | null | E.g., "image/png", "image/jpeg" |

**Validation Summary:**
- Type must be "text" or "image"
- For text content: text field must be non-null
- For image content: both data and mimeType must be non-null
- Fields for the non-selected type should be null

**Cross-Field Validation:**
- `isValidTextContent()`: Returns true if type is not "text" OR text is non-null
- `isValidImageContent()`: Returns true if type is not "image" OR both data and mimeType are non-null

**Factory Methods:**
- `Content.text(String text)` - Creates text content
- `Content.image(String data, String mimeType)` - Creates image content

**Example (Text):**
```json
{
  "type": "text",
  "text": "Query returned 42 rows"
}
```

**Example (Image):**
```json
{
  "type": "image",
  "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
  "mimeType": "image/png"
}
```

---

## Validation Reference

### Annotation Summary

| Annotation | Purpose | Applied To |
|------------|---------|------------|
| `@NotNull` | Field must not be null | Reference types that are required |
| `@NotBlank` | String must not be null, empty, or whitespace | String fields that are required |
| `@Pattern` | String must match regex | Literal types (jsonrpc, content type) |
| `@Valid` | Nested object must be validated | Complex nested objects |
| `@AssertTrue` | Custom validation method must return true | Cross-field validation |

### Standard Validation Messages

- "Tool name is required" - Tool.name
- "Input schema is required" - Tool.inputSchema
- "Content array is required" - CallToolResult.content
- "Content type is required" - Content.type
- "Type must be 'text' or 'image'" - Content.type pattern
- "Implementation name is required" - Implementation.name
- "Implementation version is required" - Implementation.version
- "Protocol version is required" - InitializeRequest/Result.protocolVersion
- "Capabilities are required" - InitializeRequest/Result.capabilities
- "JSON-RPC version is required" - JsonRpcRequest/Response.jsonrpc
- "JSON-RPC version must be '2.0'" - JsonRpcRequest/Response.jsonrpc pattern
- "Method name is required" - JsonRpcRequest.method
- "Error message is required" - JsonRpcError.message
- "Tools list is required" - ListToolsResult.tools
- "Exactly one of result or error must be present" - JsonRpcResponse mutual exclusivity
- "Text content must have text field" - Content discriminated union
- "Image content must have data and mimeType" - Content discriminated union

---

## Type Mappings

### TypeScript to Java

| TypeScript Type | Java Type | Notes |
|----------------|-----------|-------|
| `string` | `String` | Standard mapping |
| `number` | `int` or `Object` | int for error codes, Object for ids |
| `boolean` | `Boolean` | Object type for optionality |
| `unknown` | `Object` or `JsonNode` | JsonNode when structure matters |
| `T[]` | `List<T>` | Generic list |
| `{ [key: string]: T }` | `JsonNode` or `Object` | JsonNode for structure, Object for presence |
| `"literal"` | `String` | With @Pattern validation |
| `A \| B` | Single record + discriminator | With @AssertTrue validation |

### Primitive Handling

- **Optional fields**: Nullable reference types
- **Required fields**: Non-null with validation
- **Literal types**: String with Pattern constraint
- **Union types**: Discriminated with runtime checks

---

## Schema Versioning

**Current Version:** 2024-11-05

**Version History:**
- 2024-11-05: Initial MCP protocol specification

**Compatibility:**
- Client and server negotiate protocol version during initialization
- Server should support multiple versions when possible
- Breaking changes require new protocol version

---

## Best Practices

### Tool Design
1. Use descriptive tool names (kebab-case)
2. Provide clear descriptions
3. Define complete JSON schemas with descriptions
4. Mark all required parameters
5. Include parameter descriptions in schema

### Error Handling
1. Use JSON-RPC errors for protocol-level errors
2. Use CallToolResult.isError for tool-level errors
3. Provide helpful error messages
4. Include error details in data field when useful

### Validation
1. Validate at protocol boundaries (external input)
2. Use McpValidator for explicit validation
3. Trust internal objects (already validated)
4. Provide clear validation error messages

### Performance
1. Validation cost is negligible (~microseconds)
2. Validate once per request
3. Don't re-validate already validated objects
4. Cache validator instance (done automatically)

---

## Appendix A: Complete Field Index

| Type | Field | Java Type | Required | Validation |
|------|-------|-----------|----------|------------|
| JsonRpcRequest | jsonrpc | String | Yes | @NotNull, @Pattern("2\\.0") |
| JsonRpcRequest | id | Object | No | - |
| JsonRpcRequest | method | String | Yes | @NotBlank |
| JsonRpcRequest | params | Object | No | - |
| JsonRpcResponse | jsonrpc | String | Yes | @NotNull, @Pattern("2\\.0") |
| JsonRpcResponse | id | Object | No | - |
| JsonRpcResponse | result | Object | Conditional | @AssertTrue(isValid) |
| JsonRpcResponse | error | JsonRpcError | Conditional | @Valid, @AssertTrue(isValid) |
| JsonRpcError | code | int | Yes | - |
| JsonRpcError | message | String | Yes | @NotBlank |
| JsonRpcError | data | Object | No | - |
| InitializeRequest | protocolVersion | String | Yes | @NotBlank |
| InitializeRequest | capabilities | ClientCapabilities | Yes | @NotNull, @Valid |
| InitializeRequest | clientInfo | Implementation | Yes | @NotNull, @Valid |
| InitializeResult | protocolVersion | String | Yes | @NotBlank |
| InitializeResult | capabilities | ServerCapabilities | Yes | @NotNull, @Valid |
| InitializeResult | serverInfo | Implementation | Yes | @NotNull, @Valid |
| Implementation | name | String | Yes | @NotBlank |
| Implementation | version | String | Yes | @NotBlank |
| ClientCapabilities | experimental | Object | No | - |
| ClientCapabilities | sampling | Object | No | - |
| ServerCapabilities | experimental | Object | No | - |
| ServerCapabilities | logging | Object | No | - |
| ServerCapabilities | prompts | PromptsCapability | No | @Valid |
| ServerCapabilities | resources | ResourcesCapability | No | @Valid |
| ServerCapabilities | tools | ToolsCapability | No | @Valid |
| PromptsCapability | listChanged | Boolean | No | - |
| ResourcesCapability | subscribe | Boolean | No | - |
| ResourcesCapability | listChanged | Boolean | No | - |
| ToolsCapability | listChanged | Boolean | No | - |
| Tool | name | String | Yes | @NotBlank |
| Tool | description | String | No | - |
| Tool | inputSchema | JsonNode | Yes | @NotNull |
| ListToolsResult | tools | List&lt;Tool&gt; | Yes | @NotNull, @Valid |
| CallToolRequest | name | String | Yes | @NotBlank |
| CallToolRequest | arguments | JsonNode | No | - |
| CallToolResult | content | List&lt;Content&gt; | Yes | @NotNull, @Valid |
| CallToolResult | isError | Boolean | No | - |
| Content | type | String | Yes | @NotBlank, @Pattern("text\\|image") |
| Content | text | String | Conditional | @AssertTrue(isValidTextContent) |
| Content | data | String | Conditional | @AssertTrue(isValidImageContent) |
| Content | mimeType | String | Conditional | @AssertTrue(isValidImageContent) |

---

**End of Data Dictionary**

*This data dictionary is automatically synchronized with the implementation. Any changes to validation rules or field definitions must be reflected here.*


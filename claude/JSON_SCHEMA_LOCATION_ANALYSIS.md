# JSON Schema Helper Classes - Location Analysis

**Date:** December 24, 2025

## Current Situation

### Location
The JSON Schema helper classes are currently in:
```
jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/schema/
├── ArrayProperty.java
├── IntegerProperty.java
├── ObjectSchema.java
└── StringProperty.java
```

### Package
`org.peacetalk.jmcp.jdbc.schema`

### Module Exports
The module-info.java **opens** the package (for Jackson reflection) but does **not export** it:
```java
opens org.peacetalk.jmcp.jdbc.schema;
```

This means:
- ✅ Jackson can access the classes via reflection
- ❌ Other modules cannot import these classes
- ✅ These are effectively internal to jmcp-jdbc module

## Usage Analysis

### Where They're Used
The schema helper classes are **only** used within the `jmcp-jdbc` module by the tool implementations:

1. **QueryTool** - Uses ObjectSchema, StringProperty, ArrayProperty
2. **DescribeTableTool** - Uses ObjectSchema, StringProperty
3. **ListTablesTool** - Uses ObjectSchema, StringProperty
4. **ListSchemasTool** - Uses ObjectSchema
5. **PreviewTableTool** - Uses ObjectSchema, StringProperty, IntegerProperty
6. **GetRowCountTool** - Uses ObjectSchema, StringProperty

**Total:** 6 tools in the jdbc module

### Not Used By
- ❌ jmcp-core module
- ❌ jmcp-server module
- ❌ jmcp-client module
- ❌ jmcp-transport-stdio module
- ❌ Any test modules

## Purpose

These classes are **convenience builders** for creating JSON Schema objects that get converted to `JsonNode` for the MCP Tool.inputSchema field.

### The Pattern
```java
// Build schema using type-safe records
ObjectSchema schema = new ObjectSchema(
    Map.of(
        "sql", new StringProperty("The SELECT query to execute"),
        "parameters", new ArrayProperty(
            "Optional query parameters",
            new StringProperty("Parameter value")
        )
    ),
    List.of("sql")  // required fields
);

// Convert to JsonNode for MCP Tool
return MAPPER.valueToTree(schema);
```

This produces JSON like:
```json
{
  "type": "object",
  "properties": {
    "sql": {
      "type": "string",
      "description": "The SELECT query to execute"
    },
    "parameters": {
      "type": "array",
      "description": "Optional query parameters",
      "items": {
        "type": "string",
        "description": "Parameter value"
      }
    }
  },
  "required": ["sql"]
}
```

## Are They Duplicated?

**NO** - These classes are **not duplicated** in the project.

There is **no similar code** in:
- jmcp-core
- jmcp-server
- jmcp-client
- jmcp-transport-stdio

The only JSON Schema reference in the core module is in the `Tool` record, which uses `JsonNode` to accept any JSON structure.

## Should They Be Moved?

### Arguments FOR Moving to jmcp-core

1. **General Utility** - JSON Schema is a general concept, not JDBC-specific
2. **Reusability** - Other MCP servers (HTTP, GraphQL, etc.) would benefit
3. **Logical Separation** - Schema building is separate from JDBC operations
4. **Better Organization** - Core contains protocol/model concepts

### Arguments AGAINST Moving to jmcp-core

1. **YAGNI Principle** - Currently only used by JDBC tools, no other need exists
2. **Simplicity** - Core module stays focused on protocol models
3. **No Duplication** - There's no redundancy to eliminate
4. **Tight Coupling** - These are tightly coupled to how JDBC tools build schemas
5. **Module Boundary** - Not part of MCP protocol specification

## Recommendation

### ✅ **Keep them in jmcp-jdbc module** (Current location is appropriate)

**Reasons:**

1. **Single Use Case** - Only JDBC tools use them
2. **Internal Helper** - They're implementation details, not public API
3. **No Duplication** - There's no redundancy problem to solve
4. **YAGNI** - Don't move code until there's a proven need elsewhere
5. **Encapsulation** - Keeps JDBC module self-contained

### ⚠️ **However, consider these improvements:**

#### 1. Better Package Name
Current: `org.peacetalk.jmcp.jdbc.schema`
Could be: `org.peacetalk.jmcp.jdbc.schema.builder`

**Rationale:** Makes it clear these are *builders* for schemas, not schema validation/parsing.

#### 2. Add Package Documentation
Create `package-info.java`:
```java
/**
 * Internal helper classes for building JSON Schema objects used in JDBC tool definitions.
 * 
 * These classes provide type-safe builders for constructing JSON Schema structures
 * that are serialized to JsonNode and used in MCP Tool.inputSchema fields.
 * 
 * <p>These are internal utilities and not part of the public API.
 * 
 * @see org.peacetalk.jmcp.core.model.Tool
 */
package org.peacetalk.jmcp.jdbc.schema;
```

#### 3. Mark as Internal
Add `@Internal` annotation or JavaDoc comment:
```java
/**
 * Represents an object schema in JSON Schema.
 * 
 * <p><b>Internal API:</b> This class is for internal use within the jmcp-jdbc module
 * and is not part of the public API. It may change without notice.
 */
```

#### 4. Document the Pattern
Add a comprehensive class-level comment explaining the usage pattern:
```java
/**
 * Helper classes for building JSON Schema objects in JDBC tools.
 * 
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * ObjectSchema schema = new ObjectSchema(
 *     Map.of(
 *         "name", new StringProperty("Description"),
 *         "age", new IntegerProperty("Age", 0, 120)
 *     ),
 *     List.of("name")  // required fields
 * );
 * JsonNode schemaNode = objectMapper.valueToTree(schema);
 * }</pre>
 */
```

## Alternative: Move to Separate Module (Future)

If more MCP server implementations emerge (HTTP, GraphQL, MongoDB, etc.), **then** consider:

```
jmcp-schema-builder/
├── pom.xml
└── src/main/java/org/peacetalk/jmcp/schema/
    ├── ArrayProperty.java
    ├── IntegerProperty.java
    ├── ObjectSchema.java
    ├── StringProperty.java
    └── BooleanProperty.java (new)
    └── NumberProperty.java (new)
    └── package-info.java
```

**But only when:**
- At least 2 different server types need them
- There's demonstrated value in sharing
- The API is stable and proven

## Comparison with MCP Protocol

The MCP specification says tools have an `inputSchema` of type:
```typescript
{
  type: "object";
  properties?: { [key: string]: unknown };
  required?: string[];
}
```

Our helper classes map directly:
- `ObjectSchema` → The object schema structure
- `StringProperty`, `IntegerProperty`, `ArrayProperty` → Property types
- `Map<String, Object>` → properties
- `List<String>` → required

This is a **correct and appropriate** abstraction.

## Conclusion

**Status:** ✅ **No action needed** - Current location is appropriate

**Reason:** These are internal JDBC-specific helpers with no duplication, no reuse need, and proper encapsulation.

**Optional Improvements:**
1. Rename package to `.schema.builder` (clarifies purpose)
2. Add package-info.java documentation
3. Mark as internal API in JavaDoc
4. Document usage patterns

**Future Consideration:**
Only move to shared module when:
- Multiple server types exist
- Proven reuse need emerges
- API is stable

## Code Statistics

**Current:**
- Files: 4
- Package: org.peacetalk.jmcp.jdbc.schema
- Users: 6 JDBC tools (same module)
- Export: No (internal only)
- Open: Yes (for Jackson)

**Impact of Moving:**
- Would require exporting from new location
- Would require updating 13 import statements
- Would create dependency on new module
- Would add complexity without current benefit

**Verdict:** **Keep as is** with optional documentation improvements.


# JSON Schema Classes - Revised Analysis

**Date:** December 24, 2025

## Reconsidering the Location

### User's Valid Points

1. **Future MCP server modules** - If adding HTTP, GraphQL, MongoDB, or other tool types, they'll need schema builders
2. **Client-side schema awareness** - The GUI client could provide better UX if it understands schema structure beyond raw JSON

### Current Situation

**Location:** `org.peacetalk.jmcp.jdbc.schema`
- ✅ Only used by JDBC tools currently
- ❌ Not accessible to other modules (not exported)
- ❌ Package name implies JDBC-specific

## Why They SHOULD Be Moved

### 1. Future Server Modules

If you create:
- `jmcp-http` - Tools for HTTP/REST APIs
- `jmcp-graphql` - Tools for GraphQL queries
- `jmcp-mongodb` - Tools for MongoDB operations
- `jmcp-redis` - Tools for Redis operations

**Each would need to define tool schemas** and would benefit from these helpers:

```java
// In jmcp-http module
ObjectSchema schema = new ObjectSchema(
    Map.of(
        "url", new StringProperty("The URL to call"),
        "method", new StringProperty("HTTP method"),
        "headers", new ObjectSchema(Map.of())
    ),
    List.of("url", "method")
);
```

**Without moving them:** Each module would need to:
- Duplicate the schema classes (bad!)
- Build schemas manually with Maps (error-prone!)
- Depend on jmcp-jdbc just for schema helpers (wrong!)

### 2. Client-Side Schema Awareness

The GUI client could provide **much better UX** if it understands schemas:

**Current (JSON-only):**
```
Input Schema:
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 100
    }
  }
}
```

**Potential with Schema Classes:**
```java
// Parse schema to generate better UI
if (property instanceof IntegerProperty intProp) {
    // Show spinner with min/max bounds
    Spinner<Integer> spinner = new Spinner<>(
        intProp.minimum(), 
        intProp.maximum(), 
        intProp.minimum()
    );
} else if (property instanceof StringProperty strProp) {
    // Show text field with validation
    TextField field = new TextField();
    if (strProp.minLength() != null) {
        field.setTextFormatter(...);
    }
}
```

**Benefits:**
- 🎯 Generate appropriate input controls (spinner for integers, checkbox for boolean)
- ✅ Client-side validation with proper error messages
- 📊 Show constraints (min/max, required fields) in UI
- 🎨 Better form layout based on schema structure

## Recommended Action: Move to Core Module

### New Location
```
jmcp-core/src/main/java/org/peacetalk/jmcp/core/schema/
├── ObjectSchema.java
├── StringProperty.java
├── IntegerProperty.java
├── BooleanProperty.java (new)
├── NumberProperty.java (new)
├── ArrayProperty.java
└── package-info.java
```

### Package Name
`org.peacetalk.jmcp.core.schema` - Makes it clear these are core schema utilities

### Module Export
```java
module org.peacetalk.jmcp.core {
    // ... existing exports ...
    exports org.peacetalk.jmcp.core.schema;
}
```

### Why Core Module?

1. **Shared by all server modules** - HTTP, GraphQL, MongoDB tools all need schemas
2. **Useful for client** - Can parse schemas for better UI generation
3. **Protocol-level concept** - JSON Schema is part of MCP specification
4. **No dependencies** - Schema classes only need Jackson (already in core)
5. **Logical grouping** - Goes alongside `Tool` which references schemas

## Migration Plan

### Phase 1: Move Classes to Core

1. Create `jmcp-core/src/main/java/org/peacetalk/jmcp/core/schema/`
2. Move existing classes from jdbc.schema to core.schema
3. Update imports in all JDBC tools (6 files)
4. Update module-info.java in both modules
5. Run tests to verify

### Phase 2: Add Missing Property Types

Common JSON Schema types not yet implemented:
```java
// jmcp-core/src/main/java/org/peacetalk/jmcp/core/schema/

public record BooleanProperty(
    String type,
    String description
) {
    public BooleanProperty(String description) {
        this("boolean", description);
    }
}

public record NumberProperty(
    String type,
    String description,
    Double minimum,
    Double maximum
) {
    public NumberProperty(String description) {
        this("number", description, null, null);
    }
}

public record EnumProperty(
    String type,
    String description,
    List<String> enumValues
) {
    public EnumProperty(String description, String... values) {
        this("string", description, List.of(values));
    }
}
```

### Phase 3: Enhance Client to Use Schemas

```java
// In McpClientController.java
private void buildArgumentFields(Tool tool) {
    JsonNode schemaNode = tool.inputSchema();
    
    // Try to parse as ObjectSchema
    try {
        ObjectSchema schema = MAPPER.treeToValue(schemaNode, ObjectSchema.class);
        
        // Generate typed controls based on schema
        for (Map.Entry<String, Object> entry : schema.properties().entrySet()) {
            String fieldName = entry.getKey();
            Object property = entry.getValue();
            
            if (property instanceof IntegerProperty intProp) {
                // Create spinner with constraints
                Spinner<Integer> spinner = new Spinner<>(
                    intProp.minimum() != null ? intProp.minimum() : Integer.MIN_VALUE,
                    intProp.maximum() != null ? intProp.maximum() : Integer.MAX_VALUE,
                    intProp.minimum() != null ? intProp.minimum() : 0
                );
                argumentsBox.getChildren().addAll(
                    new Label(fieldName + (isRequired ? " *" : "")),
                    spinner
                );
            } else if (property instanceof BooleanProperty boolProp) {
                // Create checkbox
                CheckBox checkBox = new CheckBox(boolProp.description());
                argumentsBox.getChildren().add(checkBox);
            }
            // ... handle other types
        }
    } catch (Exception e) {
        // Fallback to current text field approach
        buildTextFieldsFromJsonNode(schemaNode);
    }
}
```

## Benefits of Moving

### For Server Modules
✅ All server types can use same schema builders  
✅ Consistent schema structure across tools  
✅ No code duplication  
✅ Type-safe schema construction  

### For Client
✅ Generate appropriate UI controls (spinner, checkbox, etc.)  
✅ Client-side validation before sending  
✅ Show constraints in UI (min/max, required)  
✅ Better error messages  
✅ Improved user experience  

### For Maintainability
✅ Single source of truth for schemas  
✅ Easier to add new property types  
✅ Consistent behavior across modules  
✅ Better tested (used in more places)  

## Updated Recommendation

### ✅ **MOVE to jmcp-core module**

**Reasons:**
1. **Future server modules will need them** - HTTP, GraphQL, MongoDB tools
2. **Client can provide better UX** - Type-aware input controls
3. **Part of MCP protocol concept** - Tool.inputSchema is in spec
4. **Enables consistency** - All tools use same schema structure
5. **No downside** - Core already has Jackson dependency

### Migration Effort
- **Low risk** - Simple package move
- **6 import updates** - In JDBC tools
- **2 module-info changes** - Export from core, remove from jdbc
- **Tests still pass** - No logic changes

### Timeline
- **Immediate** - Can be done now
- **Impact** - Unblocks future development
- **Value** - Enables better client UX today

## Revised Conclusion

**Original assessment was too conservative.**

Your use cases are valid:
1. ✅ Future tool modules will need schema builders
2. ✅ Client should be schema-aware for better UX

**Action:** Move schema classes to `org.peacetalk.jmcp.core.schema`

This sets up the architecture properly for:
- Multiple server module types
- Rich client-side schema interpretation
- Consistent tooling across the ecosystem


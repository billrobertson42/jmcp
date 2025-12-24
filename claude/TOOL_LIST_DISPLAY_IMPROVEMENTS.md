# Tool List Display Improvements

**Date:** December 23, 2025

## Problem

The tools list in the MCP Client GUI displayed tools poorly:
- Showed "Tool[...]" with JSON representation
- Tool names were buried in the JSON output
- Descriptions were not visible at all
- List items were not user-friendly

**Example of old display:**
```
Tool[name=query, description=Execute a read-only SQL query, inputSchema={...}]
Tool[name=list-tables, description=List all tables in the database, inputSchema={...}]
```

## Solution

Implemented a custom `ListCell` factory for the tools ListView to properly display tool information:

1. **Tool name as list item text** - Clear, concise display
2. **Description as tooltip** - Hover to see full description
3. **Keep description in detail pane** - Still shown when selected
4. **Styled tooltips** - Dark background, white text, word wrapping

### Implementation

**File:** `McpClientController.java`

Added custom cell factory in the `initialize()` method:

```java
// Setup tool list cell factory for better display
toolsList.setCellFactory(listView -> new ListCell<Tool>() {
    @Override
    protected void updateItem(Tool tool, boolean empty) {
        super.updateItem(tool, empty);
        
        if (empty || tool == null) {
            setText(null);
            setTooltip(null);
            setGraphic(null);
        } else {
            // Display tool name as the main text
            setText(tool.name());
            
            // Add description as tooltip if available
            if (tool.description() != null && !tool.description().isBlank()) {
                Tooltip tooltip = new Tooltip(tool.description());
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(400);
                setTooltip(tooltip);
            } else {
                setTooltip(null);
            }
        }
    }
});
```

## Result

**New display:**
```
query
list-tables
describe-table
preview-table
get-row-count
list-schemas
```

Hover over any tool to see its description in a tooltip.

## How It Works

### ListView Cell Factory

JavaFX ListView uses a cell factory to create and configure the visual representation of each item.

**Default behavior:**
- Calls `toString()` on the object (Tool record)
- Java records generate `toString()` that includes all fields
- Results in verbose, JSON-like display

**Custom cell factory:**
- Overrides `updateItem()` method
- Sets only the tool name as text
- Adds tooltip with description
- Clears display for empty cells

### Tooltip Features

1. **Automatic display** - Shows on hover after short delay
2. **Word wrapping** - `setWrapText(true)` prevents long lines
3. **Max width** - `setMaxWidth(400)` ensures readability
4. **Styled via CSS** - Dark theme, good contrast

## Additional Information Displayed

### When Tool is Selected

The right detail pane shows:

1. **Tool Name** - In the section header (implicit from selection)
2. **Description** - Full description in text area
3. **Input Schema** - JSON schema showing:
   - Required parameters
   - Optional parameters
   - Parameter types
   - Validation rules
4. **Argument Fields** - Dynamic form based on schema
5. **Execution Results** - After tool is executed

### Information Flow

```
List Item (Tool Name)
    ↓ (hover)
Tooltip (Brief Description)
    ↓ (click/select)
Detail Pane:
    - Full Description
    - Complete Input Schema
    - Generated Argument Form
```

## JavaFX ListView Best Practices

### Custom Cell Rendering

The pattern used here is the standard JavaFX approach:

```java
listView.setCellFactory(lv -> new ListCell<T>() {
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        // Always call super first
        
        if (empty || item == null) {
            // Clear the cell
            setText(null);
            setGraphic(null);
            setTooltip(null);
        } else {
            // Populate the cell
            setText(item.getName());
            setTooltip(new Tooltip(item.getDescription()));
        }
    }
});
```

### Why This Pattern?

1. **Reuse** - Cells are reused as you scroll
2. **Performance** - Only visible cells are rendered
3. **Memory efficient** - Don't create cells for every item
4. **Standard JavaFX** - Documented, well-understood pattern

## Alternative Approaches Considered

### 1. Override Tool.toString()

Could override `toString()` in the Tool record:

**Pros:**
- Simple, one-line change
- Works everywhere Tool is displayed

**Cons:**
- Can't customize per use case
- Loses detailed toString() for debugging
- Can't add tooltips

**Not used:** Cell factory is more flexible

### 2. Use ListCell with Graphics

Could create a custom graphic (HBox with labels):

```java
HBox cell = new HBox(5);
Label name = new Label(tool.name());
Label desc = new Label(tool.description());
cell.getChildren().addAll(name, desc);
setGraphic(cell);
```

**Pros:**
- More complex layouts possible
- Can show multiple pieces of info

**Cons:**
- More complex code
- Takes more screen space
- Description better as tooltip

**Not used:** Simple text + tooltip is cleaner

### 3. Two-Column ListView

Could use a TableView instead:

**Pros:**
- Can show name and description side-by-side
- Sortable, filterable

**Cons:**
- More complex UI
- Takes more horizontal space
- Overkill for simple list

**Not used:** ListView with tooltip is simpler

## Tooltip Styling

The tooltip styling is defined in `styles.css`:

```css
.tooltip {
    -fx-font-size: 13px;
    -fx-background-color: #2c2c2c;
    -fx-text-fill: white;
    -fx-padding: 6px 8px;
    -fx-background-radius: 4px;
}
```

This provides:
- Readable 13px font
- Dark background (#2c2c2c)
- White text for high contrast
- Rounded corners (4px radius)
- Comfortable padding

## Testing

To verify the improvements:

1. Run the client: `./run-client.sh`
2. Connect to a server
3. Check the tools list shows only tool names
4. Hover over a tool - tooltip should appear with description
5. Select a tool - description should appear in detail pane
6. Verify tooltip wraps long descriptions

## Benefits

1. ✅ **Cleaner list** - Only shows essential info (name)
2. ✅ **Discoverable** - Hover to learn what each tool does
3. ✅ **Consistent** - All tools displayed uniformly
4. ✅ **Space efficient** - Description hidden until needed
5. ✅ **Professional** - Standard UI pattern users expect
6. ✅ **Accessible** - Tooltips work with screen readers

## Future Enhancements

Potential improvements:

1. **Tool Icons** - Add icons based on tool type/category
2. **Tool Categories** - Group tools by functionality
3. **Search/Filter** - Filter tools by name or description
4. **Keyboard Shortcuts** - Quick tool selection
5. **Recent Tools** - Show recently used tools at top
6. **Favorites** - Mark frequently used tools
7. **Tool Stats** - Show execution count, last used time

## Code Location

**Modified File:**
- `/jmcp-client/src/main/java/org/peacetalk/jmcp/client/McpClientController.java`
  - Added cell factory in `initialize()` method
  - Lines ~48-73

**Existing Files Used:**
- `/jmcp-client/src/main/resources/org/peacetalk/jmcp/client/styles.css`
  - Tooltip styling already present
  - Lines 236-243

## References

- [JavaFX ListView Tutorial](https://openjfx.io/javadoc/17/javafx.controls/javafx/scene/control/ListView.html)
- [JavaFX Cell Factory Pattern](https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/Cell.html)
- [JavaFX Tooltip](https://openjfx.io/javadoc/17/javafx.controls/javafx/scene/control/Tooltip.html)


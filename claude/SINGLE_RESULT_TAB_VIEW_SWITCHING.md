# Single Result Tab with View Switching

## Summary

Modified the MCP client UI to use a single "Result" tab that intelligently switches between tool and resource result views, preserving state for both.

## Implementation Date

January 2, 2026

## Changes Made

### UI Structure (McpClient.fxml)

**Before:**
- Separate "Tool Result" and "Resource Result" tabs
- Each result type had its own dedicated tab in the output panel

**After:**
- Single "Result" tab with a StackPane containing both views
- Tool result view: TextArea for tool execution output
- Resource result view: VBox with navigation controls and NavigableResourceView
- Views use `visible` and `managed` properties to switch without losing state

### Controller Updates (McpClientController.java)

**Removed:**
- `@FXML private TabPane outputTabPane`
- `@FXML private Tab resourceResultTab`

**Added:**
- `@FXML private StackPane resultStackPane`
- `@FXML private VBox resourceResultView`
- `showToolView()` - Switches display to tool result view
- `showResourceView()` - Switches display to resource result view

**Modified:**
- `initialize()` - Added tab selection listener to switch views based on active tab (Tools/Resources)
- `onExecute()` - Removed `showToolView()` call (handled by tab selection)
- `navigateToResource()` - Removed `showResourceView()` call (handled by tab selection)

## Benefits

1. **Cleaner UI**: Single result tab reduces clutter in the tab bar
2. **State Preservation**: Both views maintain their content when switching
   - Tool results remain visible when returning from resource browsing
   - Resource navigation history is preserved when executing tools
3. **Context Awareness**: The UI automatically shows the appropriate view based on the **selected tab**
4. **Intuitive Navigation**: Users control the view by selecting Tools or Resources tab
5. **No Data Loss**: Switching between views doesn't clear or reset content

## Technical Details

### View Switching Mechanism

The view switching is now tied to the left TabPane selection:

```java
// Setup tab selection listener to switch result views
leftTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
    if (newTab != null) {
        String tabText = newTab.getText();
        if ("Tools".equals(tabText)) {
            showToolView();
        } else if ("Resources".equals(tabText)) {
            showResourceView();
        }
    }
});
```

When the user selects:
- **Tools tab** → Result area shows tool execution output (TextArea)
- **Resources tab** → Result area shows resource navigation view (NavigableResourceView with back button)

The StackPane contains both views at the same time, but only one is visible/managed:

```java
private void showToolView() {
    resultArea.setVisible(true);
    resultArea.setManaged(true);
    resourceResultView.setVisible(false);
    resourceResultView.setManaged(false);
}

private void showResourceView() {
    resultArea.setVisible(false);
    resultArea.setManaged(false);
    resourceResultView.setVisible(true);
    resourceResultView.setManaged(true);
}
```

Using `managed=false` removes the hidden view from layout calculations, allowing the visible view to fill the available space.

## Build Verification

All tests pass:
- **541 tests, 0 failures**
- No regressions introduced by the UI restructuring


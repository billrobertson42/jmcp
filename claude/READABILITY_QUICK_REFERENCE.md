# JavaFX Readability - Quick Reference

## What Was Done

✅ Created comprehensive CSS stylesheet (`styles.css`)
✅ Increased all font sizes (13px → 14px base, headers 16px)
✅ Improved text contrast (dark text on white backgrounds)
✅ Larger buttons with better colors (blue primary, red disconnect)
✅ Added focus indicators (blue glow on focused elements)
✅ Created `AccessibilityHelper` utility for programmatic control
✅ Applied style classes throughout the FXML

## Quick Enable Options

### Option 1: Default Improved Style (Automatic)
Just run the app - improvements are automatic via CSS.

### Option 2: Large Text (25% bigger)
In `McpClientApp.java`, uncomment:
```java
AccessibilityHelper.applyLargeTextPreset(scene);
```

### Option 3: Extra Large + High Contrast (50% bigger)
In `McpClientApp.java`, uncomment:
```java
AccessibilityHelper.applyExtraLargeTextPreset(scene);
```

### Option 4: Custom Scale
In `McpClientApp.java`, uncomment and adjust:
```java
AccessibilityHelper.setFontScale(scene, 1.4); // Any scale factor
```

## Key JavaFX Features Used

| Feature | Purpose | How Used |
|---------|---------|----------|
| **CSS Stylesheets** | Centralized styling | `stylesheets="@styles.css"` in FXML |
| **Style Classes** | Reusable styles | `.section-header`, `.status-bar`, etc. |
| **Pseudo-classes** | Interactive states | `:hover`, `:focused`, `:pressed` |
| **Font Scaling** | Programmatic size control | `setStyle("-fx-font-size: ...")` |
| **System Fonts** | Platform integration | `font-family: "System"` |

## Main Improvements

| Element | Improvement |
|---------|-------------|
| Text | 14px (was 13px), darker color (#2c2c2c) |
| Headers | 16px bold (was 16px normal) |
| Buttons | 8px padding, colored (Connect=blue, Disconnect=red) |
| Text Fields | White background, 14px font, 6px padding |
| Focus | Blue glow shadow (was barely visible) |
| Lists | 8px padding per cell (was cramped) |
| Code Areas | 13px monospace (was 11px) |

## Files Modified

- ✅ `styles.css` (NEW) - Main stylesheet
- ✅ `AccessibilityHelper.java` (NEW) - Utility class
- ✅ `McpClient.fxml` - Added stylesheet reference
- ✅ `McpClientController.java` - Added style classes
- ✅ `McpClientApp.java` - Added optional scaling

## How CSS Works in JavaFX

1. **Automatic Loading**: FXML `stylesheets="@styles.css"` loads the CSS
2. **Cascading**: Styles cascade from root to children
3. **Specificity**: ID selectors > Class selectors > Type selectors
4. **Inheritance**: Font properties inherit to children
5. **Override**: Inline styles override CSS (removed most inline styles)

## Standard JavaFX CSS Properties

Most commonly used for readability:

```css
-fx-font-size: 14px;              /* Text size */
-fx-font-family: "System";        /* Font face */
-fx-font-weight: bold;            /* Weight */
-fx-text-fill: #2c2c2c;           /* Text color */
-fx-background-color: white;      /* Background */
-fx-border-color: #c0c0c0;        /* Border */
-fx-padding: 8px 16px;            /* Spacing */
-fx-effect: dropshadow(...);      /* Visual effects */
```

## Troubleshooting

**CSS not loading?**
- Check file is in `src/main/resources/org/peacetalk/jmcp/client/`
- Verify path in FXML: `stylesheets="@styles.css"`
- Rebuild project: `mvn clean compile`

**Fonts too small/large?**
- Adjust base font in CSS: `.root { -fx-font-size: 15px; }`
- Or use AccessibilityHelper scaling
- System DPI is automatically respected

**Colors not contrasting enough?**
- Enable high contrast: `AccessibilityHelper.enableHighContrastMode(scene)`
- Or adjust colors in CSS `.root.high-contrast` section

## Next Steps

To further customize:

1. Edit `styles.css` to change colors/sizes
2. Add new style classes for custom components
3. Create additional theme CSS files (dark mode, etc.)
4. Add user preference saving/loading
5. Implement theme switcher UI

## Learn More

- JavaFX CSS docs: https://openjfx.io/javadoc/17/javafx.graphics/javafx/scene/doc-files/cssref.html
- WCAG contrast: https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html


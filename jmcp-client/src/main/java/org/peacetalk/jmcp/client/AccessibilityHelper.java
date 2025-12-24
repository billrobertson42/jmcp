package org.peacetalk.jmcp.client;

import javafx.scene.Scene;
import javafx.scene.text.Font;

/**
 * Utility class for improving UI accessibility and readability.
 *
 * JavaFX provides several standard mechanisms for better readability:
 * 1. CSS styling (implemented in styles.css)
 * 2. Font scaling
 * 3. High contrast modes
 * 4. System font preferences
 */
public class AccessibilityHelper {

    /**
     * Apply larger font scale to the entire scene.
     * This multiplies all font sizes by the given factor.
     *
     * @param scene The JavaFX scene
     * @param scaleFactor Font scale factor (e.g., 1.2 for 20% larger, 1.5 for 50% larger)
     */
    public static void setFontScale(Scene scene, double scaleFactor) {
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().setStyle("-fx-font-size: " + (14 * scaleFactor) + "px;");
        }
    }

    /**
     * Enable high contrast mode by adding the "high-contrast" style class.
     * This activates the high contrast CSS rules defined in styles.css.
     *
     * @param scene The JavaFX scene
     */
    public static void enableHighContrastMode(Scene scene) {
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().getStyleClass().add("high-contrast");
        }
    }

    /**
     * Disable high contrast mode.
     *
     * @param scene The JavaFX scene
     */
    public static void disableHighContrastMode(Scene scene) {
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().getStyleClass().remove("high-contrast");
        }
    }

    /**
     * Get recommended font scale based on system settings.
     * This is a placeholder - JavaFX doesn't have direct API for system DPI,
     * but you can use system properties or user preferences.
     *
     * @return Recommended scale factor (1.0 = normal, 1.25 = 125%, etc.)
     */
    public static double getRecommendedFontScale() {
        // Could check system properties or user preferences here
        // For now, return default
        return 1.0;
    }

    /**
     * Apply accessibility preset: Large Text
     * - 125% font size
     * - Better spacing
     */
    public static void applyLargeTextPreset(Scene scene) {
        setFontScale(scene, 1.25);
    }

    /**
     * Apply accessibility preset: Extra Large Text
     * - 150% font size
     * - High contrast
     */
    public static void applyExtraLargeTextPreset(Scene scene) {
        setFontScale(scene, 1.5);
        enableHighContrastMode(scene);
    }
}


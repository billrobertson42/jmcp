package org.peacetalk.jmcp.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main JavaFX application for the MCP Client
 */
public class McpClientApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("McpClient.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);

        // Optional: Apply larger font scale for better readability
        // Uncomment one of these lines to enable:
        // AccessibilityHelper.applyLargeTextPreset(scene);      // 25% larger fonts
        // AccessibilityHelper.applyExtraLargeTextPreset(scene); // 50% larger fonts + high contrast
        // AccessibilityHelper.setFontScale(scene, 1.3);         // Custom scale factor

        primaryStage.setTitle("MCP Client - JDBC MCP Server");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Cleanup on close
        primaryStage.setOnCloseRequest(event -> {
            McpClientController controller = loader.getController();
            if (controller != null) {
                controller.cleanup();
            }
            // Ensure JavaFX exits completely
            Platform.exit();
            // Force exit if Platform.exit() doesn't work (fallback)
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}


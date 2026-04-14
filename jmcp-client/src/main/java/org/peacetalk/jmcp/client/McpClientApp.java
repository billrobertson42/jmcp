/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
         AccessibilityHelper.applyLargeTextPreset(scene);      // 25% larger fonts
        // AccessibilityHelper.applyExtraLargeTextPreset(scene); // 50% larger fonts + high contrast
        // AccessibilityHelper.setFontScale(scene, 1.3);         // Custom scale factor

        primaryStage.setTitle("MCP Client - JDBC MCP Server");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Get controller and setup preferences handling
        McpClientController controller = loader.getController();
        if (controller != null) {
            controller.setupPreferences();
        }

        // Cleanup on close
        primaryStage.setOnCloseRequest(event -> {
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


package org.peacetalk.jmcp.client;

import tools.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.peacetalk.jmcp.client.service.CommunicationLogger;
import org.peacetalk.jmcp.client.service.McpService;
import org.peacetalk.jmcp.client.ui.ToolArgumentFormBuilder;
import org.peacetalk.jmcp.client.ui.ToolListCell;
import org.peacetalk.jmcp.client.ui.ValueParser;
import org.peacetalk.jmcp.core.model.CallToolResult;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.model.Tool;

import java.util.List;
import java.util.Map;

/**
 * Controller for the MCP Client GUI.
 * Coordinates between UI components and services.
 */
public class McpClientController {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // UI Components
    @FXML private TextField serverCommandField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Label statusLabel;
    @FXML private ListView<Tool> toolsList;
    @FXML private TextArea toolDescriptionArea;
    @FXML private TextArea toolSchemaArea;
    @FXML private VBox argumentsBox;
    @FXML private Button executeButton;
    @FXML private TextArea resultArea;
    @FXML private TextArea communicationLogArea;
    @FXML private Label workingDirectoryLabel;

    // Services and Helpers
    private final McpService mcpService = new McpService();
    private final CommunicationLogger communicationLogger = new CommunicationLogger();
    private final ToolArgumentFormBuilder formBuilder = new ToolArgumentFormBuilder();
    private final ValueParser valueParser = new ValueParser();
    private final ClientPreferences preferences = new ClientPreferences();

    // State
    private Tool selectedTool;
    private Map<String, TextField> argumentFields;

    @FXML
    public void initialize() {
        // Setup tool list cell factory
        toolsList.setCellFactory(listView -> new ToolListCell());
    }

    /**
     * Setup preferences - load saved server command
     */
    public void setupPreferences() {
        preferences.getServerCommand().ifPresent(s -> serverCommandField.setText(s));
    }

    @FXML
    private void onConnect() {
        String command = serverCommandField.getText().trim();
        if (command.isEmpty()) {
            showError("Please enter a server command");
            return;
        }

        String[] commandParts = command.split("\\s+");

        connectButton.setDisable(true);
        statusLabel.setText("Connecting...");

        // Run in background thread (daemon so it doesn't prevent exit)
        Thread connectThread = new Thread(() -> {
            try {
                // Set up communication listener
                CommunicationListener listener = new CommunicationListener() {
                    @Override
                    public void onRequestSent(JsonRpcRequest request) {
                        communicationLogger.logRequest(request);
                        updateCommunicationLog();
                    }

                    @Override
                    public void onResponseReceived(JsonRpcResponse response) {
                        communicationLogger.logResponse(response);
                        updateCommunicationLog();
                    }

                    @Override
                    public void onError(String message, Exception exception) {
                        communicationLogger.logError(message, exception);
                        updateCommunicationLog();
                    }
                };

                // Connect to server
                mcpService.connect(commandParts, listener);

                // Load and sort tools
                List<Tool> tools = mcpService.listTools();
                List<Tool> sortedTools = mcpService.sortTools(tools);

                Platform.runLater(() -> {
                    toolsList.setItems(FXCollections.observableArrayList(sortedTools));
                    statusLabel.setText("Connected: " + mcpService.getServerInfo().serverInfo().name() +
                            " v" + mcpService.getServerInfo().serverInfo().version());
                    updateConnectionState(true);

                    // Save command after successful connection
                    preferences.setServerCommand(command);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Connection failed: " + e.getMessage());
                    statusLabel.setText("Disconnected");
                    updateConnectionState(false);
                });
                communicationLogger.logError("Connection failed", e);
                updateCommunicationLog();
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    private void onDisconnect() {
        mcpService.disconnect();

        toolsList.getItems().clear();
        toolDescriptionArea.clear();
        toolSchemaArea.clear();
        formBuilder.clearForm(argumentsBox);
        argumentFields = null;
        resultArea.clear();
        communicationLogger.clear();
        communicationLogArea.clear();

        statusLabel.setText("Disconnected");
        updateConnectionState(false);
    }

    /**
     * Cleanup when application closes
     */
    public void cleanup() {
        mcpService.cleanup();
    }

    private void onToolSelected(Tool tool) {
        selectedTool = tool;

        // Update description
        toolDescriptionArea.setText(tool.description());

        // Update schema
        try {
            String prettySchema = MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tool.inputSchema());
            toolSchemaArea.setText(prettySchema);
        } catch (Exception e) {
            toolSchemaArea.setText("Error displaying schema: " + e.getMessage());
        }

        // Build argument input fields
        argumentFields = formBuilder.buildForm(tool, argumentsBox);

        executeButton.setDisable(false);
    }

    @FXML
    private void onExecute() {
        if (selectedTool == null) {
            showError("Please select a tool");
            return;
        }

        executeButton.setDisable(true);
        resultArea.setText("Executing...");

        // Collect arguments
        Map<String, Object> arguments = formBuilder.collectArguments(argumentFields, valueParser);

        // Execute in background (daemon thread)
        Thread executeThread = new Thread(() -> {
            try {
                CallToolResult result = mcpService.executeTool(selectedTool.name(), arguments);

                // Pretty print result
                String prettyResult = MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(result);

                Platform.runLater(() -> {
                    resultArea.setText(prettyResult);
                    executeButton.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.setText("Error: " + e.getMessage());
                    executeButton.setDisable(false);
                });
            }
        });
        executeThread.setDaemon(true);
        executeThread.start();
    }

    private void updateConnectionState(boolean connected) {
        connectButton.setDisable(connected);
        disconnectButton.setDisable(!connected);
        serverCommandField.setDisable(connected);
        toolsList.setDisable(!connected);
        executeButton.setDisable(!connected);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Update the communication log display
     */
    private void updateCommunicationLog() {
        Platform.runLater(() -> {
            communicationLogArea.setText(communicationLogger.getFormattedLog());
        });
    }
}


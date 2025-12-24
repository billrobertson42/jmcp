package org.peacetalk.jmcp.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.peacetalk.jmcp.core.model.CallToolResult;
import org.peacetalk.jmcp.core.model.ListToolsResult;
import org.peacetalk.jmcp.core.model.Tool;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the MCP Client GUI
 */
public class McpClientController {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private McpClient client;
    private Tool selectedTool;
    private final Map<String, TextField> argumentFields = new HashMap<>();

    @FXML
    public void initialize() {
        // Set default command
        serverCommandField.setText("./run.sh");

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

        // Setup tool list selection
        toolsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                onToolSelected(newVal);
            }
        });

        // Set working directory in status bar
        String workingDir = System.getProperty("user.dir");
        workingDirectoryLabel.setText("Working Directory: " + workingDir);

        // Initially disabled
        updateConnectionState(false);
    }

    @FXML
    private void onConnect() {
        String command = serverCommandField.getText().trim();
        if (command.isEmpty()) {
            showError("Please enter a server command");
            return;
        }

        // Parse command (simple split on spaces)
        String[] commandParts = command.split("\\s+");

        connectButton.setDisable(true);
        statusLabel.setText("Connecting...");

        // Run in background thread (daemon so it doesn't prevent exit)
        Thread connectThread = new Thread(() -> {
            try {
                client = new McpClient(commandParts);

                // Set up communication logging
                client.addCommunicationListener(new CommunicationListener() {
                    @Override
                    public void onRequestSent(org.peacetalk.jmcp.core.model.JsonRpcRequest request) {
                        logCommunication("SENT", request.method(), request);
                    }

                    @Override
                    public void onResponseReceived(org.peacetalk.jmcp.core.model.JsonRpcResponse response) {
                        logCommunication("RECEIVED", "Response", response);
                    }

                    @Override
                    public void onError(String message, Exception exception) {
                        logError(message, exception);
                    }
                });

                client.connect();

                // Load tools
                ListToolsResult toolsResult = client.listTools();

                Platform.runLater(() -> {
                    // Sort tools alphabetically by name before displaying
                    var sortedTools = toolsResult.tools().stream()
                            .sorted((t1, t2) -> t1.name().compareToIgnoreCase(t2.name()))
                            .toList();

                    toolsList.setItems(FXCollections.observableArrayList(sortedTools));
                    statusLabel.setText("Connected: " + client.getServerInfo().serverInfo().name() +
                                      " v" + client.getServerInfo().serverInfo().version());
                    updateConnectionState(true);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Connection failed: " + e.getMessage());
                    statusLabel.setText("Disconnected");
                    updateConnectionState(false);
                });
                logError("Connection failed", e);
            }
        });
        connectThread.setDaemon(true); // Make daemon so it won't prevent exit
        connectThread.start();
    }

    @FXML
    private void onDisconnect() {
        if (client != null) {
            client.close();
            client = null;
        }

        toolsList.getItems().clear();
        toolDescriptionArea.clear();
        toolSchemaArea.clear();
        argumentsBox.getChildren().clear();
        argumentFields.clear();
        resultArea.clear();
        communicationLogArea.clear();

        statusLabel.setText("Disconnected");
        updateConnectionState(false);
    }

    /**
     * Cleanup method called when the application is closing.
     * Ensures all resources are properly released.
     */
    public void cleanup() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore exceptions during cleanup
                System.err.println("Error during cleanup: " + e.getMessage());
            }
            client = null;
        }
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
        buildArgumentFields(tool);

        executeButton.setDisable(false);
    }

    private void buildArgumentFields(Tool tool) {
        argumentsBox.getChildren().clear();
        argumentFields.clear();

        // Parse schema to build input fields
        JsonNode schema = tool.inputSchema();

        if (schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            JsonNode requiredFields = schema.has("required") ? schema.get("required") : null;

            for (Map.Entry<String, JsonNode> entry : properties.properties()) {
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();

                boolean isRequired = requiredFields != null &&
                                   requiredFields.toString().contains(fieldName);

                // Create label with style class
                Label label = new Label(fieldName + (isRequired ? " *" : ""));
                label.getStyleClass().add("field-label");

                // Create text field
                TextField textField = new TextField();
                textField.setPromptText(getPromptText(fieldSchema));

                argumentFields.put(fieldName, textField);

                argumentsBox.getChildren().addAll(label, textField);
            }
        }

        if (argumentFields.isEmpty()) {
            Label noArgs = new Label("No arguments required");
            argumentsBox.getChildren().add(noArgs);
        }
    }

    private String getPromptText(tools.jackson.databind.JsonNode fieldSchema) {
        if (fieldSchema.has("type")) {
            String type = fieldSchema.get("type").asString();
            if (fieldSchema.has("description")) {
                return fieldSchema.get("description").asString();
            }
            return "Enter " + type;
        }
        return "";
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
        Map<String, Object> arguments = new HashMap<>();
        for (Map.Entry<String, TextField> entry : argumentFields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (!value.isEmpty()) {
                // Try to parse as appropriate type
                arguments.put(entry.getKey(), parseValue(value));
            }
        }

        // Execute in background (daemon thread)
        Thread executeThread = new Thread(() -> {
            try {
                CallToolResult result = client.callTool(selectedTool.name(), arguments);

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
        executeThread.setDaemon(true); // Make daemon so it won't prevent exit
        executeThread.start();
    }

    private Object parseValue(String value) {
        // Try to parse as number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // Not a number
        }

        // Try to parse as boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        // Try to parse as JSON array (simple check)
        if (value.startsWith("[") && value.endsWith("]")) {
            try {
                return MAPPER.readValue(value, Object.class);
            } catch (Exception e) {
                // Not valid JSON, treat as string
            }
        }

        // Default to string
        return value;
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
     * Log a message to the communication log with formatting
     */
    private void logCommunication(String direction, String type, Object content) {
        Platform.runLater(() -> {
            StringBuilder log = new StringBuilder();

            // Add separator line
            log.append("=".repeat(80)).append("\n");

            // Add header with direction and type
            log.append(String.format(">>> %s %s <<<", direction, type)).append("\n");
            log.append("=".repeat(80)).append("\n");

            // Add pretty-printed JSON content
            try {
                String prettyJson = MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(content);
                log.append(prettyJson).append("\n");
            } catch (Exception e) {
                log.append(content.toString()).append("\n");
            }

            log.append("\n");

            // Append to log area
            communicationLogArea.appendText(log.toString());
        });
    }

    /**
     * Log an error to the communication log
     */
    private void logError(String message, Exception exception) {
        Platform.runLater(() -> {
            StringBuilder log = new StringBuilder();

            log.append("!".repeat(80)).append("\n");
            log.append(">>> ERROR <<<").append("\n");
            log.append("!".repeat(80)).append("\n");
            log.append(message).append("\n");
            if (exception != null) {
                log.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
                log.append("Message: ").append(exception.getMessage()).append("\n");
            }
            log.append("\n");

            communicationLogArea.appendText(log.toString());
        });
    }
}


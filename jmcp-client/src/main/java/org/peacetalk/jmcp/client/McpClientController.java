package org.peacetalk.jmcp.client;

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
import tools.jackson.databind.ObjectMapper;

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
    @FXML private ComboBox<Tool> toolsComboBox;
    @FXML private TextArea toolDescriptionArea;
    @FXML private VBox argumentsBox;
    @FXML private Button executeButton;
    @FXML private TextArea resultArea;
    @FXML private TextArea communicationLogArea;
    @FXML private TextArea serverStderrArea;
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
        // Setup tool ComboBox with custom cell factory for display
        toolsComboBox.setButtonCell(new ToolListCell());
        toolsComboBox.setCellFactory(listView -> new ToolListCell());

        // Setup tool selection listener
        toolsComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
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
                        String logEntry = communicationLogger.formatRequest(request);
                        appendToCommunicationLog(logEntry);
                    }

                    @Override
                    public void onResponseReceived(JsonRpcResponse response) {
                        String logEntry = communicationLogger.formatResponse(response);
                        appendToCommunicationLog(logEntry);
                    }

                    @Override
                    public void onError(String message, Exception exception) {
                        String logEntry = communicationLogger.formatError(message, exception);
                        appendToCommunicationLog(logEntry);
                    }

                    @Override
                    public void onServerStderr(String line) {
                        appendToServerStderr(line);
                    }
                };

                // Connect to server
                mcpService.connect(commandParts, listener);

                // Load and sort tools
                List<Tool> tools = mcpService.listTools();
                List<Tool> sortedTools = mcpService.sortTools(tools);

                Platform.runLater(() -> {
                    toolsComboBox.setItems(FXCollections.observableArrayList(sortedTools));
                    statusLabel.setText("Connected: " + mcpService.getServerInfo().serverInfo().name() +
                            " v" + mcpService.getServerInfo().serverInfo().version());
                    updateConnectionState(true);

                    // Save command after successful connection
                    preferences.setServerCommand(command);
                });

            } catch (Exception e) {
                System.err.println("Connection failed: " + e.getMessage());
                e.printStackTrace(System.err);
                Platform.runLater(() -> {
                    showError("Connection failed: " + e.getMessage());
                    statusLabel.setText("Disconnected");
                    updateConnectionState(false);
                });
                String logEntry = communicationLogger.formatError("Connection failed", e);
                appendToCommunicationLog(logEntry);
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    private void onDisconnect() {
        // Immediately disable all interactive controls to prevent double-clicks
        disconnectButton.setDisable(true);
        connectButton.setDisable(true);
        serverCommandField.setDisable(true);
        toolsComboBox.setDisable(true);
        executeButton.setDisable(true);

        statusLabel.setText("Disconnecting...");

        // Run disconnect in background thread to avoid blocking UI
        Thread disconnectThread = new Thread(() -> {
            try {
                mcpService.disconnect();
            } catch (Exception e) {
                System.err.println("Error during disconnect: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                // ALWAYS update UI, even if disconnect failed
                cleanupAndResetUI();
            }
        });
        disconnectThread.setDaemon(true);
        disconnectThread.start();

        // Safety mechanism: Force UI reset after 5 seconds even if disconnect is still running
        // This prevents permanent UI freeze if disconnect hangs
        Thread timeoutThread = new Thread(() -> {
            try {
                // Wait for disconnect thread to finish, but max 5 seconds
                disconnectThread.join(5000);

                // If thread is still alive after 5 seconds, force UI reset anyway
                if (disconnectThread.isAlive()) {
                    System.err.println("WARNING: Disconnect operation timed out after 5 seconds - forcing UI reset");
                    cleanupAndResetUI();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    /**
     * Clean up all UI state and reset to disconnected state.
     * This is called from background threads, so uses Platform.runLater().
     */
    private void cleanupAndResetUI() {
        Platform.runLater(() -> {
            // Clear all UI state
            toolsComboBox.getItems().clear();
            toolsComboBox.getSelectionModel().clearSelection();
            toolDescriptionArea.clear();
            formBuilder.clearForm(argumentsBox);
            argumentFields = null;
            selectedTool = null;  // Clear selected tool reference
            resultArea.clear();
            communicationLogArea.clear();
            serverStderrArea.clear();

            statusLabel.setText("Disconnected");
            updateConnectionState(false);  // This will properly set button states
        });
    }

    /**
     * Cleanup when application closes.
     * Runs in background with timeout to avoid blocking app exit.
     */
    public void cleanup() {
        // Run cleanup in background thread with timeout
        Thread cleanupThread = new Thread(() -> {
            try {
                mcpService.cleanup();
            } catch (Exception e) {
                System.err.println("Error during cleanup: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();

        // Give it 2 seconds max, then exit anyway
        try {
            cleanupThread.join(2000);
            if (cleanupThread.isAlive()) {
                System.err.println("WARNING: Cleanup timed out after 2 seconds - forcing exit");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void onToolSelected(Tool tool) {
        selectedTool = tool;

        // Update description with schema appended
        StringBuilder fullDescription = new StringBuilder();
        fullDescription.append(tool.description());

        // Append schema information
        try {
            String prettySchema = MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tool.inputSchema());
            fullDescription.append("\n\n--- Input Schema ---\n");
            fullDescription.append(prettySchema);
        } catch (Exception e) {
            System.err.println("Error displaying schema for tool " + tool.name() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            fullDescription.append("\n\nError displaying schema: ").append(e.getMessage());
        }

        toolDescriptionArea.setText(fullDescription.toString());

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
                System.err.println("Error executing tool: " + e.getMessage());
                e.printStackTrace(System.err);
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
        toolsComboBox.setDisable(!connected);
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
     * Append a log entry to the communication log display
     */
    private void appendToCommunicationLog(String logEntry) {
        Platform.runLater(() -> {
            communicationLogArea.appendText(logEntry);
            // Auto-scroll to bottom
            communicationLogArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * Append a line to the server stderr display
     */
    private void appendToServerStderr(String line) {
        Platform.runLater(() -> {
            serverStderrArea.appendText(line + "\n");
            // Auto-scroll to bottom
            serverStderrArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}


package org.peacetalk.jmcp.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.peacetalk.jmcp.client.service.CommunicationLogger;
import org.peacetalk.jmcp.client.service.McpService;
import org.peacetalk.jmcp.client.ui.NavigableResourceView;
import org.peacetalk.jmcp.client.ui.ResourceListCell;
import org.peacetalk.jmcp.client.ui.ResourceNavigationHistory;
import org.peacetalk.jmcp.client.ui.ToolArgumentFormBuilder;
import org.peacetalk.jmcp.client.ui.ToolListCell;
import org.peacetalk.jmcp.client.ui.ValueParser;
import org.peacetalk.jmcp.core.model.CallToolResult;
import org.peacetalk.jmcp.core.model.Content;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.model.ListResourcesResult;
import org.peacetalk.jmcp.core.model.ReadResourceResult;
import org.peacetalk.jmcp.core.model.ResourceContents;
import org.peacetalk.jmcp.core.model.ResourceDescriptor;
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

    // UI Components - Tools
    @FXML private TextField serverCommandField;
    @FXML private Button connectButton;
    @FXML private Button pingButton;
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
    @FXML private CheckBox decodeJsonCheckBox;
    @FXML private TabPane leftTabPane;

    // UI Components - Resources
    @FXML private ComboBox<ResourceDescriptor> resourcesComboBox;
    @FXML private Button readResourceButton;
    @FXML private VBox resourceDetailsBox;
    @FXML private TextField resourceUriField;
    @FXML private StackPane resultStackPane;
    @FXML private VBox resourceResultView;
    @FXML private Button backButton;
    @FXML private Label resourceBreadcrumb;
    @FXML private ScrollPane resourceResultScrollPane;

    // Services and Helpers
    private final McpService mcpService = new McpService();
    private final CommunicationLogger communicationLogger = new CommunicationLogger();
    private final ToolArgumentFormBuilder formBuilder = new ToolArgumentFormBuilder();
    private final ValueParser valueParser = new ValueParser();
    private final ClientPreferences preferences = new ClientPreferences();

    // Resource navigation
    private final ResourceNavigationHistory resourceHistory = new ResourceNavigationHistory();
    private NavigableResourceView navigableResourceView;

    // State
    private Tool selectedTool;
    private Map<String, TextField> argumentFields;
    private ResourceDescriptor selectedResource;

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

        // Setup resource ComboBox with custom cell factory for display
        resourcesComboBox.setButtonCell(new ResourceListCell());
        resourcesComboBox.setCellFactory(listView -> new ResourceListCell());

        // Setup resource selection listener
        resourcesComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                onResourceSelected(newVal);
            }
        });

        // Setup navigable resource view
        navigableResourceView = new NavigableResourceView();
        navigableResourceView.setOnNavigate(this::navigateToResource);
        resourceResultScrollPane.setContent(navigableResourceView);

        // Set the font from resultArea after the scene is ready (CSS applied)
        Platform.runLater(() -> {
            navigableResourceView.setFont(resultArea.getFont());
        });

        // Initially back button is disabled
        updateBackButtonState();

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

        // Set working directory in status bar
        String workingDir = System.getProperty("user.dir");
        workingDirectoryLabel.setText("Working Directory: " + workingDir);

        // Initially disabled (start with tool view)
        updateConnectionState(false);
        showToolView();
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

                // Load and sort resources
                List<ResourceDescriptor> resources = List.of();
                try {
                    ListResourcesResult resourcesResult = mcpService.listResources();
                    resources = mcpService.sortResources(resourcesResult.resources());
                } catch (Exception e) {
                    System.err.println("Resources not available: " + e.getMessage());
                    // Resources are optional - continue without them
                }
                final List<ResourceDescriptor> finalResources = resources;

                Platform.runLater(() -> {
                    toolsComboBox.setItems(FXCollections.observableArrayList(sortedTools));
                    resourcesComboBox.setItems(FXCollections.observableArrayList(finalResources));
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
        pingButton.setDisable(true);
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
            // Clear all UI state - Tools
            toolsComboBox.getItems().clear();
            toolsComboBox.getSelectionModel().clearSelection();
            toolDescriptionArea.clear();
            formBuilder.clearForm(argumentsBox);
            argumentFields = null;
            selectedTool = null;  // Clear selected tool reference

            // Clear all UI state - Resources
            resourcesComboBox.getItems().clear();
            resourcesComboBox.getSelectionModel().clearSelection();
            resourceUriField.clear();
            resourceDetailsBox.getChildren().clear();
            resourceDetailsBox.getChildren().add(new Label("Select a resource to see details"));
            selectedResource = null;

            // Clear resource navigation
            resourceHistory.clear();
            navigableResourceView.clear();
            resourceBreadcrumb.setText("");
            updateBackButtonState();

            // Clear output areas
            resultArea.clear();
            communicationLogArea.clear();
            serverStderrArea.clear();

            statusLabel.setText("Disconnected");
            updateConnectionState(false);  // This will properly set button states
        });
    }

    @FXML
    private void onPing() {
        // Disable ping button during ping to prevent rapid clicks
        pingButton.setDisable(true);
        statusLabel.setText("Pinging server...");

        // Run ping in background thread
        Thread pingThread = new Thread(() -> {
            try {
                boolean success = mcpService.ping();

                Platform.runLater(() -> {
                    if (success) {
                        statusLabel.setText("Connected - Ping successful");
                        // Show brief success message in result area
                        resultArea.setText("✓ Server responded to ping");
                    } else {
                        statusLabel.setText("Connected - Ping failed");
                        showError("Server did not respond to ping");
                    }
                    pingButton.setDisable(false);
                });

            } catch (Exception e) {
                System.err.println("Ping error: " + e.getMessage());
                e.printStackTrace(System.err);
                Platform.runLater(() -> {
                    statusLabel.setText("Connected - Ping error");
                    showError("Ping failed: " + e.getMessage());
                    pingButton.setDisable(false);
                });
            }
        });
        pingThread.setDaemon(true);
        pingThread.start();
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

        // Build argument input fields with Enter key action
        argumentFields = formBuilder.buildForm(tool, argumentsBox, this::onExecute);

        executeButton.setDisable(false);
    }

    private void onResourceSelected(ResourceDescriptor resource) {
        selectedResource = resource;

        // Update URI field
        resourceUriField.setText(resource.uri());

        // Update details
        resourceDetailsBox.getChildren().clear();
        resourceDetailsBox.getChildren().add(new Label("Name: " + resource.name()));
        if (resource.description() != null && !resource.description().isEmpty()) {
            Label descLabel = new Label("Description: " + resource.description());
            descLabel.setWrapText(true);
            resourceDetailsBox.getChildren().add(descLabel);
        }
        if (resource.mimeType() != null) {
            resourceDetailsBox.getChildren().add(new Label("MIME Type: " + resource.mimeType()));
        }

        readResourceButton.setDisable(false);
    }

    @FXML
    private void onReadResource() {
        if (selectedResource == null) {
            showError("Please select a resource");
            return;
        }

        navigateToResource(selectedResource.uri());
    }

    /**
     * Navigate to a resource URI and display the result.
     * This is called both from the Read Resource button and from clicking links.
     */
    private void navigateToResource(String uri) {
        readResourceButton.setDisable(true);
        navigableResourceView.clear();
        resourceBreadcrumb.setText("Loading: " + uri);

        // Execute in background (daemon thread)
        Thread readThread = new Thread(() -> {
            try {
                ReadResourceResult result = mcpService.readResource(uri);

                // Format the result for display
                String displayContent = formatResourceResult(result);

                Platform.runLater(() -> {
                    // Add to history and display
                    resourceHistory.navigateTo(uri, displayContent);
                    displayResourceContent(uri, displayContent);
                    updateBackButtonState();
                    readResourceButton.setDisable(false);
                });

            } catch (Exception e) {
                System.err.println("Resource read failed: " + e.getMessage());
                e.printStackTrace(System.err);
                Platform.runLater(() -> {
                    showError("Resource read failed: " + e.getMessage());
                    navigableResourceView.setContent("Error: " + e.getMessage());
                    resourceBreadcrumb.setText("Error loading: " + uri);
                    readResourceButton.setDisable(false);
                });
            }
        });
        readThread.setDaemon(true);
        readThread.start();
    }

    /**
     * Format a resource result for display, applying JSON decoding if enabled.
     */
    private String formatResourceResult(ReadResourceResult result) throws Exception {
        if (result == null || result.contents() == null || result.contents().isEmpty()) {
            return "{}";
        }

        // Apply JSON decoding if checkbox is selected
        if (decodeJsonCheckBox.isSelected()) {
            Object decoded = decodeJsonInResourceResult(result);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decoded);
        } else {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        }
    }

    /**
     * Decode JSON strings in text fields of ReadResourceResult for display purposes.
     */
    private Object decodeJsonInResourceResult(ReadResourceResult result) {
        if (result == null || result.contents() == null) {
            return result;
        }

        List<Object> transformedContents = result.contents().stream()
            .map(this::decodeJsonInResourceContents)
            .toList();

        // Return a simple map for display
        return Map.of("contents", transformedContents);
    }

    /**
     * Decode JSON in a ResourceContents object.
     */
    private Object decodeJsonInResourceContents(ResourceContents contents) {
        if (contents == null || contents.text() == null) {
            return contents;
        }

        String text = contents.text().trim();

        // Check if text looks like JSON
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return contents;
        }

        try {
            Object decoded = MAPPER.readValue(text, Object.class);
            // Return a map with decoded text
            return Map.of(
                "uri", contents.uri(),
                "mimeType", contents.mimeType() != null ? contents.mimeType() : "application/json",
                "text", decoded
            );
        } catch (Exception e) {
            return contents;
        }
    }

    /**
     * Display resource content in the navigable view.
     */
    private void displayResourceContent(String uri, String content) {
        navigableResourceView.setContent(content);
        resourceBreadcrumb.setText(uri + " (History: " + resourceHistory.historyDepth() + ")");
    }

    /**
     * Handle back button click for resource navigation.
     */
    @FXML
    private void onResourceBack() {
        resourceHistory.goBack().ifPresent(entry -> {
            displayResourceContent(entry.uri(), entry.displayContent());
            updateBackButtonState();
        });
    }

    /**
     * Update the back button enabled state based on history.
     */
    private void updateBackButtonState() {
        backButton.setDisable(!resourceHistory.canGoBack());
    }

    /**
     * Switch the result display to show the tool result view.
     */
    private void showToolView() {
        resultArea.setVisible(true);
        resultArea.setManaged(true);
        resourceResultView.setVisible(false);
        resourceResultView.setManaged(false);
    }

    /**
     * Switch the result display to show the resource result view.
     */
    private void showResourceView() {
        resultArea.setVisible(false);
        resultArea.setManaged(false);
        resourceResultView.setVisible(true);
        resourceResultView.setManaged(true);
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

                // Apply JSON decoding transformation if enabled
                Object displayResult = decodeJsonCheckBox.isSelected()
                    ? decodeJsonInResult(result)
                    : result;

                // Pretty print result
                String prettyResult = MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(displayResult);

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
        pingButton.setDisable(!connected);
        disconnectButton.setDisable(!connected);
        serverCommandField.setDisable(connected);
        toolsComboBox.setDisable(!connected);
        executeButton.setDisable(!connected);
        resourcesComboBox.setDisable(!connected);
        readResourceButton.setDisable(!connected);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Decode JSON strings in text fields of CallToolResult for display purposes.
     * This transforms "text": "{\"foo\":\"bar\"}" into a decoded_text field with the parsed JSON.
     * The transformation only affects display - the original result is unchanged.
     */
    private Object decodeJsonInResult(CallToolResult result) {
        if (result == null || result.content() == null) {
            return result;
        }

        List<Object> transformedContent = result.content().stream()
            .map(this::decodeJsonInContent)
            .toList();

        // Return a display wrapper instead of a real CallToolResult
        return new DisplayResult(transformedContent, result.isError());
    }

    /**
     * Transform a Content object by decoding JSON in text fields.
     * If the text field contains valid JSON, creates a display-friendly version
     * with the decoded JSON as an object.
     */
    private Object decodeJsonInContent(Content content) {
        if (content == null || !"text".equals(content.type()) || content.text() == null) {
            return content;
        }

        String text = content.text().trim();

        // Check if text looks like JSON (starts with { or [)
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return content;
        }

        try {
            // Try to parse as JSON
            Object decoded = MAPPER.readValue(text, Object.class);

            // Return a wrapper that will serialize with the decoded object
            return new DisplayContent("text", decoded);

        } catch (Exception e) {
            // Not valid JSON or parsing failed - return original content
            return content;
        }
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


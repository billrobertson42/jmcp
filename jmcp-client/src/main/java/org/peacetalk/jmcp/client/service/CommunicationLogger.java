package org.peacetalk.jmcp.client.service;

import tools.jackson.databind.ObjectMapper;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;

/**
 * Formats and logs communication events between client and server.
 * Produces formatted log entries for requests, responses, and errors.
 */
public class CommunicationLogger {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final StringBuilder logBuffer = new StringBuilder();

    /**
     * Log a JSON-RPC request being sent.
     *
     * @param request The request being sent
     */
    public void logRequest(JsonRpcRequest request) {
        logCommunication("SENT", request.method(), request);
    }

    /**
     * Log a JSON-RPC response being received.
     *
     * @param response The response received
     */
    public void logResponse(JsonRpcResponse response) {
        logCommunication("RECEIVED", "Response", response);
    }

    /**
     * Log an error that occurred during communication.
     *
     * @param message Error message
     * @param exception The exception that occurred (can be null)
     */
    public void logError(String message, Exception exception) {
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

        logBuffer.append(log);
    }

    /**
     * Get the complete formatted log.
     *
     * @return The formatted log as a string
     */
    public String getFormattedLog() {
        return logBuffer.toString();
    }

    /**
     * Clear all logged entries.
     */
    public void clear() {
        logBuffer.setLength(0);
    }

    /**
     * Internal method to format and log a communication event.
     */
    private void logCommunication(String direction, String type, Object content) {
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

        logBuffer.append(log);
    }
}


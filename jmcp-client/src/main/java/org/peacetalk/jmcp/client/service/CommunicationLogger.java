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

package org.peacetalk.jmcp.client.service;

import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Formats and logs communication events between client and server.
 * Produces formatted log entries for requests, responses, and errors.
 */
public class CommunicationLogger {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Format a JSON-RPC request being sent.
     *
     * @param request The request being sent
     * @return Formatted log entry for the request
     */
    public String formatRequest(JsonRpcRequest request) {
        return formatCommunication("SENT", request.method(), request);
    }

    /**
     * Format a JSON-RPC response being received.
     *
     * @param response The response received
     * @return Formatted log entry for the response
     */
    public String formatResponse(JsonRpcResponse response) {
        return formatCommunication("RECEIVED", "Response", response);
    }

    /**
     * Format an error that occurred during communication.
     *
     * @param message Error message
     * @param exception The exception that occurred (can be null)
     * @return Formatted log entry for the error
     */
    public String formatError(String message, Exception exception) {
        StringBuilder log = new StringBuilder();

        log.append("!".repeat(80)).append("\n");
        log.append(">>> ERROR <<<").append("\n");
        log.append("!".repeat(80)).append("\n");
        log.append(message).append("\n");
        if (exception != null) {
            log.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
            log.append("Message: ").append(exception.getMessage()).append("\n");
            log.append("\nStack Trace:\n");

            // Get full stack trace as string
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            log.append(sw.toString());
        }
        log.append("\n");

        return log.toString();
    }

    /**
     * Clear log (convenience method for UI).
     * Note: This returns a command to clear, caller is responsible for clearing display.
     *
     * @return Empty string signal
     */
    public String getClearCommand() {
        return "";
    }

    /**
     * Internal method to format a communication event.
     */
    private String formatCommunication(String direction, String type, Object content) {
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

        return log.toString();
    }
}




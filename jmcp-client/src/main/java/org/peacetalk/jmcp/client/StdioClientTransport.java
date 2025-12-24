package org.peacetalk.jmcp.client;

import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side transport for communicating with MCP servers via stdio.
 * Launches the server process and communicates via stdin/stdout.
 */
public class StdioClientTransport implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong REQUEST_ID = new AtomicLong(1);

    private Process serverProcess;
    private BufferedReader reader;
    private PrintWriter writer;
    private final String[] command;
    private final List<CommunicationListener> listeners = new CopyOnWriteArrayList<>();

    public StdioClientTransport(String[] command) {
        this.command = command;
    }

    /**
     * Add a listener to be notified of communication events
     */
    public void addListener(CommunicationListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a listener
     */
    public void removeListener(CommunicationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Start the server process and establish communication
     */
    public void connect() throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT); // Show server stderr in console

        serverProcess = builder.start();

        // Setup streams
        reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(serverProcess.getOutputStream()), true);
    }

    /**
     * Send a request and wait for response
     */
    public JsonRpcResponse sendRequest(String method, Object params) throws IOException {
        if (serverProcess == null || !serverProcess.isAlive()) {
            throw new IllegalStateException("Server process is not running");
        }

        // Create request
        JsonRpcRequest request = new JsonRpcRequest(
            "2.0",
            REQUEST_ID.getAndIncrement(),
            method,
            params
        );

        listeners.forEach(l -> notifyRequestSent(l, request));

        // Serialize and send
        String requestJson = MAPPER.writeValueAsString(request);
        writer.println(requestJson);
        writer.flush();

        // Read response
        String responseLine;
        try {
            responseLine = reader.readLine();
            if (responseLine == null) {
                IOException error = new IOException("Server closed connection");
                notifyError("Server closed connection", error);
                throw error;
            }
        } catch (IOException e) {
            notifyError("Error reading response", e);
            throw e;
        }

        // Parse response
        JsonRpcResponse response;
        try {
            response = MAPPER.readValue(responseLine, JsonRpcResponse.class);
        } catch (Exception e) {
            IOException parseError = new IOException("Failed to parse response: " + e.getMessage(), e);
            notifyError("Failed to parse response", parseError);
            throw parseError;
        }

        listeners.forEach(l -> notifyResponseReceived(l, response));
        return response;
    }

    private static void notifyResponseReceived(CommunicationListener listener, JsonRpcResponse response) {
        try {
            listener.onResponseReceived(response);
        } catch (Exception e) {
            // Don't let listener exceptions break the response handling
            System.err.println("Listener error on response received: " + e.getMessage());
        }
    }

    private static void notifyRequestSent(CommunicationListener listener, JsonRpcRequest request) {
        try {
            listener.onRequestSent(request);
        } catch (Exception e) {
            // Don't let listener exceptions break the request
            System.err.println("Listener error on request sent: " + e.getMessage());
        }
    }

    /**
     * Notify all listeners of an error
     */
    private void notifyError(String message, Exception exception) {
        for (CommunicationListener listener : listeners) {
            try {
                listener.onError(message, exception);
            } catch (Exception e) {
                // Don't let listener exceptions cause more problems
                System.err.println("Listener error on error notification: " + e.getMessage());
            }
        }
    }

    /**
     * Check if the server process is still running
     */
    public boolean isConnected() {
        return serverProcess != null && serverProcess.isAlive();
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.close();
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}


package org.peacetalk.jmcp.client;

import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import tools.jackson.core.exc.StreamReadException;
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
    private BufferedReader stderrReader;
    private PrintWriter writer;
    private Thread stderrReaderThread;
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
        // Capture stderr instead of inheriting it
        builder.redirectError(ProcessBuilder.Redirect.PIPE);

        serverProcess = builder.start();

        // Setup streams
        reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
        stderrReader = new BufferedReader(new InputStreamReader(serverProcess.getErrorStream()));
        writer = new PrintWriter(new OutputStreamWriter(serverProcess.getOutputStream()), true);

        // Start stderr reader thread
        startStderrReader();
    }

    /**
     * Start a background thread to read stderr from the server process
     */
    private void startStderrReader() {
        stderrReaderThread = new Thread(() -> {
            try {
                String line;
                while ((line = stderrReader.readLine()) != null) {
                    String stderrLine = line;
                    listeners.forEach(l -> notifyStderr(l, stderrLine));
                }
            } catch (IOException e) {
                // Server process ended or stream closed
                if (serverProcess != null && serverProcess.isAlive()) {
                    System.err.println("Error reading server stderr: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }, "stderr-reader");
        stderrReaderThread.setDaemon(true);
        stderrReaderThread.start();
    }

    private static void notifyStderr(CommunicationListener listener, String line) {
        try {
            listener.onServerStderr(line);
        } catch (Exception e) {
            // Don't let listener exceptions break the stderr reading
            System.err.println("Listener error on stderr: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    /**
     * Send a request and wait for response
     */
    public JsonRpcResponse sendRequest(String method, Object params) throws IOException {
        if (serverProcess == null || !serverProcess.isAlive()) {
            throw new IllegalStateException("Server process is not running");
        }

        // Create request with ID
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

        // Read response - skip any non-JSON lines (e.g., debug agent output)
        while (true) {
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

            // Skip empty lines
            if (responseLine.trim().isEmpty()) {
                continue;
            }

            // Check if line looks like JSON (starts with '{')
            String trimmed = responseLine.trim();
            if (!trimmed.startsWith("{")) {
                // Not JSON - likely debug agent output or other non-protocol message
                // Check for debug agent message specifically
                if (trimmed.startsWith("Listening for transport dt_socket")) {
                    listeners.forEach(l -> notifyStderr(l, "[DEBUG] Server suspended - waiting for debugger to attach"));
                    listeners.forEach(l -> notifyStderr(l, "[DEBUG] " + trimmed));
                } else {
                    // Log other non-JSON output to stderr listeners
                    String nonJsonLine = responseLine;
                    listeners.forEach(l -> notifyStderr(l, "[stdout] " + nonJsonLine));
                }
                continue;
            }

            // Try to parse as JSON-RPC response
            try {
                JsonRpcResponse response = MAPPER.readValue(responseLine, JsonRpcResponse.class);
                listeners.forEach(l -> notifyResponseReceived(l, response));
                return response;
            } catch (StreamReadException jack) {
                // JSON parse error - this might be malformed JSON, log and throw
                if (jack.getMessage().startsWith("Unrecognized token")) {
                    notifyError("Error parsing response: '" + responseLine + "'", jack);
                } else {
                    notifyError("Failed to parse response", jack);
                }
                throw jack;
            } catch (Exception e) {
                notifyError("Failed to parse response", e);
                throw new IOException("Failed to parse response: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Send a notification (request without id - no response expected).
     * Notifications do not wait for a response per JSON-RPC 2.0 spec.
     */
    public void sendNotification(String method, Object params) throws IOException {
        if (serverProcess == null || !serverProcess.isAlive()) {
            throw new IllegalStateException("Server process is not running");
        }

        // Create notification (no id)
        JsonRpcRequest notification = new JsonRpcRequest(
            "2.0",
            null,  // No ID for notifications
            method,
            params
        );

        listeners.forEach(l -> notifyRequestSent(l, notification));

        // Serialize and send
        String notificationJson = MAPPER.writeValueAsString(notification);
        writer.println(notificationJson);
        writer.flush();

        // No response expected for notifications
    }

    private static void notifyResponseReceived(CommunicationListener listener, JsonRpcResponse response) {
        try {
            listener.onResponseReceived(response);
        } catch (Exception e) {
            // Don't let listener exceptions break the response handling
            System.err.println("Listener error on response received: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static void notifyRequestSent(CommunicationListener listener, JsonRpcRequest request) {
        try {
            listener.onRequestSent(request);
        } catch (Exception e) {
            // Don't let listener exceptions break the request
            System.err.println("Listener error on request sent: " + e.getMessage());
            e.printStackTrace(System.err);
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
                e.printStackTrace(System.err);
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
        // Step 1: Terminate the process tree (including all child processes)
        // This is important because run.sh spawns a java subprocess that would become orphaned
        if (serverProcess != null && serverProcess.isAlive()) {
            try {
                ProcessHandle processHandle = serverProcess.toHandle();

                // First, destroy all descendant processes (children, grandchildren, etc.)
                processHandle.descendants().forEach(descendant -> {
                    System.err.println("Destroying descendant process: " + descendant.pid());
                    descendant.destroy();
                });

                // Then destroy the main process
                serverProcess.destroy();

                // Wait up to 2 seconds for graceful shutdown
                if (!serverProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    // Force kill all descendants
                    processHandle.descendants().forEach(descendant -> {
                        System.err.println("Force killing descendant process: " + descendant.pid());
                        descendant.destroyForcibly();
                    });

                    // Force kill the main process
                    serverProcess.destroyForcibly();

                    // Give it 1 more second after force kill
                    serverProcess.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Force kill on interrupt
                if (serverProcess != null) {
                    try {
                        ProcessHandle processHandle = serverProcess.toHandle();
                        processHandle.descendants().forEach(ProcessHandle::destroyForcibly);
                    } catch (Exception ex) {
                        // Ignore - best effort
                    }
                    serverProcess.destroyForcibly();
                }
            } catch (Exception e) {
                System.err.println("Error destroying process: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
        serverProcess = null;

        // Step 2: Wait for stderr reader thread to finish naturally
        // (it will exit when the stream closes due to process termination)
        if (stderrReaderThread != null && stderrReaderThread.isAlive()) {
            try {
                stderrReaderThread.join(1000);
                if (stderrReaderThread.isAlive()) {
                    System.err.println("WARNING: stderr reader thread did not exit after 1 second");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        stderrReaderThread = null;

        // Step 3: Close streams explicitly (should already be closed by process termination)
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                // Ignore - stream should already be closed
            }
            writer = null;
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                // Ignore - stream should already be closed
            }
            reader = null;
        }
        if (stderrReader != null) {
            try {
                stderrReader.close();
            } catch (Exception e) {
                // Ignore - stream should already be closed
            }
            stderrReader = null;
        }
    }
}


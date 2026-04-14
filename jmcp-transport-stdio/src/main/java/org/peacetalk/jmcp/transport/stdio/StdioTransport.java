package org.peacetalk.jmcp.transport.stdio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.peacetalk.jmcp.core.transport.McpRequestHandler;
import org.peacetalk.jmcp.core.transport.McpTransport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * stdio-based MCP transport implementation.
 * Reads JSON-RPC requests from stdin and writes responses to stdout.
 */
public class StdioTransport implements McpTransport {
    private static final Logger LOG = LogManager.getLogger(StdioTransport.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;

    @Override
    public void start(McpRequestHandler handler) throws Exception {
        if (running.get()) {
            throw new IllegalStateException("Transport is already running");
        }

        running.set(true);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter writer = new PrintWriter(System.out, true);

        readerThread = new Thread(() -> {
            try {
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String response = handler.handleRequest(line);

                    // Only write response if not null (notifications return null)
                    if (response != null) {
                        writer.println(response);
                        writer.flush();
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error reading from stdin: {}", e.getMessage(), e);
                }
            }
        }, "stdio-transport-reader");

        readerThread.start();
    }

    @Override
    public void stop() throws Exception {
        running.set(false);
        if (readerThread != null) {
            readerThread.interrupt();
            readerThread.join(1000);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}

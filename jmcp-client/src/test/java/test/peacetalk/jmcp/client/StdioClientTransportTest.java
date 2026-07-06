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

package test.peacetalk.jmcp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.client.CommunicationListener;
import org.peacetalk.jmcp.client.StdioClientTransport;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the single-flight request/response exchange in StdioClientTransport.
 * Uses the stream-based constructor with pipes and a scripted fake server
 * thread, so no real process is required. The fake server parses each incoming
 * request (so it knows the real, globally-incrementing request id) and replies
 * according to the per-test script.
 */
class StdioClientTransportTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(10);

    private StdioClientTransport transport;
    private PipedWriter serverResponseWriter;
    private Thread serverThread;
    private RecordingListener listener;

    /**
     * Wire the transport to a fake server thread. For every request line the
     * server reads, {@code script} is invoked with the parsed request and a
     * writer for the response stream.
     */
    private void connect(BiConsumer<JsonRpcRequest, PrintWriter> script) throws Exception {
        PipedReader requestsForServer = new PipedReader();
        PipedWriter transportRequestWriter = new PipedWriter(requestsForServer);
        PipedReader responsesForTransport = new PipedReader();
        serverResponseWriter = new PipedWriter(responsesForTransport);

        transport = new StdioClientTransport(responsesForTransport, transportRequestWriter);
        listener = new RecordingListener();
        transport.addListener(listener);

        serverThread = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(requestsForServer);
                 PrintWriter out = new PrintWriter(serverResponseWriter, true)) {
                String line;
                while ((line = in.readLine()) != null) {
                    JsonRpcRequest request = MAPPER.readValue(line, JsonRpcRequest.class);
                    script.accept(request, out);
                }
            } catch (Exception e) {
                // Pipe closed during teardown - expected
            }
        }, "fake-mcp-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (transport != null) {
            transport.close();
        }
        if (serverThread != null) {
            serverThread.join(2000);
        }
    }

    /**
     * Kills the mutant: delete (or invert) the id-mismatch check in
     * sendRequest, so the first parsed response is returned regardless of id.
     * That mutant returns result {"from":"stale"} and this test's result
     * assertion fails.
     */
    @Test
    void staleResponseWithMismatchedIdIsSkippedUntilMatchingIdArrives() throws Exception {
        connect((request, out) -> {
            out.println("{\"jsonrpc\":\"2.0\",\"id\":999999,\"result\":{\"from\":\"stale\"}}");
            out.println("{\"jsonrpc\":\"2.0\",\"id\":" + request.id()
                    + ",\"result\":{\"from\":\"current\"}}");
        });

        JsonRpcResponse response = assertTimeoutPreemptively(EXCHANGE_TIMEOUT,
                () -> transport.sendRequest("tools/list", Map.of()));

        Object sentId = listener.requests.get(0).id();
        assertEquals(String.valueOf(sentId), String.valueOf(response.id()),
                "returned response id must match the request id");
        assertEquals(Map.of("from", "current"), response.result(),
                "the stale response must be skipped, not returned");
        assertEquals(1, listener.responses.size(),
                "the stale response must not be delivered to response listeners");
        assertTrue(listener.errors.stream().anyMatch(m -> m.contains("999999")),
                "listeners must be notified of the discarded stale response id; got: " + listener.errors);
    }

    /**
     * Kills the mutant: replace the string-value id comparison with
     * {@code sentId.equals(responseId)}. The transport sends Long ids while
     * Jackson deserializes small numeric JSON ids as Integer, so Object.equals
     * is always false; that mutant treats the correct response as stale and
     * blocks forever, failing the preemptive timeout.
     */
    @Test
    void responseIdDeserializedAsIntegerMatchesRequestIdSentAsLong() throws Exception {
        connect((request, out) ->
                out.println("{\"jsonrpc\":\"2.0\",\"id\":" + request.id() + ",\"result\":{}}"));

        JsonRpcResponse response = assertTimeoutPreemptively(EXCHANGE_TIMEOUT,
                () -> transport.sendRequest("ping", Map.of()));

        // Preconditions that make this test meaningful: the id types really differ,
        // so a naive equals comparison could not have matched them.
        Object sentId = listener.requests.get(0).id();
        assertInstanceOf(Long.class, sentId,
                "precondition: transport generates Long request ids");
        assertInstanceOf(Integer.class, response.id(),
                "precondition: Jackson deserializes small numeric ids as Integer");
        assertNotEquals(sentId, response.id(),
                "precondition: Object.equals across Long/Integer must be false, "
                        + "otherwise this test no longer exercises the type-mismatch case");

        assertEquals(Map.of(), response.result(), "matching response must be returned");
        assertEquals(List.of(), listener.errors,
                "a matching response must not be reported as stale");
    }

    /**
     * Kills the mutant: remove the exchange lock from sendRequest. Without it,
     * both requests are written before either response is read and one thread
     * can consume (and discard) the response belonging to the other, so that
     * caller never completes and Future.get times out; or, without the id
     * check, the callers receive each other's results and the per-caller
     * result assertions fail.
     */
    @Test
    void concurrentSendRequestsEachReceiveTheResponseForTheirOwnId() throws Exception {
        connect((request, out) -> {
            // Delay each reply so the second caller is contending while the
            // first exchange is still in flight.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            out.println("{\"jsonrpc\":\"2.0\",\"id\":" + request.id()
                    + ",\"result\":{\"method\":\"" + request.method() + "\"}}");
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JsonRpcResponse> alpha = pool.submit(() -> transport.sendRequest("alpha", Map.of()));
            Future<JsonRpcResponse> beta = pool.submit(() -> transport.sendRequest("beta", Map.of()));

            JsonRpcResponse alphaResponse = alpha.get(EXCHANGE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            JsonRpcResponse betaResponse = beta.get(EXCHANGE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertEquals(Map.of("method", "alpha"), alphaResponse.result(),
                    "the 'alpha' caller must receive the response to its own request");
            assertEquals(Map.of("method", "beta"), betaResponse.result(),
                    "the 'beta' caller must receive the response to its own request");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Kills the mutants: drop the blank-line skip (the blank line would be
     * routed as a second "[stdout] " entry), drop or change the "[stdout] "
     * routing of non-JSON lines (the stderr list assertion fails), or make the
     * non-JSON branch throw instead of continue (no response is ever
     * returned). Pins the pre-existing behavior the single-flight change had
     * to preserve.
     */
    @Test
    void nonJsonStdoutLinesAreRoutedToStderrListenersAndSkipped() throws Exception {
        connect((request, out) -> {
            out.println("");
            out.println("log noise before response");
            out.println("{\"jsonrpc\":\"2.0\",\"id\":" + request.id() + ",\"result\":{}}");
        });

        JsonRpcResponse response = assertTimeoutPreemptively(EXCHANGE_TIMEOUT,
                () -> transport.sendRequest("ping", Map.of()));

        assertEquals(Map.of(), response.result(), "response after noise must still be returned");
        assertEquals(List.of("[stdout] log noise before response"), listener.stderrLines,
                "exactly the non-JSON, non-blank line must be routed to stderr listeners");
    }

    /**
     * Hand-written fake per CLAUDE.md 1.5 - records every listener callback.
     * Thread-safe because transport exchanges happen on test worker threads.
     */
    private static final class RecordingListener implements CommunicationListener {
        final List<JsonRpcRequest> requests = new CopyOnWriteArrayList<>();
        final List<JsonRpcResponse> responses = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<String> stderrLines = new CopyOnWriteArrayList<>();

        @Override
        public void onRequestSent(JsonRpcRequest request) {
            requests.add(request);
        }

        @Override
        public void onResponseReceived(JsonRpcResponse response) {
            responses.add(response);
        }

        @Override
        public void onError(String message, Exception exception) {
            errors.add(message);
        }

        @Override
        public void onServerStderr(String line) {
            stderrLines.add(line);
        }
    }
}

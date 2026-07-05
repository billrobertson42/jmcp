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

package test.org.peacetalk.jmcp.transport.stdio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.peacetalk.jmcp.core.transport.McpRequestHandler;
import org.peacetalk.jmcp.transport.stdio.StdioTransport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link StdioTransport}.
 *
 * <p>{@code StdioTransport.start()} reads from {@link System#in} and writes to
 * {@link System#out} directly (there is no stream-injection seam), so these
 * tests redirect {@code System.in}/{@code System.out} to in-memory streams
 * around the transport lifecycle and restore them in {@link #restoreStreams()}.
 * All I/O is in-memory: there is no real process, socket, or file involved.</p>
 *
 * <p>The transport frames messages as newline-delimited JSON: it reads one line
 * per request via {@code BufferedReader.readLine()} and writes each response via
 * {@code PrintWriter.println()} (response text followed by the platform line
 * separator). Blank / whitespace-only input lines are skipped, and a {@code null}
 * handler result (a notification) produces no output at all.</p>
 */
class StdioTransportTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;

    private StdioTransport transport;

    @AfterEach
    void restoreStreams() throws Exception {
        // Stop the transport first so its reader thread is not left running
        // against a stream we are about to swap back out from under it.
        if (transport != null) {
            transport.stop();
        }
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    // ------------------------------------------------------------------
    // Test infrastructure
    // ------------------------------------------------------------------

    /**
     * Redirects {@code System.in} to the given bytes and {@code System.out} to a
     * fresh buffer, then starts the transport with the supplied handler. The
     * transport must be started AFTER the streams are swapped because
     * {@code start()} captures {@code System.in}/{@code System.out} at that moment.
     *
     * @return the buffer that captures everything the transport writes to stdout.
     */
    private ByteArrayOutputStream startWith(String stdinContents, McpRequestHandler handler) throws Exception {
        return startWith(stdinContents.getBytes(StandardCharsets.UTF_8), handler);
    }

    private ByteArrayOutputStream startWith(byte[] stdinContents, McpRequestHandler handler) throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setIn(new ByteArrayInputStream(stdinContents));
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        transport = new StdioTransport();
        transport.start(handler);
        return captured;
    }

    /**
     * Busy-waits (with a short sleep) until {@code captured} contains at least
     * {@code expectedLineCount} newline-terminated lines, or the deadline passes.
     * The reader runs on a background thread, so output arrives asynchronously.
     */
    private static void awaitLineCount(ByteArrayOutputStream captured, int expectedLineCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (countLines(captured) >= expectedLineCount) {
                return;
            }
            Thread.sleep(5);
        }
    }

    /** Waits until the given counter reaches {@code target} or the deadline passes. */
    private static void awaitCount(AtomicInteger counter, int target) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline && counter.get() < target) {
            Thread.sleep(5);
        }
    }

    /** Waits until the given list has at least {@code target} elements or the deadline passes. */
    private static void awaitListSize(List<?> list, int target) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline && list.size() < target) {
            Thread.sleep(5);
        }
    }

    private static int countLines(ByteArrayOutputStream captured) {
        String s = captured.toString(StandardCharsets.UTF_8);
        if (s.isEmpty()) {
            return 0;
        }
        int lines = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * Splits captured stdout into logical response lines. Uses {@code \R} so the
     * assertion is agnostic to whether {@code println} emitted {@code \n} or
     * {@code \r\n} on the running platform.
     */
    private static List<String> capturedLines(ByteArrayOutputStream captured) {
        String s = captured.toString(StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) {
            return out;
        }
        // Trailing separator would otherwise yield an empty final element with
        // limit -1; drop trailing separators first so we count real responses.
        String trimmedTrailing = s.replaceAll("\\R+$", "");
        if (trimmedTrailing.isEmpty()) {
            return out;
        }
        for (String part : trimmedTrailing.split("\\R")) {
            out.add(part);
        }
        return out;
    }

    /** Records every line handed to the handler, in arrival order. */
    private static final class RecordingHandler implements McpRequestHandler {
        final List<String> received = new CopyOnWriteArrayList<>();
        private final Function<String, String> responder;

        RecordingHandler(Function<String, String> responder) {
            this.responder = responder;
        }

        @Override
        public String handleRequest(String jsonRpcRequest) {
            received.add(jsonRpcRequest);
            return responder.apply(jsonRpcRequest);
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    void initiallyNotRunning() {
        StdioTransport t = new StdioTransport();
        assertFalse(t.isRunning(), "a freshly constructed transport must not report running");
    }

    @Test
    @Timeout(5)
    void isRunningAfterStart() throws Exception {
        // Never-ending input keeps the reader thread alive so isRunning() is observable.
        InputStream blocking = new InputStream() {
            @Override
            public int read() {
                // Block forever without spinning; interrupted on stop().
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }
        };
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setIn(blocking);
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        transport = new StdioTransport();
        transport.start(req -> null);

        assertTrue(transport.isRunning(), "transport must report running after start()");
    }

    @Test
    @Timeout(5)
    void cannotStartTwice() throws Exception {
        ByteArrayOutputStream captured = startWith("", req -> null);

        assertTrue(transport.isRunning(), "transport should be running after first start()");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> transport.start(req -> null),
                "second start() must be rejected with IllegalStateException");
        assertTrue(ex.getMessage() != null && ex.getMessage().toLowerCase().contains("running"),
                "exception message should mention that the transport is already running, was: " + ex.getMessage());
        assertTrue(captured.toString(StandardCharsets.UTF_8).isEmpty(),
                "empty stdin must produce no stdout");
    }

    @Test
    void stopWhenNotRunningIsNoOp() {
        StdioTransport t = new StdioTransport();
        assertDoesNotThrow(t::stop, "stop() on a never-started transport must not throw");
        assertFalse(t.isRunning(), "transport must remain not-running after a no-op stop()");
    }

    @Test
    @Timeout(5)
    void stopSetsRunningFalse() throws Exception {
        InputStream blocking = new InputStream() {
            @Override
            public int read() {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }
        };
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setIn(blocking);
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        transport = new StdioTransport();
        transport.start(req -> null);
        assertTrue(transport.isRunning(), "precondition: transport running before stop()");

        transport.stop();

        assertFalse(transport.isRunning(), "isRunning() must be false after stop()");
    }

    // ------------------------------------------------------------------
    // Framing: single request -> single response line
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void writesSingleResponseFramedWithNewline() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":1,"method":"ping"}""";
        String response = """
                {"jsonrpc":"2.0","id":1,"result":"pong"}""";

        RecordingHandler handler = new RecordingHandler(req -> response);
        ByteArrayOutputStream captured = startWith(request + "\n", handler);

        awaitLineCount(captured, 1);

        // Exact framing: the response text followed by exactly one line separator.
        assertEquals(response + System.lineSeparator(), captured.toString(StandardCharsets.UTF_8),
                "a single request must yield exactly the response plus one trailing line separator");
        // Would fail if the code failed to write, wrote the request instead, or double-framed.
        assertEquals(List.of(response), capturedLines(captured),
                "exactly one response line should be emitted");
    }

    @Test
    @Timeout(5)
    void handlerReceivesLineWithoutTrailingNewline() throws Exception {
        String request = """
                {"method":"tools/list"}""";
        RecordingHandler handler = new RecordingHandler(req -> "{}");

        ByteArrayOutputStream captured = startWith(request + "\n", handler);
        awaitLineCount(captured, 1);

        // readLine() strips the terminator: the handler must see the raw JSON only.
        assertEquals(List.of(request), handler.received,
                "handler must receive the exact request line with no trailing newline or carriage return");
    }

    @Test
    @Timeout(5)
    void lastMessageWithoutTrailingNewlineIsStillProcessed() throws Exception {
        // No trailing newline: readLine() still returns the final line at EOF.
        String request = """
                {"id":42}""";
        RecordingHandler handler = new RecordingHandler(req -> """
                {"echoed":true}""");

        ByteArrayOutputStream captured = startWith(request, handler);
        awaitLineCount(captured, 1);

        assertEquals(List.of(request), handler.received,
                "a final line lacking a newline terminator must still be delivered to the handler");
        assertEquals(List.of("""
                {"echoed":true}"""), capturedLines(captured),
                "the response for an unterminated final line must still be written");
    }

    // ------------------------------------------------------------------
    // Multiple messages back-to-back
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void processesMultipleMessagesInOrder() throws Exception {
        String r1 = """
                {"id":1}""";
        String r2 = """
                {"id":2}""";
        String r3 = """
                {"id":3}""";
        // Each request echoed back with a marker so ordering is verifiable.
        RecordingHandler handler = new RecordingHandler(req -> "resp:" + req);

        ByteArrayOutputStream captured = startWith(r1 + "\n" + r2 + "\n" + r3 + "\n", handler);
        awaitLineCount(captured, 3);

        assertEquals(List.of(r1, r2, r3), handler.received,
                "all three requests must be delivered to the handler in stdin order");
        assertEquals(List.of("resp:" + r1, "resp:" + r2, "resp:" + r3), capturedLines(captured),
                "responses must be written in request order, one framed line each");
    }

    @Test
    @Timeout(5)
    void eachResponseIsNewlineTerminatedForBackToBackMessages() throws Exception {
        RecordingHandler handler = new RecordingHandler(req -> "A");
        ByteArrayOutputStream captured = startWith("x\ny\n", handler);
        awaitLineCount(captured, 2);

        String sep = System.lineSeparator();
        // Two independent framed responses, not a single concatenated blob.
        assertEquals("A" + sep + "A" + sep, captured.toString(StandardCharsets.UTF_8),
                "back-to-back messages must each get their own trailing separator");
    }

    // ------------------------------------------------------------------
    // Blank / whitespace-only line handling
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void skipsBlankAndWhitespaceOnlyLines() throws Exception {
        // Blank line, whitespace-only line, then a real request. Kept as an escaped
        // string rather than a text block: the whitespace-only line (spaces + tab)
        // is exactly what this test is probing, and text blocks strip trailing
        // whitespace from each line, which would silently change it.
        String stdin = "\n   \t \n{\"id\":7}\n";
        RecordingHandler handler = new RecordingHandler(req -> "ok");

        ByteArrayOutputStream captured = startWith(stdin, handler);
        awaitLineCount(captured, 1);

        assertEquals(List.of("""
                {"id":7}"""), handler.received,
                "blank and whitespace-only lines must be skipped, not forwarded to the handler");
        assertEquals(List.of("ok"), capturedLines(captured),
                "no response should be produced for skipped blank lines");
    }

    @Test
    @Timeout(5)
    void blankLinesBetweenMessagesDoNotBreakFraming() throws Exception {
        String stdin = """
                {"id":1}

                {"id":2}
                """;
        RecordingHandler handler = new RecordingHandler(req -> req);

        ByteArrayOutputStream captured = startWith(stdin, handler);
        awaitLineCount(captured, 2);

        assertEquals(List.of("""
                {"id":1}""", """
                {"id":2}"""), handler.received,
                "an interleaved blank line must not consume or corrupt the following message");
        assertEquals(List.of("""
                {"id":1}""", """
                {"id":2}"""), capturedLines(captured),
                "exactly two responses should be framed despite the blank separator line");
    }

    // ------------------------------------------------------------------
    // Notifications: null handler result -> no output
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void nullHandlerResultProducesNoOutput() throws Exception {
        // A notification handler returns null; the transport must write nothing.
        AtomicInteger calls = new AtomicInteger();
        McpRequestHandler handler = req -> {
            calls.incrementAndGet();
            return null;
        };

        ByteArrayOutputStream captured = startWith("""
                {"method":"notify"}
                """, handler);
        awaitCount(calls, 1);
        // Give any erroneous write a chance to land before asserting emptiness.
        Thread.sleep(50);

        assertEquals(1, calls.get(), "handler must still be invoked for a notification");
        assertEquals("", captured.toString(StandardCharsets.UTF_8),
                "a null handler result (notification) must produce no bytes on stdout");
    }

    @Test
    @Timeout(5)
    void mixedNotificationsAndResponses() throws Exception {
        // Odd ids get a response; even ids are notifications (null).
        RecordingHandler handler = new RecordingHandler(req -> req.contains("""
                "id":2""") ? null : ("r:" + req));
        String stdin = """
                {"id":1}
                {"id":2}
                {"id":3}
                """;

        ByteArrayOutputStream captured = startWith(stdin, handler);
        awaitLineCount(captured, 2);
        Thread.sleep(50); // ensure a stray write for id 2 would have appeared

        assertEquals(List.of("""
                {"id":1}""", """
                {"id":2}""", """
                {"id":3}"""), handler.received,
                "all messages including the notification must reach the handler");
        assertEquals(List.of("""
                r:{"id":1}""", """
                r:{"id":3}"""), capturedLines(captured),
                "only the two non-null results should be framed; the notification writes nothing");
    }

    // ------------------------------------------------------------------
    // Malformed input is passed through, not crashed on
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void malformedJsonIsForwardedVerbatimToHandler() throws Exception {
        // The transport does not parse JSON; it must hand the raw line to the
        // handler unchanged and must not crash on non-JSON content.
        String garbage = "this is not json {[";
        RecordingHandler handler = new RecordingHandler(req -> """
                {"error":"parse"}""");

        ByteArrayOutputStream captured = startWith(garbage + "\n", handler);
        awaitLineCount(captured, 1);

        assertEquals(List.of(garbage), handler.received,
                "malformed input must be forwarded verbatim; the transport is not a JSON parser");
        assertEquals(List.of("""
                {"error":"parse"}"""), capturedLines(captured),
                "the handler's error response for malformed input must be framed and written");
    }

    @Test
    @Timeout(5)
    void handlerThrowingStopsReaderButDoesNotCrashTest() throws Exception {
        // If the handler throws, the reader thread dies (RuntimeException escapes
        // the loop). This documents current behavior: the throw is not swallowed,
        // subsequent lines are NOT processed, but stop() still cleans up safely.
        RecordingHandler handler = new RecordingHandler(req -> {
            throw new RuntimeException("boom");
        });

        ByteArrayOutputStream captured = startWith("""
                {"id":1}
                {"id":2}
                """, handler);
        awaitListSize(handler.received, 1); // wait until the first (throwing) call has occurred
        Thread.sleep(50); // and give a would-be second read time to (not) happen

        assertEquals(1, handler.received.size(),
                "reader thread dies on the first thrown handler exception, so the second line is never read");
        assertEquals("", captured.toString(StandardCharsets.UTF_8),
                "a thrown handler exception produces no response output");
        assertDoesNotThrow(() -> transport.stop(),
                "stop() must succeed even after the reader thread died from a handler exception");
    }

    // ------------------------------------------------------------------
    // Boundary: EOF and large messages
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void emptyStdinProducesNoOutputAndNoHandlerCalls() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        McpRequestHandler handler = req -> {
            calls.incrementAndGet();
            return "unexpected";
        };

        ByteArrayOutputStream captured = startWith("", handler);
        // Reader hits EOF immediately; give it a moment to exit the loop.
        Thread.sleep(50);

        assertEquals(0, calls.get(), "no handler invocation should occur for empty (immediate-EOF) stdin");
        assertEquals("", captured.toString(StandardCharsets.UTF_8),
                "empty stdin must produce no output");
    }

    @Test
    @Timeout(5)
    void handlesLargeSingleMessage() throws Exception {
        // A large one-line payload must survive buffering and framing intact.
        StringBuilder sb = new StringBuilder("{\"data\":\"");
        for (int i = 0; i < 200_000; i++) {
            sb.append('a');
        }
        sb.append("\"}");
        String bigRequest = sb.toString();

        RecordingHandler handler = new RecordingHandler(req -> "len:" + req.length());
        ByteArrayOutputStream captured = startWith(bigRequest + "\n", handler);
        awaitLineCount(captured, 1);

        assertEquals(1, handler.received.size(), "the large message should arrive as a single line");
        assertEquals(bigRequest, handler.received.get(0),
                "the entire large payload must be delivered intact, not truncated");
        assertEquals(List.of("len:" + bigRequest.length()), capturedLines(captured),
                "the response for the large message must be framed correctly");
    }
}

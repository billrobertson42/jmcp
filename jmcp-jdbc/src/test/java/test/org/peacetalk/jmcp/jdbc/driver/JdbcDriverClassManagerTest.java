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

package test.org.peacetalk.jmcp.jdbc.driver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverClassManager;
import org.peacetalk.jmcp.jdbc.driver.MavenCoordinates;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Driver;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JdbcDriverClassManagerTest {

    private static final MavenCoordinates FAKE_DRIVER =
        new MavenCoordinates("com.example", "fake-driver", "1.0");
    private static final MavenCoordinates OTHER_FAKE_DRIVER =
        new MavenCoordinates("com.example", "other-driver", "2.0");

    private static final byte[] JAR_BYTES = "fake jar bytes for download tests".getBytes();

    @TempDir
    Path cacheDir;

    // ------------------------------------------------------------------
    // Local repository server (no external network)
    // ------------------------------------------------------------------

    private HttpServer server;
    private final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
    /** Maps a request path to a 200 response body; null means 404. */
    private volatile Function<String, byte[]> responder = path -> null;

    /** Starts a localhost repository server and returns its base URL. */
    private String startRepo() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            byte[] body = responder.apply(path);
            if (body == null) {
                byte[] notFound = "not found".getBytes();
                exchange.sendResponseHeaders(404, notFound.length);
                exchange.getResponseBody().write(notFound);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/repo/";
    }

    @AfterEach
    void stopRepo() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** Serves {@code JAR_BYTES} for every *.jar and its correct digest for every *.jar.sha1. */
    private void serveJarsWithValidChecksums() {
        String sha1 = sha1Of(JAR_BYTES);
        responder = path -> {
            if (path.endsWith(".jar.sha1")) {
                return (sha1 + "  " + path.substring(path.lastIndexOf('/') + 1)).getBytes();
            }
            if (path.endsWith(".jar")) {
                return JAR_BYTES.clone();
            }
            return null;
        };
    }

    /** SHA-1 computed independently of production code, so the two can disagree. */
    private static String sha1Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static IOException rootIoException(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof IOException io) {
                return io;
            }
        }
        return fail("expected an IOException in the cause chain of: " + thrown);
    }

    private List<Path> cacheDirContents() throws IOException {
        try (Stream<Path> files = Files.list(cacheDir)) {
            return files.toList();
        }
    }

    // ------------------------------------------------------------------
    // getKnownDriver
    // ------------------------------------------------------------------

    @Test
    void getKnownDriverReturnsCoordinatesForType() throws Exception {
        // Mutant killed: KNOWN_DRIVERS entry for "h2" pointing at the wrong
        // groupId/artifactId, or the lookup ignoring its key.
        var manager = new JdbcDriverClassManager(cacheDir);
        List<MavenCoordinates> h2 = manager.getKnownDriver("h2");
        assertEquals(1, h2.size(), "h2 is a single-jar driver");
        assertEquals("com.h2database", h2.get(0).groupId());
        assertEquals("h2", h2.get(0).artifactId());
    }

    @Test
    void sqlServerDriverIncludesMsal4jCompanion() throws Exception {
        // Mutant killed: dropping the companion artifact from the sqlserver
        // entry - Azure AD auth needs msal4j on the driver's classpath, and
        // the driver jar must stay first (it is what loadDriverClass resolves).
        var manager = new JdbcDriverClassManager(cacheDir);
        List<MavenCoordinates> sqlserver = manager.getKnownDriver("sqlserver");
        assertEquals("mssql-jdbc", sqlserver.get(0).artifactId(),
            "the JDBC driver must be the first artifact");
        assertTrue(sqlserver.stream().anyMatch(c -> c.artifactId().equals("msal4j")),
            "sqlserver must ship msal4j for Azure AD authentication: " + sqlserver);
    }

    @Test
    void getKnownDriverIsCaseInsensitive() throws Exception {
        // Mutant killed: dropping the toLowerCase() on the database type.
        var manager = new JdbcDriverClassManager(cacheDir);
        assertEquals(manager.getKnownDriver("h2"), manager.getKnownDriver("H2"));
    }

    @Test
    void getUnknownDriverThrowsWithType() throws Exception {
        // Mutant killed: returning null (or a default) instead of rejecting an
        // unknown database type.
        var manager = new JdbcDriverClassManager(cacheDir);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> manager.getKnownDriver("unknown-db"));
        assertTrue(ex.getMessage().contains("unknown-db"),
            "message should name the unknown type: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Download path (against the localhost repository)
    // ------------------------------------------------------------------

    @Test
    void downloadVerifiesChecksumAndCachesJar() throws Exception {
        // Mutants killed: checksum verification wrongly rejecting a valid download
        // (equalsIgnoreCase negated), atomic move to the wrong file name, temp
        // .part file left behind, and — via the .sha1 request assertion —
        // verification being skipped entirely.
        serveJarsWithValidChecksums();
        var manager = new JdbcDriverClassManager(cacheDir, startRepo());

        var loader = manager.loadDriver(FAKE_DRIVER);
        try {
            assertArrayEquals(JAR_BYTES, Files.readAllBytes(cacheDir.resolve("fake-driver-1.0.jar")),
                "cached jar must contain exactly the served bytes");
            assertTrue(requestedPaths.contains("/repo/" + FAKE_DRIVER.toPath() + ".sha1"),
                "the .sha1 companion file must be fetched for verification");
            for (Path cached : cacheDirContents()) {
                assertTrue(cached.getFileName().toString().endsWith(".jar"),
                    "no temp files may remain in the cache dir, found: " + cached);
            }
        } finally {
            manager.unloadDriver(FAKE_DRIVER);
        }
    }

    @Test
    void uppercaseChecksumAccepted() throws Exception {
        // Mutant killed: comparing digests with equals() instead of
        // equalsIgnoreCase() — Maven Central digests are lowercase but the format
        // does not guarantee it.
        String upper = sha1Of(JAR_BYTES).toUpperCase();
        responder = path -> path.endsWith(".jar.sha1") ? upper.getBytes()
            : path.endsWith(".jar") ? JAR_BYTES.clone() : null;
        var manager = new JdbcDriverClassManager(cacheDir, startRepo());

        var loader = manager.loadDriver(FAKE_DRIVER);
        try {
            assertTrue(Files.exists(cacheDir.resolve("fake-driver-1.0.jar")),
                "upper-case checksum must verify successfully");
        } finally {
            manager.unloadDriver(FAKE_DRIVER);
        }
    }

    @Test
    void non200ResponseFailsAndCachesNothing() throws Exception {
        // Mutants killed: statusCode() != 200 check removed (a 404 HTML body would
        // be cached and loaded as a jar), and error bodies surviving in the cache.
        responder = path -> null; // 404 for everything
        var manager = new JdbcDriverClassManager(cacheDir, startRepo());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> manager.loadDriver(FAKE_DRIVER));
        IOException io = rootIoException(ex);
        assertTrue(io.getMessage().contains("404"),
            "message must carry the HTTP status: " + io.getMessage());
        assertTrue(io.getMessage().contains(FAKE_DRIVER.toPath()),
            "message must carry the URL: " + io.getMessage());
        assertEquals(List.of(), cacheDirContents(),
            "a failed download must leave the cache empty");
    }

    @Test
    void checksumMismatchFailsAndCachesNothing() throws Exception {
        // Mutant killed: digest comparison negated or removed — a tampered jar
        // must never reach the cache.
        String wrongSha1 = sha1Of("completely different bytes".getBytes());
        responder = path -> path.endsWith(".jar.sha1") ? wrongSha1.getBytes()
            : path.endsWith(".jar") ? JAR_BYTES.clone() : null;
        var manager = new JdbcDriverClassManager(cacheDir, startRepo());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> manager.loadDriver(FAKE_DRIVER));
        IOException io = rootIoException(ex);
        assertTrue(io.getMessage().contains("SHA-1 mismatch"),
            "message must name the failure: " + io.getMessage());
        assertEquals(List.of(), cacheDirContents(),
            "a jar failing verification must never be cached");
    }

    @Test
    void missingChecksumFailsClosedAndCachesNothing() throws Exception {
        // Mutant killed: treating a failed .sha1 fetch as "skip verification"
        // instead of failing closed.
        responder = path -> path.endsWith(".jar") ? JAR_BYTES.clone() : null;
        var manager = new JdbcDriverClassManager(cacheDir, startRepo());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> manager.loadDriver(FAKE_DRIVER));
        IOException io = rootIoException(ex);
        assertTrue(io.getMessage().contains("checksum"),
            "message must point at the checksum fetch: " + io.getMessage());
        assertTrue(io.getMessage().contains("404"),
            "message must carry the HTTP status: " + io.getMessage());
        assertEquals(List.of(), cacheDirContents(),
            "an unverifiable jar must never be cached");
    }

    @Test
    void cachedJarSkipsDownload() throws Exception {
        // Mutant killed: removing the Files.exists() cache-hit short-circuit —
        // the second manager would hit the 404-only responder and fail.
        serveJarsWithValidChecksums();
        String baseUrl = startRepo();
        var first = new JdbcDriverClassManager(cacheDir, baseUrl);
        first.loadDriver(FAKE_DRIVER);
        first.unloadDriver(FAKE_DRIVER);

        responder = path -> null; // any further download attempt would fail
        requestedPaths.clear();
        var second = new JdbcDriverClassManager(cacheDir, baseUrl);
        var loader = second.loadDriver(FAKE_DRIVER);
        try {
            assertEquals(List.of(), requestedPaths,
                "a cache hit must not touch the repository at all");
        } finally {
            second.unloadDriver(FAKE_DRIVER);
        }
    }

    @Test
    void loadDriverCachesLoaderPerCoordinates() throws Exception {
        // Mutants killed: computeIfAbsent replaced by always-create (same
        // coordinates must reuse the loader), cache key ignoring the coordinates
        // (different coordinates must not share a loader), and unloadDriver not
        // evicting (reload after unload must build a fresh loader).
        serveJarsWithValidChecksums();
        var manager = new JdbcDriverClassManager(cacheDir, startRepo());
        try {
            var loader = manager.loadDriver(FAKE_DRIVER);
            assertSame(loader, manager.loadDriver(FAKE_DRIVER),
                "same coordinates must reuse the cached classloader");
            assertNotSame(loader, manager.loadDriver(OTHER_FAKE_DRIVER),
                "different coordinates must get their own classloader");

            manager.unloadDriver(FAKE_DRIVER);
            assertNotSame(loader, manager.loadDriver(FAKE_DRIVER),
                "unload must evict, forcing a fresh classloader on reload");
        } finally {
            manager.unloadDriver(FAKE_DRIVER);
            manager.unloadDriver(OTHER_FAKE_DRIVER);
        }
    }

    @Test
    void multiArtifactDriverDownloadsAllJarsIntoOneLoader() throws Exception {
        // Mutants killed: only the first artifact downloaded (the companion jar
        // must be cached too), and the loader cache key ignoring companion
        // artifacts (a single-jar load of the same first artifact must get a
        // DIFFERENT classloader than the multi-jar load).
        serveJarsWithValidChecksums();
        var manager = new JdbcDriverClassManager(cacheDir, startRepo());
        List<MavenCoordinates> pair = List.of(FAKE_DRIVER, OTHER_FAKE_DRIVER);
        try {
            var pairLoader = manager.loadDriver(pair);
            assertArrayEquals(JAR_BYTES, Files.readAllBytes(cacheDir.resolve("fake-driver-1.0.jar")),
                "first artifact must be downloaded and cached");
            assertArrayEquals(JAR_BYTES, Files.readAllBytes(cacheDir.resolve("other-driver-2.0.jar")),
                "companion artifact must be downloaded and cached");

            assertSame(pairLoader, manager.loadDriver(pair),
                "same artifact list must reuse the cached classloader");
            assertNotSame(pairLoader, manager.loadDriver(FAKE_DRIVER),
                "a single-jar driver sharing the first artifact must not share "
                    + "the multi-jar driver's classloader");
        } finally {
            manager.unloadDriver(pair);
            manager.unloadDriver(FAKE_DRIVER);
        }
    }

    // ------------------------------------------------------------------
    // End-to-end against the real Maven Central (network)
    // ------------------------------------------------------------------

    @Test
    void loadH2DriverFromMavenCentral() throws Exception {
        // Real-world smoke test: kills mutants in URL construction and .sha1
        // parsing that only a real repository exposes (actual checksum file
        // format, redirects), and proves the downloaded jar is a loadable driver.
        var manager = new JdbcDriverClassManager(cacheDir);
        var loader = manager.loadDriver("h2");
        try {
            // Note: no classloader-identity assertion here — the test JVM has H2 on
            // the module path, and the JDK's built-in loaders delegate named-module
            // packages to their defining loader, so org.h2.Driver resolves to the
            // test-classpath H2 regardless of the isolated loader's URLs.
            Class<?> driverClass = loader.loadClass("org.h2.Driver");
            assertTrue(Driver.class.isAssignableFrom(driverClass),
                "org.h2.Driver must implement java.sql.Driver");

            MavenCoordinates h2 = manager.getKnownDriver("h2").get(0);
            Path cachedJar = cacheDir.resolve(h2.artifactId() + "-" + h2.version() + ".jar");
            assertTrue(Files.exists(cachedJar), "verified jar must be cached at " + cachedJar);
        } finally {
            manager.unloadDriver("h2");
        }
    }

    // ------------------------------------------------------------------
    // parseSha1 — lenient .sha1 body parsing
    // ------------------------------------------------------------------

    private static final String DIGEST = "a9993e364706816aba3e25717850c26c9cd0d89d";

    // Mutants killed: regex not anchored on the leading token, filename/whitespace
    // decorations breaking the parse, upper-case hex rejected.
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("parseableSha1Bodies")
    void parseSha1ExtractsLeadingDigest(String description, String body, String expected) throws Exception {
        assertEquals(expected, JdbcDriverClassManager.parseSha1(body, "test"), description);
    }

    static Stream<Arguments> parseableSha1Bodies() {
        return Stream.of(
            Arguments.of("bare digest", DIGEST, DIGEST),
            Arguments.of("digest with trailing newline", DIGEST + "\n", DIGEST),
            Arguments.of("digest with trailing filename", DIGEST + "  fake-driver-1.0.jar", DIGEST),
            Arguments.of("digest with leading whitespace", "  " + DIGEST, DIGEST),
            Arguments.of("uppercase digest preserved", DIGEST.toUpperCase(), DIGEST.toUpperCase())
        );
    }

    // Mutants killed: {40} quantifier loosened (39-char and 64-char runs must be
    // rejected — a SHA-256 served by mistake is not a SHA-1), non-hex accepted,
    // empty body producing a bogus digest instead of an error.
    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
        "",
        "not a checksum at all",
        "a9993e364706816aba3e25717850c26c9cd0d89",                          // 39 hex chars
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"  // 64 hex chars (SHA-256)
    })
    void parseSha1RejectsBodiesWithoutSha1Token(String body) {
        IOException ex = assertThrows(IOException.class,
            () -> JdbcDriverClassManager.parseSha1(body, "test"));
        assertTrue(ex.getMessage().contains("No SHA-1 digest"),
            "message must explain the rejection: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // sha1Hex — digest of a file
    // ------------------------------------------------------------------

    @Test
    void sha1HexMatchesKnownVector() throws Exception {
        // Mutants killed: wrong algorithm (MD5/SHA-256), hex formatting of the
        // wrong buffer, dropped bytes in the read loop. FIPS 180 test vector:
        // SHA-1("abc") = a9993e364706816aba3e25717850c26c9cd0d89d.
        Path file = cacheDir.resolve("vector.bin");
        Files.write(file, "abc".getBytes());
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
            JdbcDriverClassManager.sha1Hex(file));
    }

    @Test
    void sha1HexDigestsWholeFileBeyondOneBuffer() throws Exception {
        // Mutant killed: read loop mishandling multi-chunk files (e.g. only the
        // first 8192-byte buffer digested). 20000 bytes forces three reads.
        byte[] big = new byte[20000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) i;
        }
        Path file = cacheDir.resolve("big.bin");
        Files.write(file, big);
        assertEquals(sha1Of(big), JdbcDriverClassManager.sha1Hex(file),
            "digest must cover all bytes, not just the first buffer");
    }
}

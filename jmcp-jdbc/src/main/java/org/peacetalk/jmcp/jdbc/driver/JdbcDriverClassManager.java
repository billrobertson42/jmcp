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

package org.peacetalk.jmcp.jdbc.driver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.peacetalk.jmcp.jdbc.ProxyConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Driver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages dynamic loading of JDBC drivers from Maven Central
 */
public class JdbcDriverClassManager {
    private static final Logger LOG = LogManager.getLogger(JdbcDriverClassManager.class);

    // HikariCP version to use with all drivers (6.x for Java 11+, 7.x requires
    // Java 21+). Unpinned: verified via the repository checksum, because the
    // localhost-repo tests serve fake bytes that a real pin would reject.
    // Pinning it would need a test seam for the hikari artifact - see STATUS.md.
    private static final DriverArtifact HIKARI_CP = DriverArtifact.unpinned(
        new MavenCoordinates("com.zaxxer", "HikariCP", "7.0.2"));

    /**
     * Classpath resource defining the artifacts for each known database type:
     * a JSON object mapping type → array of artifact entries. An entry is
     * either an object {@code {"gav": "groupId:artifactId:version",
     * "sha256": "<64-hex pin>"}} (sha256 optional) or a bare
     * {@code "groupId:artifactId:version"} string (no pin). The first entry is
     * the JDBC driver itself; any further entries are companion jars the driver
     * needs on its classpath (e.g. sqlserver ships msal4j so Azure AD
     * authentication works out of the box — Azure SQL and plain SQL Server are
     * deliberately the same type). Use scripts/update-driver-pins.sh to
     * populate missing sha256 pins.
     */
    static final String KNOWN_DRIVERS_RESOURCE = "/known_drivers.json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration JAR_REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration CHECKSUM_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Leading 40-hex-char token of a Maven {@code .sha1} file, tolerating leading
     * whitespace and trailing {@code "  filename.jar"} decorations. The negative
     * lookahead rejects longer hex runs (e.g. a SHA-256 served by mistake).
     */
    private static final Pattern SHA1_TOKEN =
        Pattern.compile("^\\s*([0-9a-fA-F]{40})(?![0-9a-fA-F])");

    private final Path driverCacheDir;
    private final String repoBaseUrl;
    private final HttpClient httpClient;
    private final Map<String, DriverClassLoader> loadedDrivers;
    private final Map<String, List<DriverArtifact>> knownDrivers;

    public JdbcDriverClassManager(Path driverCacheDir) throws IOException {
        this(driverCacheDir, MavenCoordinates.MAVEN_CENTRAL_BASE);
    }

    /**
     * Creates a manager that downloads from an alternate repository base URL.
     * Exists so tests can point at a local HTTP server instead of Maven Central.
     */
    public JdbcDriverClassManager(Path driverCacheDir, String repoBaseUrl) throws IOException {
        this.driverCacheDir = driverCacheDir;
        this.repoBaseUrl = repoBaseUrl.endsWith("/") ? repoBaseUrl : repoBaseUrl + "/";
        this.loadedDrivers = new ConcurrentHashMap<>();
        this.knownDrivers = loadKnownDrivers();
        Files.createDirectories(driverCacheDir);

        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL);
        // The default selector only honors https.proxyHost for our https URLs;
        // ProxyConfig supplies a selector for the http.proxyHost compat and
        // HTTP_PROXY/HTTPS_PROXY env-var conventions the default would ignore.
        new ProxyConfig().proxySelector().ifPresent(builder::proxy);
        this.httpClient = builder.build();
    }

    /**
     * Load the known-driver definitions from {@link #KNOWN_DRIVERS_RESOURCE}.
     * The file ships inside the jmcp-jdbc jar, so any failure here is a build
     * or packaging defect — fail loudly rather than start with no drivers.
     */
    private static Map<String, List<DriverArtifact>> loadKnownDrivers() throws IOException {
        try (InputStream in = JdbcDriverClassManager.class.getResourceAsStream(KNOWN_DRIVERS_RESOURCE)) {
            if (in == null) {
                throw new IOException("Driver definition resource not found: " + KNOWN_DRIVERS_RESOURCE);
            }
            return parseKnownDrivers(new ObjectMapper().readTree(in));
        }
    }

    /**
     * Parse driver definitions (see {@link #KNOWN_DRIVERS_RESOURCE} for the
     * format). Public so tests can exercise the parsing without swapping the
     * shipped resource.
     *
     * @throws IOException on any malformed entry, naming the offender
     */
    public static Map<String, List<DriverArtifact>> parseKnownDrivers(JsonNode root) throws IOException {
        try {
            Map<String, List<DriverArtifact>> drivers = new HashMap<>();
            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                JsonNode array = entry.getValue();
                if (!array.isArray() || array.isEmpty()) {
                    throw new IOException("Driver definition for '" + entry.getKey()
                        + "' in " + KNOWN_DRIVERS_RESOURCE + " must be a non-empty array");
                }
                List<DriverArtifact> artifacts = new ArrayList<>();
                for (int i = 0; i < array.size(); i++) {
                    artifacts.add(parseArtifact(entry.getKey(), array.get(i)));
                }
                drivers.put(entry.getKey(), List.copyOf(artifacts));
            }
            return Map.copyOf(drivers);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid driver definition in " + KNOWN_DRIVERS_RESOURCE
                + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parse one artifact entry: either {@code {"gav": ..., "sha256": ...}}
     * (sha256 optional) or a bare {@code "groupId:artifactId:version"} string.
     */
    private static DriverArtifact parseArtifact(String databaseType, JsonNode node) throws IOException {
        if (node.isString()) {
            return DriverArtifact.unpinned(MavenCoordinates.parse(node.asString()));
        }
        if (node.isObject() && node.path("gav").isString()) {
            JsonNode sha256 = node.path("sha256");
            return new DriverArtifact(
                MavenCoordinates.parse(node.path("gav").asString()),
                sha256.isString() ? sha256.asString() : null);
        }
        throw new IOException("Driver artifact for '" + databaseType + "' in "
            + KNOWN_DRIVERS_RESOURCE + " must be a \"group:artifact:version\" string "
            + "or an object with a \"gav\" attribute, got: " + node);
    }

    /**
     * Get the known artifacts for a database type. The first entry is the JDBC
     * driver; any further entries are companion jars.
     */
    public List<DriverArtifact> getKnownDriver(String databaseType) {
        List<DriverArtifact> artifacts = knownDrivers.get(databaseType.toLowerCase());
        if (artifacts == null) {
            throw new IllegalArgumentException("Unknown database type: " + databaseType);
        }
        return artifacts;
    }

    /**
     * Load a driver by database type (postgresql, mysql, etc.)
     */
    public DriverClassLoader loadDriver(String databaseType) throws Exception {
        return loadDriverArtifacts(getKnownDriver(databaseType));
    }

    /**
     * Load a single-jar, unpinned driver by Maven coordinates
     */
    public DriverClassLoader loadDriver(MavenCoordinates coordinates) throws Exception {
        return loadDriver(List.of(coordinates));
    }

    /**
     * Load a driver made of one or more unpinned artifacts (verification falls
     * back to the repository's published checksums).
     */
    public DriverClassLoader loadDriver(List<MavenCoordinates> artifacts) throws Exception {
        return loadDriverArtifacts(artifacts.stream().map(DriverArtifact::unpinned).toList());
    }

    /**
     * Load a driver made of one or more artifacts. All jars are downloaded and
     * verified (against the sha256 pin when present, the repository checksum
     * otherwise) and loaded together in one isolated classloader.
     */
    public DriverClassLoader loadDriverArtifacts(List<DriverArtifact> artifacts) throws Exception {
        return loadedDrivers.computeIfAbsent(cacheKey(artifacts), k -> {
            try {
                List<Path> jarPaths = new ArrayList<>();
                for (DriverArtifact artifact : artifacts) {
                    jarPaths.add(downloadDriver(artifact));
                }
                jarPaths.add(downloadDriver(HIKARI_CP));
                return new DriverClassLoader(jarPaths);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load driver: " + artifacts, e);
            }
        });
    }

    /**
     * Cache key covering every artifact's coordinates, so two drivers sharing a
     * first artifact but differing in companions get distinct classloaders.
     * Pins are a verification detail, not part of the driver's identity.
     */
    private static String cacheKey(List<DriverArtifact> artifacts) {
        StringBuilder key = new StringBuilder();
        for (DriverArtifact artifact : artifacts) {
            if (key.length() > 0) {
                key.append('+');
            }
            key.append(artifact.coordinates());
        }
        return key.toString();
    }

    /**
     * Download driver JAR from Maven Central if not cached.
     *
     * <p>The jar is downloaded to a temp file in the cache directory and verified
     * — against the artifact's pinned SHA-256 when one is present (trust anchored
     * in known_drivers.json), otherwise against the repository's published
     * {@code .sha1} companion file (transfer integrity only) — and only then is
     * it atomically moved to its final cache path, so a partial or tampered
     * download is never cached.
     */
    private Path downloadDriver(DriverArtifact artifact) throws IOException, InterruptedException {
        MavenCoordinates coordinates = artifact.coordinates();
        String fileName = coordinates.artifactId() + "-" + coordinates.version() + ".jar";
        Path targetPath = driverCacheDir.resolve(fileName);

        if (Files.exists(targetPath)) {
            return targetPath;
        }

        String jarUrl = repoBaseUrl + coordinates.toPath();
        LOG.info("Downloading driver from: {}", jarUrl);

        Path tempFile = Files.createTempFile(driverCacheDir, fileName + ".", ".part");
        try {
            download(jarUrl, tempFile);

            if (artifact.sha256() != null) {
                String actualSha256 = sha256Hex(tempFile);
                if (!artifact.sha256().equalsIgnoreCase(actualSha256)) {
                    throw new IOException("SHA-256 pin mismatch for " + jarUrl
                        + ": pinned " + artifact.sha256()
                        + " but downloaded file has " + actualSha256);
                }
            } else {
                String expectedSha1 = fetchExpectedSha1(jarUrl + ".sha1");
                String actualSha1 = sha1Hex(tempFile);
                if (!expectedSha1.equalsIgnoreCase(actualSha1)) {
                    throw new IOException("SHA-1 mismatch for " + jarUrl
                        + ": repository says " + expectedSha1
                        + " but downloaded file has " + actualSha1);
                }
            }

            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException e) {
                // Another process published the jar between our exists-check and
                // this move (the cache dir may be shared by multiple servers, so
                // in-JVM locking could not prevent this). POSIX rename replaces
                // silently; on filesystems that throw instead, the file that won
                // is the same verified artifact - treat as success.
                if (!Files.exists(targetPath)) {
                    throw e;
                }
            }
        } finally {
            // No-op after a successful move; removes partial/unverified data otherwise.
            Files.deleteIfExists(tempFile);
        }

        LOG.info("Driver downloaded to: {}", targetPath);
        return targetPath;
    }

    /**
     * Download {@code url} into {@code dest}, failing on any non-200 status.
     */
    private void download(String url, Path dest) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(JAR_REQUEST_TIMEOUT)
            .GET()
            .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(dest));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
        }
    }

    /**
     * Fetch the repository's {@code .sha1} companion file and extract the digest.
     * Maven Central always publishes one, so any failure here fails the download.
     */
    private String fetchExpectedSha1(String checksumUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(checksumUrl))
            .timeout(CHECKSUM_REQUEST_TIMEOUT)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching checksum " + checksumUrl);
        }
        return parseSha1(response.body(), checksumUrl);
    }

    /**
     * Extract the leading 40-hex-char SHA-1 token from a {@code .sha1} file body
     * ({@code "<hex>"}, {@code "<hex>  filename.jar"}, trailing whitespace, and
     * upper- or lower-case hex are all accepted).
     *
     * @throws IOException if the body contains no SHA-1 digest
     */
    public static String parseSha1(String body, String source) throws IOException {
        if (body != null) {
            Matcher matcher = SHA1_TOKEN.matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        String shown = body == null ? "null"
            : body.length() > 80 ? body.substring(0, 80) + "..." : body;
        throw new IOException("No SHA-1 digest found in checksum from " + source + ": '" + shown + "'");
    }

    /**
     * Compute the lower-case hex SHA-1 digest of a file.
     */
    public static String sha1Hex(Path file) throws IOException {
        return digestHex("SHA-1", file);
    }

    /**
     * Compute the lower-case hex SHA-256 digest of a file.
     */
    public static String sha256Hex(Path file) throws IOException {
        return digestHex("SHA-256", file);
    }

    private static String digestHex(String algorithm, Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " MessageDigest unavailable", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Unload a driver and close its classloader
     */
    public void unloadDriver(String databaseType) throws Exception {
        unloadDriverArtifacts(getKnownDriver(databaseType));
    }

    /**
     * Unload a single-jar driver by coordinates
     */
    public void unloadDriver(MavenCoordinates coordinates) throws Exception {
        unloadDriver(List.of(coordinates));
    }

    /**
     * Unload a driver by its full coordinates list
     */
    public void unloadDriver(List<MavenCoordinates> artifacts) throws Exception {
        unloadDriverArtifacts(artifacts.stream().map(DriverArtifact::unpinned).toList());
    }

    /**
     * Unload a driver by its full artifact list
     */
    public void unloadDriverArtifacts(List<DriverArtifact> artifacts) throws Exception {
        DriverClassLoader classLoader = loadedDrivers.remove(cacheKey(artifacts));
        if (classLoader != null) {
            classLoader.close();
        }
    }

    /**
     * Isolated ClassLoader for the driver's jars (driver, companion artifacts,
     * HikariCP). This ensures the driver and connection pool are completely
     * isolated.
     */
    public static class DriverClassLoader extends URLClassLoader {
        public DriverClassLoader(List<Path> jarPaths) throws Exception {
            super(toUrls(jarPaths), ClassLoader.getPlatformClassLoader());
        }

        private static URL[] toUrls(List<Path> jarPaths) throws Exception {
            URL[] urls = new URL[jarPaths.size()];
            for (int i = 0; i < jarPaths.size(); i++) {
                urls[i] = jarPaths.get(i).toUri().toURL();
            }
            return urls;
        }

        /**
         * Load the JDBC driver class
         */
        public Driver loadDriverClass(String driverClassName) throws Exception {
            Class<?> driverClass = loadClass(driverClassName);
            return (Driver) driverClass.getDeclaredConstructor().newInstance();
        }
    }
}

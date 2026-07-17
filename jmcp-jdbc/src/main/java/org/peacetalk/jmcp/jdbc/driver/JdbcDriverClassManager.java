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

    // HikariCP version to use with all drivers (6.x for Java 11+, 7.x requires Java 21+)
    private static final MavenCoordinates HIKARI_CP =
        new MavenCoordinates("com.zaxxer", "HikariCP", "7.0.2");

    /**
     * Artifacts per database type. The first entry is the JDBC driver itself;
     * any further entries are companion jars the driver needs on its classpath.
     * sqlserver ships msal4j so Azure AD authentication works out of the box
     * (Azure SQL and plain SQL Server are deliberately the same type).
     */
    private static final Map<String, List<MavenCoordinates>> KNOWN_DRIVERS = Map.ofEntries(
        Map.entry("postgresql", List.of(new MavenCoordinates("org.postgresql", "postgresql", "42.7.8"))),
        Map.entry("mysql", List.of(new MavenCoordinates("com.mysql", "mysql-connector-j", "9.5.0"))),
        Map.entry("mariadb", List.of(new MavenCoordinates("org.mariadb.jdbc", "mariadb-java-client", "3.5.7"))),
        Map.entry("oracle", List.of(new MavenCoordinates("com.oracle.database.jdbc", "ojdbc11", "23.7.0.25.01"))),
        Map.entry("sqlserver", List.of(
            new MavenCoordinates("com.microsoft.sqlserver", "mssql-jdbc", "13.2.1.jre11"),
            new MavenCoordinates("com.microsoft.azure", "msal4j", "1.25.0"))),
        Map.entry("h2", List.of(new MavenCoordinates("com.h2database", "h2", "2.4.240"))),
        Map.entry("sqlite", List.of(new MavenCoordinates("org.xerial", "sqlite-jdbc", "3.51.1.0")))
    );

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
     * Get the known artifacts for a database type. The first entry is the JDBC
     * driver; any further entries are companion jars.
     */
    public List<MavenCoordinates> getKnownDriver(String databaseType) {
        List<MavenCoordinates> artifacts = KNOWN_DRIVERS.get(databaseType.toLowerCase());
        if (artifacts == null) {
            throw new IllegalArgumentException("Unknown database type: " + databaseType);
        }
        return artifacts;
    }

    /**
     * Load a driver by database type (postgresql, mysql, etc.)
     */
    public DriverClassLoader loadDriver(String databaseType) throws Exception {
        return loadDriver(getKnownDriver(databaseType));
    }

    /**
     * Load a single-jar driver by Maven coordinates
     */
    public DriverClassLoader loadDriver(MavenCoordinates coordinates) throws Exception {
        return loadDriver(List.of(coordinates));
    }

    /**
     * Load a driver made of one or more artifacts. All jars are downloaded
     * (and SHA-1 verified) and loaded together in one isolated classloader.
     */
    public DriverClassLoader loadDriver(List<MavenCoordinates> artifacts) throws Exception {
        return loadedDrivers.computeIfAbsent(cacheKey(artifacts), k -> {
            try {
                List<Path> jarPaths = new ArrayList<>();
                for (MavenCoordinates coordinates : artifacts) {
                    jarPaths.add(downloadDriver(coordinates));
                }
                jarPaths.add(downloadDriver(HIKARI_CP));
                return new DriverClassLoader(jarPaths);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load driver: " + artifacts, e);
            }
        });
    }

    /**
     * Cache key covering every artifact of the driver, so two drivers sharing a
     * first artifact but differing in companions get distinct classloaders.
     */
    private static String cacheKey(List<MavenCoordinates> artifacts) {
        StringBuilder key = new StringBuilder();
        for (MavenCoordinates coordinates : artifacts) {
            if (key.length() > 0) {
                key.append('+');
            }
            key.append(coordinates);
        }
        return key.toString();
    }

    /**
     * Download driver JAR from Maven Central if not cached.
     *
     * <p>The jar is downloaded to a temp file in the cache directory, its SHA-1 is
     * verified against the repository's {@code .sha1} companion file, and only then
     * is it atomically moved to its final cache path — so a partial or tampered
     * download is never cached.
     */
    private Path downloadDriver(MavenCoordinates coordinates) throws IOException, InterruptedException {
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

            String expectedSha1 = fetchExpectedSha1(jarUrl + ".sha1");
            String actualSha1 = sha1Hex(tempFile);
            if (!expectedSha1.equalsIgnoreCase(actualSha1)) {
                throw new IOException("SHA-1 mismatch for " + jarUrl
                    + ": repository says " + expectedSha1
                    + " but downloaded file has " + actualSha1);
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
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 MessageDigest unavailable", e);
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
        unloadDriver(getKnownDriver(databaseType));
    }

    /**
     * Unload a single-jar driver by coordinates
     */
    public void unloadDriver(MavenCoordinates coordinates) throws Exception {
        unloadDriver(List.of(coordinates));
    }

    /**
     * Unload a driver by its full artifact list
     */
    public void unloadDriver(List<MavenCoordinates> artifacts) throws Exception {
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

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
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages dynamic loading of JDBC drivers from Maven Central
 */
public class JdbcDriverManager {
    private static final Logger LOG = LogManager.getLogger(JdbcDriverManager.class);

    // HikariCP version to use with all drivers (6.x for Java 11+, 7.x requires Java 21+)
    private static final MavenCoordinates HIKARI_CP =
        new MavenCoordinates("com.zaxxer", "HikariCP", "7.0.2");

    private static final Map<String, MavenCoordinates> KNOWN_DRIVERS = Map.ofEntries(
        Map.entry("postgresql", new MavenCoordinates("org.postgresql", "postgresql", "42.7.8")),
        Map.entry("mysql", new MavenCoordinates("com.mysql", "mysql-connector-j", "9.5.0")),
        Map.entry("mariadb", new MavenCoordinates("org.mariadb.jdbc", "mariadb-java-client", "3.5.7")),
        Map.entry("oracle", new MavenCoordinates("com.oracle.database.jdbc", "ojdbc11", "23.7.0.25.01")),
        Map.entry("sqlserver", new MavenCoordinates("com.microsoft.sqlserver", "mssql-jdbc", "13.2.1.jre11")),
        Map.entry("h2", new MavenCoordinates("com.h2database", "h2", "2.4.240")),
        Map.entry("sqlite", new MavenCoordinates("org.xerial", "sqlite-jdbc", "3.51.1.0"))
    );

    private final Path driverCacheDir;
    private final Map<String, DriverClassLoader> loadedDrivers;
    private final ProxyConfig proxyConfig;

    public JdbcDriverManager(Path driverCacheDir) throws IOException {
        this.driverCacheDir = driverCacheDir;
        this.loadedDrivers = new ConcurrentHashMap<>();
        this.proxyConfig = new ProxyConfig();
        Files.createDirectories(driverCacheDir);
    }

    /**
     * Get known driver coordinates by database type
     */
    public MavenCoordinates getKnownDriver(String databaseType) {
        MavenCoordinates coordinates = KNOWN_DRIVERS.get(databaseType.toLowerCase());
        if (coordinates == null) {
            throw new IllegalArgumentException("Unknown database type: " + databaseType);
        }
        return coordinates;
    }

    /**
     * Load a driver by database type (postgresql, mysql, etc.)
     */
    public DriverClassLoader loadDriver(String databaseType) throws Exception {
        MavenCoordinates coordinates = getKnownDriver(databaseType);
        return loadDriver(coordinates);
    }

    /**
     * Load a driver by Maven coordinates
     */
    public DriverClassLoader loadDriver(MavenCoordinates coordinates) throws Exception {
        String key = coordinates.toString();

        return loadedDrivers.computeIfAbsent(key, k -> {
            try {
                Path driverJarPath = downloadDriver(coordinates);
                Path hikariJarPath = downloadDriver(HIKARI_CP);
                return new DriverClassLoader(driverJarPath, hikariJarPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load driver: " + coordinates, e);
            }
        });
    }

    /**
     * Download driver JAR from Maven Central if not cached
     */
    private Path downloadDriver(MavenCoordinates coordinates) throws IOException {
        String fileName = coordinates.artifactId() + "-" + coordinates.version() + ".jar";
        Path targetPath = driverCacheDir.resolve(fileName);

        if (Files.exists(targetPath)) {
            return targetPath;
        }

        String url = coordinates.getMavenCentralUrl();
        LOG.info("Downloading driver from: {}", url);

        URLConnection conn = null;

        String proxyHost = proxyConfig.getProxyHost();
        if(proxyHost != null ) {
            String proxyPortStr = proxyConfig.getProxyPort();
            int proxyPort = proxyPortStr != null ? Integer.parseInt(proxyPortStr) : 80;
            InetSocketAddress proxyAddress = new InetSocketAddress(proxyHost, proxyPort);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyAddress);
            conn = new URL(url).openConnection(proxy);
        }
        else {
            conn = new URL(url).openConnection();
        }

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        LOG.info("Driver downloaded to: {}", targetPath);
        return targetPath;
    }


    /**
     * Unload a driver and close its classloader
     */
    public void unloadDriver(String databaseType) throws Exception {
        MavenCoordinates coordinates = getKnownDriver(databaseType);
        if (coordinates != null) {
            unloadDriver(coordinates);
        }
    }

    /**
     * Unload a driver by coordinates
     */
    public void unloadDriver(MavenCoordinates coordinates) throws Exception {
        String key = coordinates.toString();
        DriverClassLoader classLoader = loadedDrivers.remove(key);
        if (classLoader != null) {
            classLoader.close();
        }
    }

    /**
     * Isolated ClassLoader for JDBC driver and HikariCP
     * This ensures the driver and connection pool are completely isolated
     */
    public static class DriverClassLoader extends URLClassLoader {
        public DriverClassLoader(Path driverJarPath, Path hikariJarPath) throws Exception {
            super(new URL[]{
                driverJarPath.toUri().toURL(),
                hikariJarPath.toUri().toURL()
            }, ClassLoader.getPlatformClassLoader());
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

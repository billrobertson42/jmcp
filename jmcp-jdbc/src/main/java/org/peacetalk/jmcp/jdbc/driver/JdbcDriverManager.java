package org.peacetalk.jmcp.jdbc.driver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic loading of JDBC drivers from Maven Central
 */
public class JdbcDriverManager {

    private static final Map<String, DriverCoordinates> KNOWN_DRIVERS = Map.ofEntries(
        Map.entry("postgresql", new DriverCoordinates("org.postgresql", "postgresql", "42.7.1")),
        Map.entry("mysql", new DriverCoordinates("com.mysql", "mysql-connector-j", "8.3.0")),
        Map.entry("mariadb", new DriverCoordinates("org.mariadb.jdbc", "mariadb-java-client", "3.3.2")),
        Map.entry("oracle", new DriverCoordinates("com.oracle.database.jdbc", "ojdbc11", "23.3.0.23.09")),
        Map.entry("sqlserver", new DriverCoordinates("com.microsoft.sqlserver", "mssql-jdbc", "12.6.0.jre11")),
        Map.entry("h2", new DriverCoordinates("com.h2database", "h2", "2.2.224")),
        Map.entry("derby", new DriverCoordinates("org.apache.derby", "derby", "10.17.1.0")),
        Map.entry("sqlite", new DriverCoordinates("org.xerial", "sqlite-jdbc", "3.45.0.0"))
    );

    private final Path driverCacheDir;
    private final Map<String, DriverClassLoader> loadedDrivers;

    public JdbcDriverManager(Path driverCacheDir) throws IOException {
        this.driverCacheDir = driverCacheDir;
        this.loadedDrivers = new ConcurrentHashMap<>();
        Files.createDirectories(driverCacheDir);
    }

    /**
     * Get known driver coordinates by database type
     */
    public DriverCoordinates getKnownDriver(String databaseType) {
        return KNOWN_DRIVERS.get(databaseType.toLowerCase());
    }

    /**
     * Load a driver by database type (postgresql, mysql, etc.)
     */
    public DriverClassLoader loadDriver(String databaseType) throws Exception {
        DriverCoordinates coordinates = getKnownDriver(databaseType);
        if (coordinates == null) {
            throw new IllegalArgumentException("Unknown database type: " + databaseType);
        }
        return loadDriver(coordinates);
    }

    /**
     * Load a driver by Maven coordinates
     */
    public DriverClassLoader loadDriver(DriverCoordinates coordinates) throws Exception {
        String key = coordinates.toString();

        return loadedDrivers.computeIfAbsent(key, k -> {
            try {
                Path jarPath = downloadDriver(coordinates);
                return new DriverClassLoader(jarPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load driver: " + coordinates, e);
            }
        });
    }

    /**
     * Download driver JAR from Maven Central if not cached
     */
    private Path downloadDriver(DriverCoordinates coordinates) throws IOException {
        String fileName = coordinates.artifactId() + "-" + coordinates.version() + ".jar";
        Path targetPath = driverCacheDir.resolve(fileName);

        if (Files.exists(targetPath)) {
            return targetPath;
        }

        String url = coordinates.getMavenCentralUrl();
        System.err.println("Downloading driver from: " + url);

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        System.err.println("Driver downloaded to: " + targetPath);
        return targetPath;
    }

    /**
     * Unload a driver and close its classloader
     */
    public void unloadDriver(String databaseType) throws Exception {
        DriverCoordinates coordinates = getKnownDriver(databaseType);
        if (coordinates != null) {
            unloadDriver(coordinates);
        }
    }

    /**
     * Unload a driver by coordinates
     */
    public void unloadDriver(DriverCoordinates coordinates) throws Exception {
        String key = coordinates.toString();
        DriverClassLoader classLoader = loadedDrivers.remove(key);
        if (classLoader != null) {
            classLoader.close();
        }
    }

    /**
     * Isolated ClassLoader for JDBC driver
     */
    public static class DriverClassLoader extends URLClassLoader {
        public DriverClassLoader(Path jarPath) throws Exception {
            super(new URL[]{jarPath.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
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


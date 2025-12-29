package org.peacetalk.jmcp.jdbc.driver;

/**
 * Maven coordinates for a JDBC driver
 */
public record MavenCoordinates(
    String groupId,
    String artifactId,
    String version
) {
    public String toPath() {
        return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
    }

    public String getMavenCentralUrl() {
        return "https://repo1.maven.org/maven2/" + toPath();
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}



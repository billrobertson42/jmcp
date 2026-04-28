package org.peacetalk.jmcp.jdbc;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides HTTP proxy configuration by resolving proxy settings from Java system
 * properties ({@code http.proxyHost}, {@code http.proxyPort}) or, as a fallback,
 * from the {@code HTTP_PROXY} / {@code HTTPS_PROXY} environment variables.
 *
 * <p>The lookup functions for system properties and environment variables are
 * injectable, making this class easy to testable
 */
public class ProxyConfig {

    private final Function<String, String> sys;
    private final Function<String, String> env;

    /**
     * Creates a {@code ProxyConfig} with custom lookup functions for system
     * properties and environment variables. This is for testing.
     *
     * @param sys function that resolves a system property by name
     * @param env function that resolves an environment variable by name
     */
    public ProxyConfig(Function<String, String> sys, Function<String, String> env) {
        this.sys = sys;
        this.env = env;
    }

    /**
     * Creates a {@code ProxyConfig} using the standard JVM system properties.
     * This is for normal use.
     *
     * ({@link System#getProperty}) and environment variables ({@link System#getenv}).
     */
    public ProxyConfig() {
        this(System::getProperty, System::getenv);
    }

    /**
     * Returns the proxy host to use for HTTP connections.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Java system property {@code http.proxyHost}</li>
     *   <li>Host portion parsed from the {@code HTTP_PROXY} / {@code HTTPS_PROXY}
     *       environment variable (e.g. {@code http://proxy.example.com:8080})</li>
     * </ol>
     *
     * @return the proxy host, or {@code null} if no proxy host is configured
     */
    public String getProxyHost() {
        String proxyHost = sys.apply("http.proxyHost");
        if (proxyHost == null) {
            String fromEnv = getHttpProxyEnvVariable();
            if (fromEnv != null) {
                Matcher matcher = Pattern.compile("^https?://([^:]+).*$").matcher(fromEnv);
                if (matcher.matches()) {
                    proxyHost = matcher.group(1);
                }
            }
        }
        return proxyHost;
    }

    /**
     * Returns the proxy port to use for HTTP connections.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Java system property {@code http.proxyPort}</li>
     *   <li>Port portion parsed from the {@code HTTP_PROXY} / {@code HTTPS_PROXY}
     *       environment variable (e.g. {@code http://proxy.example.com:8080})</li>
     * </ol>
     *
     * @return the proxy port as a string, or {@code null} if no proxy port is configured
     */
    public String getProxyPort() {
        String proxyPort = sys.apply("http.proxyPort");
        if (proxyPort == null) {
            String fromEnv = getHttpProxyEnvVariable();
            if (fromEnv != null) {
                Matcher matcher = Pattern.compile("^https?://[^:]+:([0-9]+)$").matcher(fromEnv);
                if (matcher.matches()) {
                    proxyPort = matcher.group(1);
                }
            }
        }
        return proxyPort;
    }

    /**
     * Returns the value of the {@code HTTP_PROXY} environment variable, falling back
     * to {@code HTTPS_PROXY} if {@code HTTP_PROXY} is not set.
     *
     * @return the proxy URL string from the environment, or {@code null} if neither
     *         variable is set
     */
    public String getHttpProxyEnvVariable() {
        String http_proxy =  getenv("HTTP_PROXY");
        if (http_proxy == null) {
            http_proxy = getenv("HTTPS_PROXY");
        }
        return http_proxy;
    }

    /**
     * Looks up an environment variable by name using the configured lookup function.
     * The lookup is attempted first with the name converted to upper-case (e.g.
     * {@code HTTP_PROXY}), and, if not found, retried with the name converted to
     * lower-case (e.g. {@code http_proxy}).  This handles both the Windows/macOS
     * convention of upper-case proxy variables and the common Unix convention of
     * lower-case ones.
     *
     * @param name the environment variable name (case-insensitive)
     * @return the value of the environment variable, or {@code null} if not set
     */
    public String getenv(String name) {
        String value = env.apply(name.toUpperCase());
        if (value == null) {
            value = env.apply(name.toLowerCase());
        }
        return value;
    }

}

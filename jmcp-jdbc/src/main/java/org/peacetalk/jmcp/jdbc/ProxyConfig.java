package org.peacetalk.jmcp.jdbc;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supplies the HTTP proxy override for {@code java.net.http.HttpClient}.
 *
 * <p>The JDK's default {@link ProxySelector} (used by {@code HttpClient} when no
 * explicit selector is set) applies system properties <em>per protocol</em>: it
 * consults {@code https.proxyHost} for the https URLs this client fetches, and
 * {@code http.proxyHost} only for plain-http URLs. This class covers the two
 * conventions the default selector would miss for https traffic:
 * {@code http.proxyHost}/{@code http.proxyPort} (honored by the pre-HttpClient
 * implementation, so kept for compatibility) and the {@code HTTP_PROXY} /
 * {@code HTTPS_PROXY} (and lower-case) environment variables.
 *
 * <p>The lookup functions for system properties and environment variables are
 * injectable, making this class easy to test.
 */
public class ProxyConfig {

    /**
     * Matches proxy env-var values of the form {@code http://host[:port][/]} or
     * {@code https://host[:port][/]}. Group 1 is the host, group 2 the optional port.
     */
    private static final Pattern ENV_PROXY_URL =
        Pattern.compile("^https?://([^:/\\s]+)(?::([0-9]+))?/?$");

    /** Port assumed when the proxy env var omits one. */
    private static final int DEFAULT_PROXY_PORT = 80;

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
     * Creates a {@code ProxyConfig} using the standard JVM system properties
     * ({@link System#getProperty}) and environment variables ({@link System#getenv}).
     * This is for normal use.
     */
    public ProxyConfig() {
        this(System::getProperty, System::getenv);
    }

    /**
     * Returns the {@link ProxySelector} to install on an {@code HttpClient}, if any.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code https.proxyHost} set → empty: the default selector already
     *       applies it to this client's https URLs, and it must win over the
     *       fallbacks below.</li>
     *   <li>{@code http.proxyHost} set → a selector built from
     *       {@code http.proxyHost}/{@code http.proxyPort}. The default selector
     *       ignores these for https URLs, but the pre-HttpClient implementation
     *       honored them, so they keep working.</li>
     *   <li>{@code HTTP_PROXY}/{@code HTTPS_PROXY} environment variables, which
     *       the JDK ignores entirely.</li>
     *   <li>Nothing configured → empty (direct connections).</li>
     * </ol>
     *
     * @return the proxy selector to install, or empty to keep the client default
     */
    public Optional<ProxySelector> proxySelector() {
        if (sys.apply("https.proxyHost") != null) {
            return Optional.empty();
        }
        Optional<InetSocketAddress> fromSysProps = sysPropProxyAddress();
        if (fromSysProps.isPresent()) {
            return fromSysProps.map(ProxySelector::of);
        }
        return envProxyAddress().map(ProxySelector::of);
    }

    /**
     * Parses the proxy address from the {@code http.proxyHost} /
     * {@code http.proxyPort} system properties. A missing port defaults to
     * {@value #DEFAULT_PROXY_PORT}; a non-numeric port yields empty.
     *
     * @return the unresolved proxy address, or empty if {@code http.proxyHost}
     *         is not set or the port is unparseable
     */
    public Optional<InetSocketAddress> sysPropProxyAddress() {
        String host = sys.apply("http.proxyHost");
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String portStr = sys.apply("http.proxyPort");
        int port = DEFAULT_PROXY_PORT;
        if (portStr != null) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(InetSocketAddress.createUnresolved(host, port));
    }

    /**
     * Parses the proxy address from the {@code HTTP_PROXY} / {@code HTTPS_PROXY}
     * environment variables (upper- or lower-case). A value without an explicit
     * port defaults to port {@value #DEFAULT_PROXY_PORT}; a value that is not an
     * {@code http(s)://host[:port]} URL yields empty.
     *
     * @return the unresolved proxy address, or empty if none is configured
     */
    public Optional<InetSocketAddress> envProxyAddress() {
        String fromEnv = getHttpProxyEnvVariable();
        if (fromEnv == null) {
            return Optional.empty();
        }
        Matcher matcher = ENV_PROXY_URL.matcher(fromEnv);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String host = matcher.group(1);
        int port = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : DEFAULT_PROXY_PORT;
        return Optional.of(InetSocketAddress.createUnresolved(host, port));
    }

    /**
     * Returns the value of the {@code HTTP_PROXY} environment variable, falling back
     * to {@code HTTPS_PROXY} if {@code HTTP_PROXY} is not set.
     *
     * @return the proxy URL string from the environment, or {@code null} if neither
     *         variable is set
     */
    public String getHttpProxyEnvVariable() {
        String httpProxy = getenv("HTTP_PROXY");
        if (httpProxy == null) {
            httpProxy = getenv("HTTPS_PROXY");
        }
        return httpProxy;
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

package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.peacetalk.jmcp.jdbc.ProxyConfig;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyConfigTest {

    private ProxyConfig config(Map<String, String> sysProps, Map<String, String> envVars) {
        return new ProxyConfig(sysProps::get, envVars::get);
    }

    // ------------------------------------------------------------------
    // envProxyAddress — parsing HTTP_PROXY/HTTPS_PROXY into an address
    // ------------------------------------------------------------------

    // Would fail if: wrong regex group for host/port, default port changed from 80,
    // dropped HTTPS_PROXY fallback, dropped lowercase env-var lookup, regex accepting
    // non-http(s) schemes or garbage values.
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("envProxyAddressCases")
    void envProxyAddress(String description, Map<String, String> env, InetSocketAddress expected) {
        Optional<InetSocketAddress> actual = config(Map.of(), env).envProxyAddress();
        assertEquals(Optional.ofNullable(expected), actual, description);
    }

    static Stream<Arguments> envProxyAddressCases() {
        return Stream.of(
            Arguments.of("HTTP_PROXY uppercase with port",
                Map.of("HTTP_PROXY", "http://proxy.example.com:8080"),
                addr("proxy.example.com", 8080)),
            Arguments.of("HTTP_PROXY without port defaults to 80",
                Map.of("HTTP_PROXY", "http://proxy.example.com"),
                addr("proxy.example.com", 80)),
            Arguments.of("HTTP_PROXY with trailing slash",
                Map.of("HTTP_PROXY", "http://proxy.example.com:8080/"),
                addr("proxy.example.com", 8080)),
            Arguments.of("http_proxy lowercase with port",
                Map.of("http_proxy", "http://proxy.example.com:8080"),
                addr("proxy.example.com", 8080)),
            Arguments.of("HTTPS_PROXY uppercase fallback",
                Map.of("HTTPS_PROXY", "https://secure.example.com:443"),
                addr("secure.example.com", 443)),
            Arguments.of("https_proxy lowercase fallback",
                Map.of("https_proxy", "https://secure.example.com:443"),
                addr("secure.example.com", 443)),
            Arguments.of("HTTP_PROXY preferred over HTTPS_PROXY",
                mapOf("HTTP_PROXY", "http://http.example.com:80",
                      "HTTPS_PROXY", "https://https.example.com:443"),
                addr("http.example.com", 80)),
            Arguments.of("nothing configured",
                Map.of(), null),
            Arguments.of("HTTP_PROXY not a URL",
                Map.of("HTTP_PROXY", "not-a-url"), null),
            Arguments.of("HTTP_PROXY wrong scheme",
                Map.of("HTTP_PROXY", "ftp://proxy.example.com:21"), null),
            Arguments.of("HTTP_PROXY empty string",
                Map.of("HTTP_PROXY", ""), null),
            Arguments.of("HTTP_PROXY non-numeric port",
                Map.of("HTTP_PROXY", "http://proxy.example.com:abc"), null)
        );
    }

    // ------------------------------------------------------------------
    // proxySelector — what gets installed on the HttpClient
    // ------------------------------------------------------------------

    @Test
    void proxySelectorEmptyWhenHttpsProxyHostSet() {
        // Would fail if the https.proxyHost check were removed: the default selector
        // already applies https.proxyHost to this client's https URLs, so an
        // env-var (or http.proxyHost) selector must not override it.
        Optional<ProxySelector> selector = config(
            Map.of("https.proxyHost", "sys.example.com"),
            Map.of("HTTP_PROXY", "http://env.example.com:8080")
        ).proxySelector();
        assertEquals(Optional.empty(), selector,
            "https.proxyHost must be left to the JDK default selector");
    }

    @Test
    void proxySelectorBuiltFromHttpProxySystemProperties() {
        // Would fail if: the http.proxyHost compat branch were dropped (the default
        // selector ignores http.proxyHost for https URLs, so downloads would go
        // DIRECT - the regression this branch fixes), or the
        // system-property-over-env-var precedence were flipped.
        ProxySelector selector = config(
            Map.of("http.proxyHost", "sys.example.com", "http.proxyPort", "3128"),
            Map.of("HTTP_PROXY", "http://env.example.com:8080")
        ).proxySelector()
            .orElseThrow(() -> new AssertionError(
                "http.proxyHost is set; a compat selector must be installed"));

        List<Proxy> proxies = selector.select(URI.create("https://repo1.maven.org/maven2/a/b/c.jar"));
        assertEquals(1, proxies.size(), "exactly one proxy expected");
        assertEquals(addr("sys.example.com", 3128), proxies.get(0).address(),
            "system properties must win over the env-var proxy");
    }

    // Would fail if: http.proxyPort were ignored (default 80 always used), the
    // default port changed, an unset host stopped yielding empty, or a non-numeric
    // port crashed or produced a bogus address instead of empty.
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("sysPropProxyAddressCases")
    void sysPropProxyAddress(String description, Map<String, String> sysProps, InetSocketAddress expected) {
        Optional<InetSocketAddress> actual = config(sysProps, Map.of()).sysPropProxyAddress();
        assertEquals(Optional.ofNullable(expected), actual, description);
    }

    static Stream<Arguments> sysPropProxyAddressCases() {
        return Stream.of(
            Arguments.of("host and port",
                mapOf("http.proxyHost", "proxy.example.com", "http.proxyPort", "3128"),
                addr("proxy.example.com", 3128)),
            Arguments.of("host without port defaults to 80",
                Map.of("http.proxyHost", "proxy.example.com"),
                addr("proxy.example.com", 80)),
            Arguments.of("nothing configured",
                Map.of(), null),
            Arguments.of("blank host",
                Map.of("http.proxyHost", "  "), null),
            Arguments.of("non-numeric port",
                mapOf("http.proxyHost", "proxy.example.com", "http.proxyPort", "abc"),
                null)
        );
    }

    @Test
    void proxySelectorEmptyWhenNothingConfigured() {
        // Would fail if a non-empty selector were returned when no proxy is configured.
        assertEquals(Optional.empty(), config(Map.of(), Map.of()).proxySelector());
    }

    @Test
    void proxySelectorRoutesThroughEnvVarProxy() {
        // Would fail if the env-var address were not passed to ProxySelector.of
        // (wrong host/port, or empty returned despite HTTP_PROXY being set).
        ProxySelector selector = config(Map.of(), Map.of("HTTP_PROXY", "http://proxy.example.com:8080"))
            .proxySelector()
            .orElseThrow(() -> new AssertionError("HTTP_PROXY is set; selector must be present"));

        List<Proxy> proxies = selector.select(URI.create("https://repo1.maven.org/maven2/a/b/c.jar"));
        assertEquals(1, proxies.size(), "exactly one proxy expected");
        assertEquals(Proxy.Type.HTTP, proxies.get(0).type());
        assertEquals(addr("proxy.example.com", 8080), proxies.get(0).address(),
            "selector must route through the env-var proxy address");
    }

    // ------------------------------------------------------------------
    // getHttpProxyEnvVariable
    // ------------------------------------------------------------------

    // Would fail if: the HTTPS_PROXY fallback were removed, or precedence between
    // the two variables were flipped.
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("httpProxyEnvCases")
    void getHttpProxyEnvVariable(String description, Map<String, String> env, String expected) {
        assertEquals(expected, config(Map.of(), env).getHttpProxyEnvVariable());
    }

    static Stream<Arguments> httpProxyEnvCases() {
        return Stream.of(
            Arguments.of("HTTP_PROXY uppercase",
                Map.of("HTTP_PROXY", "http://proxy.example.com:8080"),
                "http://proxy.example.com:8080"),
            Arguments.of("http_proxy lowercase",
                Map.of("http_proxy", "http://proxy.example.com:8080"),
                "http://proxy.example.com:8080"),
            Arguments.of("HTTPS_PROXY uppercase fallback",
                Map.of("HTTPS_PROXY", "https://secure.example.com:443"),
                "https://secure.example.com:443"),
            Arguments.of("https_proxy lowercase fallback",
                Map.of("https_proxy", "https://secure.example.com:443"),
                "https://secure.example.com:443"),
            Arguments.of("HTTP_PROXY preferred over HTTPS_PROXY",
                mapOf("HTTP_PROXY", "http://http.example.com:80",
                      "HTTPS_PROXY", "https://https.example.com:443"),
                "http://http.example.com:80"),
            Arguments.of("neither set", Map.of(), null)
        );
    }

    // ------------------------------------------------------------------
    // getenv
    // ------------------------------------------------------------------

    // Would fail if: the upper-case or lower-case retry were dropped, or the name
    // were looked up verbatim instead of case-normalized.
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("getenvCases")
    void getenv(String description, String lookupName, Map<String, String> env, String expected) {
        assertEquals(expected, config(Map.of(), env).getenv(lookupName));
    }

    static Stream<Arguments> getenvCases() {
        return Stream.of(
            Arguments.of("uppercase name finds uppercase key",
                "HTTP_PROXY", Map.of("HTTP_PROXY", "http://proxy.example.com"),
                "http://proxy.example.com"),
            Arguments.of("lowercase name uppercased to find uppercase key",
                "http_proxy", Map.of("HTTP_PROXY", "http://proxy.example.com"),
                "http://proxy.example.com"),
            Arguments.of("mixed-case name uppercased to find uppercase key",
                "Http_Proxy", Map.of("HTTP_PROXY", "http://proxy.example.com"),
                "http://proxy.example.com"),
            Arguments.of("uppercase name lowercased to find lowercase key",
                "HTTP_PROXY", Map.of("http_proxy", "http://proxy.example.com"),
                "http://proxy.example.com"),
            Arguments.of("not set returns null",
                "HTTP_PROXY", Map.of(), null)
        );
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static InetSocketAddress addr(String host, int port) {
        return InetSocketAddress.createUnresolved(host, port);
    }

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}

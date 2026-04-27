package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.peacetalk.jmcp.jdbc.ProxyConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyConfigTest {

    private ProxyConfig config(Map<String, String> sysProps, Map<String, String> envVars) {
        return new ProxyConfig(sysProps::get, envVars::get);
    }

    // ------------------------------------------------------------------
    // getProxyHost
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("proxyHostCases")
    void getProxyHost(String description, Map<String, String> sys, Map<String, String> env, String expected) {
        assertEquals(expected, config(sys, env).getProxyHost());
    }

    static Stream<Arguments> proxyHostCases() {
        return Stream.of(
            sysEnvExpected("system property",
                Map.of("http.proxyHost", "proxy.example.com"), Map.of(),
                "proxy.example.com"),
            sysEnvExpected("HTTP_PROXY uppercase with port",
                Map.of(), Map.of("HTTP_PROXY", "http://proxy.example.com:8080"),
                "proxy.example.com"),
            sysEnvExpected("HTTP_PROXY uppercase without port",
                Map.of(), Map.of("HTTP_PROXY", "http://proxy.example.com"),
                "proxy.example.com"),
            sysEnvExpected("http_proxy lowercase with port",
                Map.of(), Map.of("http_proxy", "http://proxy.example.com:8080"),
                "proxy.example.com"),
            sysEnvExpected("http_proxy lowercase without port",
                Map.of(), Map.of("http_proxy", "http://proxy.example.com"),
                "proxy.example.com"),
            sysEnvExpected("HTTPS_PROXY uppercase fallback",
                Map.of(), Map.of("HTTPS_PROXY", "https://secure.example.com:443"),
                "secure.example.com"),
            sysEnvExpected("https_proxy lowercase fallback",
                Map.of(), Map.of("https_proxy", "https://secure.example.com:443"),
                "secure.example.com"),
            sysEnvExpected("system property overrides HTTP_PROXY",
                Map.of("http.proxyHost", "sys.example.com"),
                Map.of("HTTP_PROXY", "http://env.example.com:8080"),
                "sys.example.com"),
            sysEnvExpected("nothing configured",
                Map.of(), Map.of(), null),
            sysEnvExpected("HTTP_PROXY not a URL",
                Map.of(), Map.of("HTTP_PROXY", "not-a-url"), null),
            sysEnvExpected("HTTP_PROXY wrong scheme",
                Map.of(), Map.of("HTTP_PROXY", "ftp://proxy.example.com:21"), null),
            sysEnvExpected("HTTP_PROXY empty string",
                Map.of(), Map.of("HTTP_PROXY", ""), null)
        );
    }

    // ------------------------------------------------------------------
    // getProxyPort
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("proxyPortCases")
    void getProxyPort(String description, Map<String, String> sys, Map<String, String> env, String expected) {
        assertEquals(expected, config(sys, env).getProxyPort());
    }

    static Stream<Arguments> proxyPortCases() {
        return Stream.of(
            sysEnvExpected("system property",
                Map.of("http.proxyPort", "3128"), Map.of(), "3128"),
            sysEnvExpected("HTTP_PROXY uppercase",
                Map.of(), Map.of("HTTP_PROXY", "http://proxy.example.com:8080"), "8080"),
            sysEnvExpected("http_proxy lowercase",
                Map.of(), Map.of("http_proxy", "http://proxy.example.com:8080"), "8080"),
            sysEnvExpected("HTTPS_PROXY uppercase fallback",
                Map.of(), Map.of("HTTPS_PROXY", "https://secure.example.com:443"), "443"),
            sysEnvExpected("https_proxy lowercase fallback",
                Map.of(), Map.of("https_proxy", "https://secure.example.com:443"), "443"),
            sysEnvExpected("system property overrides HTTP_PROXY",
                Map.of("http.proxyPort", "9999"),
                Map.of("HTTP_PROXY", "http://env.example.com:8080"),
                "9999"),
            sysEnvExpected("nothing configured",
                Map.of(), Map.of(), null),
            sysEnvExpected("HTTP_PROXY no port",
                Map.of(), Map.of("HTTP_PROXY", "http://proxy.example.com"), null),
            sysEnvExpected("HTTP_PROXY non-numeric port",
                Map.of(), Map.of("HTTP_PROXY", "http://proxy.example.com:abc"), null),
            sysEnvExpected("HTTP_PROXY not a URL",
                Map.of(), Map.of("HTTP_PROXY", "not-a-url"), null),
            sysEnvExpected("HTTP_PROXY wrong scheme",
                Map.of(), Map.of("HTTP_PROXY", "ftp://proxy.example.com:21"), null)
        );
    }

    // ------------------------------------------------------------------
    // getHttpProxyEnvVariable
    // ------------------------------------------------------------------

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

    private static Arguments sysEnvExpected(String name, Map<String, String> sys,
                                             Map<String, String> env, String expected) {
        return Arguments.of(name, sys, env, expected);
    }

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}

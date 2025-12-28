package org.peacetalk.jmcp.jdbc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for sanitizing JDBC URLs to hide sensitive information
 */
public class JdbcUrlSanitizer {

    // Pattern to match sensitive parameters in URL query strings
    // Matches: password, pass, pwd, various key types, secret variations, token, auth, credential variations
    // Supports multiple separators: ? & ; (used by different databases)
    private static final Pattern SENSITIVE_PARAM_PATTERN = Pattern.compile(
        "([?&;])(password|pass|pwd|" +
            "(access|api|secret|access_|api_|secret_|client|app|oauth|shared|master|client_|app_|oauth_|shared_|master_)?(secret|key)|" +
            "token|auth|credential|cred)=([^&;]*)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern for Oracle-style connection strings with passwords
    // Example: user/password@host:port
    private static final Pattern ORACLE_PASSWORD_PATTERN = Pattern.compile(
        "(//)([^/@]+)/([^@]+)(@)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Sanitize a JDBC URL by replacing sensitive parameter values with ****
     *
     * @param jdbcUrl The original JDBC URL
     * @return Sanitized URL with sensitive values replaced
     */
    public static String sanitizeUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return jdbcUrl;
        }

        String sanitized = jdbcUrl;

        // Handle Oracle-style user/password@host format
        Matcher oracleMatcher = ORACLE_PASSWORD_PATTERN.matcher(sanitized);
        if (oracleMatcher.find()) {
            // Replace password part with ****
            sanitized = oracleMatcher.replaceAll("$1$2/****$4");
        }

        // Handle URL query parameter style (password=xxx&other=yyy)
        Matcher paramMatcher = SENSITIVE_PARAM_PATTERN.matcher(sanitized);
        StringBuffer result = new StringBuffer();

        while (paramMatcher.find()) {
            String delimiter = paramMatcher.group(1);  // ? or &
            String paramName = paramMatcher.group(2);  // parameter name
            // Replace the value with ****
            paramMatcher.appendReplacement(result,
                Matcher.quoteReplacement(delimiter + paramName + "=****"));
        }
        paramMatcher.appendTail(result);

        return result.toString();
    }

    /**
     * Get the URL to expose based on configuration
     *
     * @param jdbcUrl The original JDBC URL
     * @param exposeUrls Whether to expose URLs (if false, returns ****)
     * @return The URL to expose (sanitized or hidden)
     */
    public static String getExposableUrl(String jdbcUrl, boolean exposeUrls) {
        if (!exposeUrls) {
            return "****";
        }
        return sanitizeUrl(jdbcUrl);
    }
}


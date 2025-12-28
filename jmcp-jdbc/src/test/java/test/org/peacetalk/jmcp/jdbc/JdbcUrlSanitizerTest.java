package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.JdbcUrlSanitizer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JdbcUrlSanitizer
 */
class JdbcUrlSanitizerTest {

    @Test
    void testSanitizeUrlWithPassword() {
        String url = "jdbc:postgresql://localhost:5432/mydb?user=admin&password=secret123&ssl=true";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals("jdbc:postgresql://localhost:5432/mydb?user=admin&password=****&ssl=true", sanitized);
        assertFalse(sanitized.contains("secret123"));
    }

    @Test
    void testSanitizeUrlWithPass() {
        String url = "jdbc:mysql://localhost:3306/db?user=root&pass=mypass&timeout=30";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals("jdbc:mysql://localhost:3306/db?user=root&pass=****&timeout=30", sanitized);
        assertFalse(sanitized.contains("mypass"));
    }

    @Test
    void testSanitizeUrlWithKey() {
        String url = "jdbc:sqlserver://server:1433;database=mydb;key=abc123;encrypt=true";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("key=****"));
        assertFalse(sanitized.contains("abc123"));
    }

    @Test
    void testSanitizeUrlWithSecret() {
        String url = "jdbc:postgresql://localhost/db?secret=topsecret&timeout=10";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("secret=****"));
        assertFalse(sanitized.contains("topsecret"));
    }

    @Test
    void testSanitizeUrlWithToken() {
        String url = "jdbc:h2:mem:test?token=bearerToken123&mode=MySQL";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("token=****"));
        assertFalse(sanitized.contains("bearerToken123"));
    }

    @Test
    void testSanitizeUrlWithApiKey() {
        String url = "jdbc:custom://host/db?apikey=key123456&format=json";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("apikey=****"));
        assertFalse(sanitized.contains("key123456"));
    }

    @Test
    void testSanitizeUrlWithMultipleSensitiveParams() {
        String url = "jdbc:postgresql://localhost/db?user=admin&password=pass1&secret=sec1&key=key1";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("password=****"));
        assertTrue(sanitized.contains("secret=****"));
        assertTrue(sanitized.contains("key=****"));
        assertFalse(sanitized.contains("pass1"));
        assertFalse(sanitized.contains("sec1"));
        assertFalse(sanitized.contains("key1"));
    }

    @Test
    void testSanitizeUrlCaseInsensitive() {
        String url = "jdbc:db://host/db?PASSWORD=secret&Pass=secret2&KEY=key1";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("PASSWORD=****"));
        assertTrue(sanitized.contains("Pass=****"));
        assertTrue(sanitized.contains("KEY=****"));
    }

    @Test
    void testSanitizeUrlOracleStyle() {
        String url = "jdbc:oracle:thin://username/password@hostname:1521/servicename";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("username/****@"));
        assertFalse(sanitized.contains("password@"));
    }

    @Test
    void testSanitizeUrlWithNoSensitiveData() {
        String url = "jdbc:postgresql://localhost:5432/mydb?ssl=true&timeout=30";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals(url, sanitized);
    }

    @Test
    void testSanitizeUrlNull() {
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(null);
        assertNull(sanitized);
    }

    @Test
    void testSanitizeUrlBlank() {
        String sanitized = JdbcUrlSanitizer.sanitizeUrl("   ");
        assertEquals("   ", sanitized);
    }

    @Test
    void testGetExposableUrlWhenExposeUrlsTrue() {
        String url = "jdbc:postgresql://localhost/db?password=secret";
        String exposable = JdbcUrlSanitizer.getExposableUrl(url, true);

        assertEquals("jdbc:postgresql://localhost/db?password=****", exposable);
        assertFalse(exposable.contains("secret"));
    }

    @Test
    void testGetExposableUrlWhenExposeUrlsFalse() {
        String url = "jdbc:postgresql://localhost/db?password=secret";
        String exposable = JdbcUrlSanitizer.getExposableUrl(url, false);

        assertEquals("****", exposable);
    }

    @Test
    void testSanitizeUrlWithPwd() {
        String url = "jdbc:db://host/db?user=admin&pwd=mypassword";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("pwd=****"));
        assertFalse(sanitized.contains("mypassword"));
    }

    @Test
    void testSanitizeUrlWithAuth() {
        String url = "jdbc:db://host/db?auth=authtoken123";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("auth=****"));
        assertFalse(sanitized.contains("authtoken123"));
    }

    @Test
    void testSanitizeUrlWithCredential() {
        String url = "jdbc:db://host/db?credential=cred123";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("credential=****"));
        assertFalse(sanitized.contains("cred123"));
    }

    @Test
    void testSanitizeUrlPreservesNonSensitiveValues() {
        String url = "jdbc:postgresql://localhost:5432/mydb?user=admin&password=secret&ssl=true&port=5432";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("user=admin"));
        assertTrue(sanitized.contains("ssl=true"));
        assertTrue(sanitized.contains("port=5432"));
        assertTrue(sanitized.contains("password=****"));
        assertFalse(sanitized.contains("secret"));
    }

    @Test
    void testSanitizeUrlWithSemicolonSeparators() {
        String url = "jdbc:sqlserver://localhost:1433;database=mydb;password=secret123;encrypt=true";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("password=****"));
        assertTrue(sanitized.contains("encrypt=true"));
        assertFalse(sanitized.contains("secret123"));
    }

    @Test
    void testSanitizeUrlSqlServerStyle() {
        String url = "jdbc:sqlserver://server:1433;databaseName=mydb;user=admin;password=mypass;encrypt=false";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("password=****"));
        assertTrue(sanitized.contains("user=admin"));
        assertTrue(sanitized.contains("databaseName=mydb"));
        assertTrue(sanitized.contains("encrypt=false"));
        assertFalse(sanitized.contains("mypass"));
    }

    @Test
    void testSanitizeUrlWithMultipleSemicolonSensitiveParams() {
        String url = "jdbc:db://host;database=db;password=pass1;secret=sec1;key=key1";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("password=****"));
        assertTrue(sanitized.contains("secret=****"));
        assertTrue(sanitized.contains("key=****"));
        assertFalse(sanitized.contains("pass1"));
        assertFalse(sanitized.contains("sec1"));
        assertFalse(sanitized.contains("key1"));
    }

    @Test
    void testSanitizeUrlMixedSeparators() {
        // Some databases might use both styles
        String url = "jdbc:db://host?param1=value1&password=secret;param2=value2;key=mykey";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("password=****"));
        assertTrue(sanitized.contains("key=****"));
        assertTrue(sanitized.contains("param1=value1"));
        assertTrue(sanitized.contains("param2=value2"));
        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("mykey"));
    }

    @Test
    void testSanitizeUrlWithClientSecret() {
        String url = "jdbc:db://host/db?client_secret=mysecret123&timeout=30";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("client_secret=****"));
        assertFalse(sanitized.contains("mysecret123"));
    }

    @Test
    void testSanitizeUrlWithClientSecretNoUnderscore() {
        String url = "jdbc:db://host/db?clientsecret=mysecret123&timeout=30";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("clientsecret=****"));
        assertFalse(sanitized.contains("mysecret123"));
    }

    @Test
    void testSanitizeUrlWithAppSecret() {
        String url = "jdbc:db://host/db?app_secret=appsec123&mode=test";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("app_secret=****"));
        assertFalse(sanitized.contains("appsec123"));
    }

    @Test
    void testSanitizeUrlWithOAuthSecret() {
        String url = "jdbc:db://host/db?oauth_secret=oauthsec123&version=2";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("oauth_secret=****"));
        assertFalse(sanitized.contains("oauthsec123"));
    }

    @Test
    void testSanitizeUrlWithSharedSecret() {
        String url = "jdbc:db://host/db?shared_secret=sharedsec123";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("shared_secret=****"));
        assertFalse(sanitized.contains("sharedsec123"));
    }

    @Test
    void testSanitizeUrlWithMasterSecret() {
        String url = "jdbc:db://host/db?master_secret=mastersec123";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("master_secret=****"));
        assertFalse(sanitized.contains("mastersec123"));
    }

    @Test
    void testSanitizeUrlWithAccessKey() {
        String url = "jdbc:db://host/db?accesskey=acckey123&region=us-east";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("accesskey=****"));
        assertFalse(sanitized.contains("acckey123"));
    }

    @Test
    void testSanitizeUrlWithApiKeyUnderscore() {
        String url = "jdbc:db://host/db?api_key=apikey123";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertTrue(sanitized.contains("api_key=****"));
        assertFalse(sanitized.contains("apikey123"));
    }
}


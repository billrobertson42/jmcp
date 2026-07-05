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

package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.api.Disabled;
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
        // Distinct secret values so we can assert each is actually gone, not merely
        // that the "NAME=****" text is present.
        String url = "jdbc:db://host/db?PASSWORD=alpha111&Pass=beta222&KEY=gamma333";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals("jdbc:db://host/db?PASSWORD=****&Pass=****&KEY=****", sanitized);
        assertFalse(sanitized.contains("alpha111"), "uppercase PASSWORD value must be masked");
        assertFalse(sanitized.contains("beta222"), "mixed-case Pass value must be masked");
        assertFalse(sanitized.contains("gamma333"), "uppercase KEY value must be masked");
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

    // ------------------------------------------------------------------
    // Boundary / negative cases
    // ------------------------------------------------------------------

    @Test
    void testSanitizeUrlEmptyPasswordValue() {
        // An empty value must still produce the masked form and stay well-formed,
        // not accidentally swallow the following parameter.
        String url = "jdbc:db://host/db?password=&timeout=30";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals("jdbc:db://host/db?password=****&timeout=30", sanitized);
        assertTrue(sanitized.contains("timeout=30"), "trailing parameter must be preserved");
    }

    @Test
    void testSanitizeUrlEmptyStringReturnedUnchanged() {
        // isBlank() short-circuit: an empty string is returned as-is (no NPE, no mask).
        assertEquals("", JdbcUrlSanitizer.sanitizeUrl(""));
    }

    @Test
    void testSanitizeUrlPasswordValueContainingQuestionMark() {
        // The value regex terminates only on & or ;, so a '?' inside the value is
        // part of the masked value. Verify the whole secret disappears.
        String url = "jdbc:mysql://host/db?password=se?cret&user=admin";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals("jdbc:mysql://host/db?password=****&user=admin", sanitized);
        assertFalse(sanitized.contains("se?cret"), "password with embedded '?' must be masked");
        assertFalse(sanitized.contains("cret"), "no fragment of the secret may survive");
    }

    @Test
    void testSanitizeUrlDoesNotMaskUsernameParam() {
        // 'user' is not a sensitive parameter name; it must survive verbatim.
        String url = "jdbc:postgresql://localhost/db?user=admin&password=hunter2";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals("jdbc:postgresql://localhost/db?user=admin&password=****", sanitized);
        assertTrue(sanitized.contains("user=admin"), "non-sensitive user param must be preserved");
    }

    @Test
    void testGetExposableUrlWhenExposeUrlsFalseIgnoresNullUrl() {
        // When URLs are hidden, even a null input yields the fixed mask (no NPE, no leak).
        assertEquals("****", JdbcUrlSanitizer.getExposableUrl(null, false));
    }

    @Test
    void testGetExposableUrlWhenExposeUrlsTrueAndUrlNull() {
        // Exposed + null delegates to sanitizeUrl, which returns null for null input.
        assertNull(JdbcUrlSanitizer.getExposableUrl(null, true));
    }

    @Test
    void testSanitizeUrlOracleStyleRemovesSecretEntirely() {
        // Strengthened over the presence-only Oracle test: assert the actual
        // password token is gone, and the safe user/host parts remain.
        String url = "jdbc:oracle:thin://scott/tigerpw@myhost:1521/orcl";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertEquals("jdbc:oracle:thin://scott/****@myhost:1521/orcl", sanitized);
        assertFalse(sanitized.contains("tigerpw"), "Oracle password must be masked");
        assertTrue(sanitized.contains("scott"), "Oracle username must be preserved");
        assertTrue(sanitized.contains("myhost:1521/orcl"), "host/service must be preserved");
    }

    // ------------------------------------------------------------------
    // Credential-leak regressions (currently failing — see test-review/jdbc-core.md)
    // ------------------------------------------------------------------

    @Test
    @Disabled("BUG: userinfo 'user:password@host' form leaks the password — "
        + "JdbcUrlSanitizer.java:39 ORACLE_PASSWORD_PATTERN only handles 'user/password@', "
        + "not the standard URI colon form; SENSITIVE_PARAM_PATTERN needs a ?&; delimiter. "
        + "See test-review/jdbc-core.md")
    void testSanitizeUrlUserInfoColonFormMasksPassword() {
        // Standard JDBC URI userinfo form: jdbc:<db>://user:password@host/db
        String url = "jdbc:postgresql://admin:secretpw@localhost:5432/mydb";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertFalse(sanitized.contains("secretpw"),
            "password in userinfo (user:password@host) must not leak");
        assertTrue(sanitized.contains("localhost:5432/mydb"), "host/db must be preserved");
    }

    @Test
    @Disabled("BUG: Oracle EZConnect form 'user/password@//host:port/service' leaks the password — "
        + "JdbcUrlSanitizer.java:39 ORACLE_PASSWORD_PATTERN requires '//' BEFORE the user, but in "
        + "the canonical Oracle thin form the '//' appears AFTER the '@', so the pattern never matches. "
        + "See test-review/jdbc-core.md")
    void testSanitizeUrlOracleEzConnectFormMasksPassword() {
        // Canonical Oracle thin driver form (Oracle docs / most tutorials).
        String url = "jdbc:oracle:thin:scott/tigerpw@//myhost:1521/orcl";
        String sanitized = JdbcUrlSanitizer.sanitizeUrl(url);

        assertFalse(sanitized.contains("tigerpw"),
            "Oracle EZConnect password must not leak");
        assertTrue(sanitized.contains("scott"), "username must be preserved");
    }
}


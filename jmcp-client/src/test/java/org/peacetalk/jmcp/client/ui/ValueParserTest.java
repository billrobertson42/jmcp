package org.peacetalk.jmcp.client.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValueParser.
 */
class ValueParserTest {
    private ValueParser parser;

    @BeforeEach
    void setUp() {
        parser = new ValueParser();
    }

    @Test
    void testParseInteger() {
        Object result = parser.parse("42");
        assertEquals(42, result);
        assertTrue(result instanceof Integer);
    }

    @Test
    void testParseNegativeInteger() {
        Object result = parser.parse("-123");
        assertEquals(-123, result);
        assertTrue(result instanceof Integer);
    }

    @Test
    void testParseDouble() {
        Object result = parser.parse("3.14");
        assertEquals(3.14, result);
        assertTrue(result instanceof Double);
    }

    @Test
    void testParseNegativeDouble() {
        Object result = parser.parse("-2.5");
        assertEquals(-2.5, result);
        assertTrue(result instanceof Double);
    }

    @Test
    void testParseBooleanTrue() {
        Object result = parser.parse("true");
        assertEquals(true, result);
        assertTrue(result instanceof Boolean);
    }

    @Test
    void testParseBooleanFalse() {
        Object result = parser.parse("false");
        assertEquals(false, result);
        assertTrue(result instanceof Boolean);
    }

    @Test
    void testParseBooleanCaseInsensitive() {
        assertEquals(true, parser.parse("TRUE"));
        assertEquals(true, parser.parse("True"));
        assertEquals(false, parser.parse("FALSE"));
        assertEquals(false, parser.parse("False"));
    }

    @Test
    void testParseString() {
        Object result = parser.parse("hello world");
        assertEquals("hello world", result);
        assertTrue(result instanceof String);
    }

    @Test
    void testParseEmptyString() {
        Object result = parser.parse("");
        assertEquals("", result);
    }

    @Test
    void testParseBlankString() {
        Object result = parser.parse("   ");
        assertEquals("   ", result);
    }

    @Test
    void testParseNull() {
        Object result = parser.parse(null);
        assertNull(result);
    }

    @Test
    void testParseJsonArray() {
        Object result = parser.parse("[1, 2, 3]");
        assertNotNull(result);
        // Should be parsed as a List or Array
        assertTrue(result instanceof java.util.List || result instanceof Object[]);
    }

    @Test
    void testParseInvalidJsonArray() {
        // Malformed JSON should return as string
        Object result = parser.parse("[invalid json");
        assertEquals("[invalid json", result);
        assertTrue(result instanceof String);
    }

    @Test
    void testParseNumberInvalidFormat() {
        // "12.34.56" is not a valid number, should return as string
        Object result = parser.parse("12.34.56");
        assertEquals("12.34.56", result);
        assertTrue(result instanceof String);
    }

    @Test
    void testParseNumberMethod() {
        Number intResult = parser.parseNumber("42");
        assertEquals(42, intResult);
        assertTrue(intResult instanceof Integer);

        Number doubleResult = parser.parseNumber("3.14");
        assertEquals(3.14, doubleResult);
        assertTrue(doubleResult instanceof Double);

        Number invalidResult = parser.parseNumber("not a number");
        assertNull(invalidResult);
    }

    @Test
    void testParseBooleanMethod() {
        Boolean trueResult = parser.parseBoolean("true");
        assertEquals(true, trueResult);

        Boolean falseResult = parser.parseBoolean("false");
        assertEquals(false, falseResult);

        Boolean invalidResult = parser.parseBoolean("yes");
        assertEquals(false, invalidResult); // parseBoolean returns false for non-boolean strings
    }

    @Test
    void testParseJsonMethod() {
        Object arrayResult = parser.parseJson("[1, 2, 3]");
        assertNotNull(arrayResult);

        Object objectResult = parser.parseJson("{\"key\": \"value\"}");
        assertNotNull(objectResult);

        Object invalidResult = parser.parseJson("not json");
        assertNull(invalidResult);
    }

    @Test
    void testParseComplexJsonArray() {
        Object result = parser.parse("[{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]");
        assertNotNull(result);
        // Should be parsed as JSON structure
        assertFalse(result instanceof String);
    }

    @Test
    void testParseNumberWithSpaces() {
        // Numbers with leading/trailing spaces
        Object result = parser.parse("  42  ");
        // Should handle trimming or treat as string based on implementation
        // This tests actual behavior
        assertTrue(result instanceof String || result instanceof Integer);
    }
}


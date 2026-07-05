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

package test.peacetalk.jmcp.client.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.client.ui.ValueParser;

import java.util.List;
import java.util.Map;

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
        assertEquals(42, result, "'42' should parse to the int value 42");
        assertInstanceOf(Integer.class, result, "whole numbers should parse to Integer");
    }

    @Test
    void testParseNegativeInteger() {
        Object result = parser.parse("-123");
        assertEquals(-123, result, "'-123' should parse to the int value -123");
        assertInstanceOf(Integer.class, result);
    }

    @Test
    void testParseZero() {
        Object result = parser.parse("0");
        assertEquals(0, result, "'0' should parse to int 0");
        assertInstanceOf(Integer.class, result);
    }

    @Test
    void testParseDouble() {
        Object result = parser.parse("3.14");
        assertEquals(3.14, result, "'3.14' should parse to the double value 3.14");
        assertInstanceOf(Double.class, result, "values containing '.' should parse to Double");
    }

    @Test
    void testParseNegativeDouble() {
        Object result = parser.parse("-2.5");
        assertEquals(-2.5, result, "'-2.5' should parse to the double value -2.5");
        assertInstanceOf(Double.class, result);
    }

    @Test
    void testParseLeadingDotDouble() {
        // Double.parseDouble accepts a leading dot; parse() routes anything with '.' to Double.
        Object result = parser.parse(".5");
        assertEquals(0.5, result, "'.5' should parse to 0.5 as a Double");
        assertInstanceOf(Double.class, result);
    }

    @Test
    void testParseBooleanTrue() {
        Object result = parser.parse("true");
        assertEquals(true, result, "'true' should parse to Boolean.TRUE");
        assertInstanceOf(Boolean.class, result);
    }

    @Test
    void testParseBooleanFalse() {
        Object result = parser.parse("false");
        assertEquals(false, result, "'false' should parse to Boolean.FALSE");
        assertInstanceOf(Boolean.class, result);
    }

    @Test
    void testParseBooleanCaseInsensitive() {
        assertEquals(true, parser.parse("TRUE"), "'TRUE' should parse to Boolean.TRUE");
        assertEquals(true, parser.parse("True"), "'True' should parse to Boolean.TRUE");
        assertEquals(false, parser.parse("FALSE"), "'FALSE' should parse to Boolean.FALSE");
        assertEquals(false, parser.parse("False"), "'False' should parse to Boolean.FALSE");
        // Result type must actually be Boolean, not the original String.
        assertInstanceOf(Boolean.class, parser.parse("TRUE"));
    }

    @Test
    void testParseBooleanLikeButNotExact() {
        // "yes"/"trueish" are not the exact tokens true/false, so they stay strings.
        assertEquals("yes", parser.parse("yes"), "'yes' is not a boolean token; stays a String");
        assertEquals("trueish", parser.parse("trueish"),
            "'trueish' is not exactly 'true'; stays a String");
        assertInstanceOf(String.class, parser.parse("yes"));
    }

    @Test
    void testParseString() {
        Object result = parser.parse("hello world");
        assertEquals("hello world", result);
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseObjectJsonStaysString() {
        // parse() only routes '[' ... ']' to JSON. A JSON *object* is NOT parsed and
        // is returned verbatim as a String.
        String obj = """
                {"key": "value"}""";
        Object result = parser.parse(obj);
        assertEquals(obj, result, "JSON objects are not parsed by parse(); returned as String");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseEmptyString() {
        Object result = parser.parse("");
        assertEquals("", result, "empty string is returned unchanged");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseBlankString() {
        Object result = parser.parse("   ");
        assertEquals("   ", result, "blank string is returned unchanged (isBlank short-circuit)");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseNull() {
        Object result = parser.parse(null);
        assertNull(result, "null input returns null");
    }

    @Test
    void testParseJsonArrayNumbers() {
        Object result = parser.parse("[1, 2, 3]");
        assertInstanceOf(List.class, result, "a JSON array should parse to a List");
        assertEquals(List.of(1, 2, 3), result, "'[1, 2, 3]' should parse to [1, 2, 3]");
    }

    @Test
    void testParseEmptyJsonArray() {
        Object result = parser.parse("[]");
        assertInstanceOf(List.class, result, "'[]' should parse to an empty List");
        assertTrue(((List<?>) result).isEmpty(), "'[]' should be an empty list");
    }

    @Test
    void testParseInvalidJsonArray() {
        // Malformed JSON should fall through to string.
        Object result = parser.parse("[invalid json");
        assertEquals("[invalid json", result, "malformed array text should be returned as a String");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseBracketOpenNoClose() {
        // Starts with '[' but does not end with ']', so the JSON branch is never entered.
        Object result = parser.parse("[1, 2, 3");
        assertEquals("[1, 2, 3", result, "text without a closing ']' is not treated as JSON");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseNumberInvalidFormat() {
        // "12.34.56" contains '.', so Double.parseDouble is attempted and fails -> String.
        Object result = parser.parse("12.34.56");
        assertEquals("12.34.56", result, "double-dotted number is invalid; returned as String");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseIntegerOverflowFallsBackToString() {
        // Value has no '.', so Integer.parseInt is used; a value beyond int range overflows,
        // NumberFormatException is caught, and the raw String is returned.
        String big = "99999999999"; // > Integer.MAX_VALUE
        Object result = parser.parse(big);
        assertEquals(big, result, "int-overflowing whole number should fall back to String");
        assertInstanceOf(String.class, result,
            "parse() never widens to Long, so an overflowing integer stays a String");
    }

    @Test
    void testParseScientificNotationWithoutDotIsString() {
        // "1e5" has no '.', Integer.parseInt fails, and it is not routed to Double -> String.
        Object result = parser.parse("1e5");
        assertEquals("1e5", result, "scientific notation without a '.' is not parsed as a number");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseNumberMethodInteger() {
        Number result = parser.parseNumber("42");
        assertEquals(42, result);
        assertInstanceOf(Integer.class, result);
    }

    @Test
    void testParseNumberMethodDouble() {
        Number result = parser.parseNumber("3.14");
        assertEquals(3.14, result);
        assertInstanceOf(Double.class, result);
    }

    @Test
    void testParseNumberMethodInvalid() {
        assertNull(parser.parseNumber("not a number"), "non-numeric text returns null");
    }

    @Test
    void testParseNumberMethodWithSurroundingSpaces() {
        // Integer.parseInt does NOT trim, so a padded number is not a valid number.
        assertNull(parser.parseNumber("  42  "),
            "parseNumber does not trim; padded integer is not parseable");
    }

    @Test
    void testParseNumberWithSpacesReturnsString() {
        // Strengthened from the old either/or assertion: parse() delegates to parseNumber
        // which cannot parse a space-padded value, so the raw String is returned unchanged.
        Object result = parser.parse("  42  ");
        assertEquals("  42  ", result,
            "space-padded number is not parsed; returned verbatim as a String");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testParseBooleanMethod() {
        assertEquals(true, parser.parseBoolean("true"));
        assertEquals(false, parser.parseBoolean("false"));
        // Boolean.parseBoolean returns false for anything that is not "true".
        assertEquals(false, parser.parseBoolean("yes"),
            "parseBoolean returns false for non-'true' strings");
        assertEquals(false, parser.parseBoolean(null),
            "parseBoolean returns false for null (Boolean.parseBoolean contract)");
    }

    @Test
    void testParseJsonMethodArray() {
        Object result = parser.parseJson("[1, 2, 3]");
        assertInstanceOf(List.class, result);
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void testParseJsonMethodObject() {
        Object result = parser.parseJson("""
                {"key": "value"}""");
        assertInstanceOf(Map.class, result, "a JSON object should parse to a Map");
        assertEquals(Map.of("key", "value"), result);
    }

    @Test
    void testParseJsonMethodInvalid() {
        assertNull(parser.parseJson("not json"), "unparseable text returns null");
    }

    @Test
    void testParseComplexJsonArray() {
        Object result = parser.parse("""
                [{"name": "Alice"}, {"name": "Bob"}]""");
        assertInstanceOf(List.class, result, "array of objects should parse to a List");
        List<?> list = (List<?>) result;
        assertEquals(2, list.size(), "should parse both array elements");
        assertInstanceOf(Map.class, list.get(0), "each element should be a Map");
        assertEquals("Alice", ((Map<?, ?>) list.get(0)).get("name"));
        assertEquals("Bob", ((Map<?, ?>) list.get(1)).get("name"));
    }
}

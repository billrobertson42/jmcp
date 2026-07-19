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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValueParser's schema-driven coercion.
 */
class ValueParserTest {
    private ValueParser parser;

    @BeforeEach
    void setUp() {
        parser = new ValueParser();
    }

    // --- string: no guessing ---

    @Test
    void stringTypeKeepsBooleanLookingTextAsString() {
        Object result = parser.coerce("mode", "string", "true");
        assertEquals("true", result, "a string-typed field must not be coerced to Boolean");
        assertInstanceOf(String.class, result);
    }

    @Test
    void stringTypeKeepsNumericTextAsString() {
        Object result = parser.coerce("code", "string", "42");
        assertEquals("42", result, "a string-typed field must not be coerced to a number");
        assertInstanceOf(String.class, result);
    }

    @Test
    void stringTypeKeepsJsonLookingTextAsString() {
        Object result = parser.coerce("template", "string", "[1, 2]");
        assertEquals("[1, 2]", result, "a string-typed field must not be parsed as JSON");
        assertInstanceOf(String.class, result);
    }

    // --- integer: long, widening to BigInteger on overflow ---

    @Test
    void integerCoercesToLong() {
        Object result = parser.coerce("count", "integer", "42");
        assertEquals(42L, result, "'42' should coerce to the long value 42");
        assertInstanceOf(Long.class, result, "integer fields coerce to Long");
    }

    @Test
    void integerCoercesNegativeToLong() {
        Object result = parser.coerce("offset", "integer", "-123");
        assertEquals(-123L, result);
        assertInstanceOf(Long.class, result);
    }

    @Test
    void integerBeyondIntRangeStaysNumeric() {
        // The old heuristic parser used Integer.parseInt and silently fell back
        // to String for anything past Integer.MAX_VALUE.
        Object result = parser.coerce("id", "integer", "99999999999");
        assertEquals(99999999999L, result, "values past int range must still coerce to a number");
        assertInstanceOf(Long.class, result);
    }

    @Test
    void integerBeyondLongRangeWidensToBigInteger() {
        String overLongMax = "9223372036854775808"; // Long.MAX_VALUE + 1
        Object result = parser.coerce("id", "integer", overLongMax);
        assertEquals(new BigInteger(overLongMax), result,
                "long overflow must widen to BigInteger, not fall back to String");
        assertInstanceOf(BigInteger.class, result);
    }

    @Test
    void integerRejectsNonNumericText() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.coerce("count", "integer", "abc"));
        assertTrue(ex.getMessage().contains("count"), "error must name the field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("abc"), "error must show the bad value: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("integer"), "error must name the expected type: " + ex.getMessage());
    }

    @Test
    void integerRejectsDecimalText() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.coerce("count", "integer", "3.5"),
                "a decimal is not a valid integer");
        assertTrue(ex.getMessage().contains("3.5"), "error must show the bad value: " + ex.getMessage());
    }

    // --- number: double ---

    @Test
    void numberCoercesToDouble() {
        Object result = parser.coerce("ratio", "number", "3.14");
        assertEquals(3.14, result);
        assertInstanceOf(Double.class, result);
    }

    @Test
    void numberCoercesWholeNumberTextToDouble() {
        // The old parser routed values without '.' to Integer; a number-typed
        // field must coerce by declared type, not by the presence of a dot.
        Object result = parser.coerce("ratio", "number", "42");
        assertEquals(42.0, result, "number-typed '42' must coerce to a double");
        assertInstanceOf(Double.class, result);
    }

    @Test
    void numberRejectsNonNumericText() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.coerce("ratio", "number", "fast"));
        assertTrue(ex.getMessage().contains("ratio"), "error must name the field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("fast"), "error must show the bad value: " + ex.getMessage());
    }

    // --- boolean: strict true/false ---

    @Test
    void booleanCoercesTrueAndFalseCaseInsensitively() {
        assertEquals(Boolean.TRUE, parser.coerce("flag", "boolean", "true"));
        assertEquals(Boolean.TRUE, parser.coerce("flag", "boolean", "TRUE"));
        assertEquals(Boolean.FALSE, parser.coerce("flag", "boolean", "false"));
        assertEquals(Boolean.FALSE, parser.coerce("flag", "boolean", "False"));
    }

    @Test
    void booleanRejectsNonBooleanToken() {
        // Boolean.parseBoolean would silently return false for "yes"; the
        // coercer must reject it instead.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.coerce("flag", "boolean", "yes"));
        assertTrue(ex.getMessage().contains("flag"), "error must name the field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("yes"), "error must show the bad value: " + ex.getMessage());
    }

    @Test
    void booleanRejectsTruePrefixedToken() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.coerce("flag", "boolean", "trueish"),
                "'trueish' is not exactly true/false and must be rejected");
    }

    // --- array / object: JSON ---

    @Test
    void arrayCoercesJsonArray() {
        Object result = parser.coerce("ids", "array", "[1, 2, 3]");
        assertEquals(List.of(1, 2, 3), result, "'[1, 2, 3]' should parse to the list [1, 2, 3]");
    }

    @Test
    void arrayCoercesArrayOfObjects() {
        Object result = parser.coerce("people", "array", """
                [{"name": "Alice"}, {"name": "Bob"}]""");
        assertEquals(List.of(Map.of("name", "Alice"), Map.of("name", "Bob")), result);
    }

    @Test
    void arrayRejectsMalformedJson() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.coerce("ids", "array", "[1, 2"));
        assertTrue(ex.getMessage().contains("ids"), "error must name the field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("array"), "error must name the expected type: " + ex.getMessage());
    }

    @Test
    void objectCoercesJsonObject() {
        // The old parser only handled '[...]' and returned JSON objects verbatim
        // as strings.
        Object result = parser.coerce("config", "object", """
                {"key": "value"}""");
        assertEquals(Map.of("key", "value"), result,
                "an object-typed field must parse to a Map, not stay a String");
    }

    @Test
    void objectRejectsMalformedJson() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.coerce("config", "object", "{key"));
        assertTrue(ex.getMessage().contains("config"), "error must name the field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("object"), "error must name the expected type: " + ex.getMessage());
    }

    // --- absent / unknown type: one JSON attempt, then raw string ---

    @Test
    void absentTypeParsesValidJson() {
        assertEquals(List.of(1, 2), parser.coerce("x", null, "[1, 2]"),
                "untyped JSON array text should parse");
        assertEquals(Boolean.TRUE, parser.coerce("x", null, "true"),
                "untyped 'true' should parse to Boolean via JSON");
        assertEquals(42, parser.coerce("x", null, "42"),
                "untyped '42' should parse to a number via JSON");
    }

    @Test
    void absentTypeFallsBackToRawString() {
        Object result = parser.coerce("x", null, "hello world");
        assertEquals("hello world", result, "non-JSON text under an untyped field stays a String");
        assertInstanceOf(String.class, result);
    }

    @Test
    void unknownTypeBehavesLikeAbsentType() {
        assertEquals("2024-01-01", parser.coerce("when", "date", "2024-01-01"),
                "an unrecognized schema type falls back to JSON-then-string");
        assertEquals(List.of(1), parser.coerce("when", "date", "[1]"),
                "an unrecognized schema type still gets one JSON parse attempt");
    }
}

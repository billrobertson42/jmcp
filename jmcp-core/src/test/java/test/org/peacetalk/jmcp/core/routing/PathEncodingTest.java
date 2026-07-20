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

package test.org.peacetalk.jmcp.core.routing;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.routing.PathEncoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathEncodingTest {

    @Test
    void encodeThenDecodeRoundTripsNameContainingSlash() {
        String original = "weird/name";

        String encoded = PathEncoding.encodeSegment(original);
        String decoded = PathEncoding.decodeSegment(encoded);

        assertEquals("weird%2Fname", encoded, "encodeSegment must escape '/' so it can't be mistaken for a separator");
        assertEquals(original, decoded);
    }

    @Test
    void decodeDoesNotTurnPlusIntoSpace() {
        // This is the concrete difference from java.net.URLDecoder/application/x-www-form-urlencoded.
        assertEquals("a+b", PathEncoding.decodeSegment("a+b"),
                "a literal '+' in a path segment must not become a space");
    }

    @Test
    void encodeLeavesUnreservedCharactersUnchanged() {
        assertEquals("abcXYZ019-._~", PathEncoding.encodeSegment("abcXYZ019-._~"));
    }

    @Test
    void encodeThenDecodeRoundTripsSpaceAndPercent() {
        String original = "100% done here";

        String round = PathEncoding.decodeSegment(PathEncoding.encodeSegment(original));

        assertEquals(original, round);
    }

    @Test
    void encodeThenDecodeRoundTripsNonAsciiCharacters() {
        String original = "schéma_日本語";

        String round = PathEncoding.decodeSegment(PathEncoding.encodeSegment(original));

        assertEquals(original, round);
    }

    @Test
    void decodeThrowsOnTruncatedEscape() {
        assertThrows(IllegalArgumentException.class, () -> PathEncoding.decodeSegment("abc%2"));
    }

    @Test
    void decodeThrowsOnNonHexEscape() {
        assertThrows(IllegalArgumentException.class, () -> PathEncoding.decodeSegment("abc%ZZ"));
    }

    @Test
    void decodeLeavesPlainTextUnchangedWhenNoEscapesPresent() {
        assertEquals("plain-text_123", PathEncoding.decodeSegment("plain-text_123"));
    }
}

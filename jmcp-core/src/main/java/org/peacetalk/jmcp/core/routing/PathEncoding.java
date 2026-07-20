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

package org.peacetalk.jmcp.core.routing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Percent-encoding/decoding for a single {@code db://...} path segment.
 *
 * <p>This is deliberately <b>not</b> {@link java.net.URLDecoder}/{@link java.net.URLEncoder}:
 * those implement {@code application/x-www-form-urlencoded} semantics, where a literal
 * {@code +} decodes to a space. A path segment is not a form field — a connection, schema,
 * or table name containing a literal {@code +} must round-trip unchanged. This class only
 * ever interprets {@code %XX} escapes; every other character, including {@code +}, passes
 * through untouched on decode and is encoded byte-for-byte (UTF-8) on encode.
 *
 * <p>Public because callers that <i>construct</i> {@code db://} URIs (e.g. {@code jmcp-jdbc}'s
 * {@code Util}) need the encode half to stay coordinated with {@link ResourceRoutes#resolve}'s
 * decode half — one codec, two call sites.
 */
public final class PathEncoding {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private PathEncoding() {
    }

    /**
     * Percent-encodes a raw dynamic value so it is safe to place inside a single {@code db://}
     * path segment — in particular so an embedded {@code /} cannot be mistaken for a segment
     * separator on the next {@link #decodeSegment} round-trip. Encodes every byte except the
     * RFC 3986 "unreserved" set ({@code A-Z a-z 0-9 - . _ ~}); everything else, including
     * {@code /}, space, and non-ASCII characters, becomes one or more {@code %XX} triples
     * (UTF-8). Encoding characters that didn't strictly need it is harmless.
     */
    public static String encodeSegment(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (byte b : raw.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (isUnreserved(c)) {
                out.append((char) c);
            } else {
                out.append('%').append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
            }
        }
        return out.toString();
    }

    /**
     * Percent-decodes a single path segment. Only {@code %XX} escapes are interpreted (as
     * UTF-8 bytes); every other character, including a literal {@code +}, is left as-is.
     *
     * @throws IllegalArgumentException if the segment contains a malformed {@code %} escape
     *                                  (truncated or non-hex digits)
     */
    public static String decodeSegment(String segment) {
        if (segment.indexOf('%') < 0) {
            return segment;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream(segment.length());
        int i = 0;
        int len = segment.length();
        while (i < len) {
            char c = segment.charAt(i);
            if (c == '%') {
                if (i + 2 >= len) {
                    throw new IllegalArgumentException("Incomplete percent-encoding in segment: " + segment);
                }
                int hi = Character.digit(segment.charAt(i + 1), 16);
                int lo = Character.digit(segment.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    throw new IllegalArgumentException("Invalid percent-encoding in segment: " + segment);
                }
                buf.write((hi << 4) | lo);
                i += 3;
            } else {
                int start = i;
                while (i < len && segment.charAt(i) != '%') {
                    i++;
                }
                byte[] bytes = segment.substring(start, i).getBytes(StandardCharsets.UTF_8);
                buf.write(bytes, 0, bytes.length);
            }
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }
}

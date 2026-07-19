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

package org.peacetalk.jmcp.jdbc.driver;

import java.util.regex.Pattern;

/**
 * A downloadable driver artifact: Maven coordinates plus an optional pinned
 * SHA-256 digest. When a pin is present the downloaded jar is verified against
 * it (trust anchored in this codebase at pin time); when absent, verification
 * falls back to the repository's published checksum (trust anchored in the
 * download channel).
 *
 * @param coordinates the Maven coordinates of the jar
 * @param sha256 lower/upper-case hex SHA-256 pin, or null for no pin
 */
public record DriverArtifact(MavenCoordinates coordinates, String sha256) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    public DriverArtifact {
        if (sha256 != null && !SHA256_HEX.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Invalid sha256 pin for " + coordinates
                + ": expected 64 hex characters, got '" + sha256 + "'");
        }
    }

    /** An artifact with no pin. */
    public static DriverArtifact unpinned(MavenCoordinates coordinates) {
        return new DriverArtifact(coordinates, null);
    }
}

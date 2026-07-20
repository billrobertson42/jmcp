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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parameter values bound by a matched {@link ResourceRoutes} route.
 *
 * <p>Keys are the {@code :name} tokens from the route pattern; values are the
 * corresponding path segments from the resolved URI, already percent-decoded.
 * Immutable and safe to hand to a {@code Resource} factory.
 */
public final class Params {

    private final Map<String, String> values;

    Params(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * @param name the parameter name (the token after {@code :} in the route pattern)
     * @return the bound value, or {@code null} if the matched route did not bind this name
     */
    public String get(String name) {
        return values.get(name);
    }

    /**
     * @return all bound parameters as an unmodifiable map, in the order they appear in the pattern
     */
    public Map<String, String> asMap() {
        return values;
    }

    @Override
    public String toString() {
        return "Params" + values;
    }
}

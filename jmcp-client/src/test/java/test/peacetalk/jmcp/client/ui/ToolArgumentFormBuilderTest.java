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

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.client.ui.ToolArgumentFormBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolArgumentFormBuilder's required-field detection.
 */
class ToolArgumentFormBuilderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fieldListedInRequiredArrayIsRequired() {
        JsonNode required = MAPPER.readTree("""
                ["uid", "name"]""");
        assertTrue(ToolArgumentFormBuilder.isRequired(required, "uid"),
                "'uid' is listed in the required array");
        assertTrue(ToolArgumentFormBuilder.isRequired(required, "name"),
                "'name' is listed in the required array");
    }

    @Test
    void substringOfRequiredFieldNameIsNotRequired() {
        // The old implementation did requiredFields.toString().contains(fieldName),
        // so "id" was wrongly marked required because it is a substring of "uid".
        JsonNode required = MAPPER.readTree("""
                ["uid"]""");
        assertFalse(ToolArgumentFormBuilder.isRequired(required, "id"),
                "'id' is only a substring of required field 'uid' and must NOT be required");
    }

    @Test
    void fieldNotListedIsNotRequired() {
        JsonNode required = MAPPER.readTree("""
                ["uid"]""");
        assertFalse(ToolArgumentFormBuilder.isRequired(required, "email"),
                "'email' is not in the required array");
    }

    @Test
    void nullOrNonArrayRequiredNodeMeansNothingRequired() {
        assertFalse(ToolArgumentFormBuilder.isRequired(null, "uid"),
                "schema without a required array has no required fields");
        assertFalse(ToolArgumentFormBuilder.isRequired(MAPPER.readTree("\"uid\""), "uid"),
                "a non-array required node must not mark fields required");
    }
}

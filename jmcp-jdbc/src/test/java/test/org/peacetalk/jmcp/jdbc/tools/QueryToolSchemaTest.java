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

package test.org.peacetalk.jmcp.jdbc.tools;

import com.networknt.schema.*;
import com.networknt.schema.Error;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.tools.QueryTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryTool schema validation.
 */
class QueryToolSchemaTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    @Test
    void testSchemaIsValidJsonSchema() {
        QueryTool tool = new QueryTool();
        JsonNode schema = tool.getInputSchema();

        // The schema itself should be a valid JSON Schema
        assertNotNull(schema);
        assertTrue(schema.has("type"));
        assertEquals("object", schema.get("type").asText());
        assertTrue(schema.has("properties"));
        assertTrue(schema.has("required"));
    }

    @Test
    void testSchemaValidatesCorrectInput() {
        QueryTool tool = new QueryTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Valid input with required fields
        String validInput = """
            {
                "sql": "SELECT * FROM users"
            }
            """;

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Valid input should pass validation: " + errors);
    }

    @Test
    void testSchemaValidatesInputWithParameters() {
        QueryTool tool = new QueryTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Valid input with optional parameters
        String validInput = """
            {
                "sql": "SELECT * FROM users WHERE id = ?",
                "parameters": ["123"]
            }
            """;

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Valid input with parameters should pass validation: " + errors);
    }

    @Test
    void testSchemaRejectsMissingRequiredField() {
        QueryTool tool = new QueryTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Missing required 'sql' field
        String invalidInput = """
            {
                "parameters": ["value"]
            }
            """;

        List<Error> errors = validator.validate(invalidInput, InputFormat.JSON);
        assertFalse(errors.isEmpty(), "Missing required field should fail validation");
    }

    @Test
    void testSchemaRejectsWrongType() {
        QueryTool tool = new QueryTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Wrong type for 'sql' (should be string)
        String invalidInput = """
            {
                "sql": 123
            }
            """;

        List<Error> errors = validator.validate(invalidInput, InputFormat.JSON);
        assertFalse(errors.isEmpty(), "Wrong type should fail validation");
    }

    @Test
    void testSchemaRejectsWrongParametersType() {
        QueryTool tool = new QueryTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        // Wrong type for 'parameters' (should be array)
        String invalidInput = """
            {
                "sql": "SELECT * FROM users",
                "parameters": "not an array"
            }
            """;

        List<Error> errors = validator.validate(invalidInput, InputFormat.JSON);
        assertFalse(errors.isEmpty(), "Wrong parameters type should fail validation");
    }

    @Test
    void testSchemaStructure() {
        QueryTool tool = new QueryTool();
        JsonNode schema = tool.getInputSchema();

        assertThatJson(MAPPER.writeValueAsString(schema)).and(
            j -> j.node("type").isEqualTo("object"),
            j -> j.node("properties.sql.type")
                .describedAs("Schema should have 'sql' property of type string")
                .isEqualTo("string"),
            j -> j.node("properties.sql.description").isPresent(),
            j -> j.node("properties.parameters.type")
                .describedAs("Schema should have 'parameters' property of type array")
                .isEqualTo("array"),
            j -> j.node("properties.parameters.description").isPresent(),
            j -> j.node("properties.parameters.items.type").isEqualTo("string"),
            j -> j.node("required").isArray().hasSize(1),
            j -> j.node("required[0]").isEqualTo("sql")
        );
    }
}


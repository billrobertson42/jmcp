package test.org.peacetalk.jmcp.jdbc.tools;

import com.networknt.schema.*;
import com.networknt.schema.Error;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.tools.PreviewTableTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PreviewTableTool schema validation.
 */
class PreviewTableToolSchemaTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    @Test
    void testSchemaIsValidJsonSchema() {
        PreviewTableTool tool = new PreviewTableTool();
        JsonNode schema = tool.getInputSchema();

        assertNotNull(schema);
        assertTrue(schema.has("type"));
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    void testSchemaValidatesCorrectInput() {
        PreviewTableTool tool = new PreviewTableTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String validInput = """
            {
                "table": "users"
            }
            """;

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Valid input should pass validation: " + errors);
    }

    @Test
    void testSchemaValidatesInputWithLimit() {
        PreviewTableTool tool = new PreviewTableTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String validInput = """
            {
                "table": "users",
                "limit": 50
            }
            """;

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Valid input with limit should pass validation: " + errors);
    }

    @Test
    void testSchemaRejectsLimitBelowMinimum() {
        PreviewTableTool tool = new PreviewTableTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String invalidInput = """
            {
                "table": "users",
                "limit": 0
            }
            """;

        List<Error> errors = validator.validate(invalidInput, InputFormat.JSON);
        assertFalse(errors.isEmpty(), "Limit below minimum should fail validation");
    }

    @Test
    void testSchemaRejectsLimitAboveMaximum() {
        PreviewTableTool tool = new PreviewTableTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String invalidInput = """
            {
                "table": "users",
                "limit": 101
            }
            """;

        List<Error> errors = validator.validate(invalidInput, InputFormat.JSON);
        assertFalse(errors.isEmpty(), "Limit above maximum should fail validation");
    }

    @Test
    void testSchemaStructure() {
        PreviewTableTool tool = new PreviewTableTool();
        JsonNode schema = tool.getInputSchema();

        JsonNode properties = schema.get("properties");
        assertTrue(properties.has("table"));
        assertTrue(properties.has("schema"));
        assertTrue(properties.has("limit"));

        // Verify limit constraints
        JsonNode limitProp = properties.get("limit");
        assertEquals("integer", limitProp.get("type").asText());
        assertEquals(1, limitProp.get("minimum").asInt());
        assertEquals(100, limitProp.get("maximum").asInt());
    }
}


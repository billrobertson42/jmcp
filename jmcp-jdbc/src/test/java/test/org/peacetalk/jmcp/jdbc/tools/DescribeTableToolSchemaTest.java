package test.org.peacetalk.jmcp.jdbc.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Error;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.tools.DescribeTableTool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DescribeTableTool schema validation.
 */
class DescribeTableToolSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    @Test
    void testSchemaIsValidJsonSchema() {
        DescribeTableTool tool = new DescribeTableTool();
        JsonNode schema = tool.getInputSchema();

        assertNotNull(schema);
        assertTrue(schema.has("type"));
        assertEquals("object", schema.get("type").asText());
        assertTrue(schema.has("properties"));
    }

    @Test
    void testSchemaValidatesCorrectInput() {
        DescribeTableTool tool = new DescribeTableTool();
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
    void testSchemaValidatesInputWithSchema() {
        DescribeTableTool tool = new DescribeTableTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String validInput = """
            {
                "table": "users",
                "schema": "public"
            }
            """;

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Valid input with schema should pass validation: " + errors);
    }

    @Test
    void testSchemaRejectsMissingRequiredField() {
        DescribeTableTool tool = new DescribeTableTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String invalidInput = """
            {
                "schema": "public"
            }
            """;

        List<Error> errors = validator.validate(invalidInput, InputFormat.JSON);
        assertFalse(errors.isEmpty(), "Missing required 'table' field should fail validation");
    }

    @Test
    void testSchemaStructure() {
        DescribeTableTool tool = new DescribeTableTool();
        JsonNode schema = tool.getInputSchema();

        JsonNode properties = schema.get("properties");
        assertTrue(properties.has("table"));
        assertTrue(properties.has("schema"));

        assertEquals("string", properties.get("table").get("type").asText());
        assertEquals("string", properties.get("schema").get("type").asText());

        JsonNode required = schema.get("required");
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("table", required.get(0).asText());
    }
}


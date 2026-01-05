package test.org.peacetalk.jmcp.jdbc.tools;

import com.networknt.schema.*;
import com.networknt.schema.Error;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.tools.GetRowCountTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for schema validation of remaining tools.
 */
class RemainingToolsSchemaTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    // ========== GetRowCountTool Tests ==========

    @Test
    void testGetRowCountToolSchemaIsValid() {
        GetRowCountTool tool = new GetRowCountTool();
        JsonNode schema = tool.getInputSchema();

        assertNotNull(schema);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    void testGetRowCountToolValidatesCorrectInput() {
        GetRowCountTool tool = new GetRowCountTool();
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
    void testGetRowCountToolRejectsMissingTable() {
        GetRowCountTool tool = new GetRowCountTool();
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

}


package test.org.peacetalk.jmcp.jdbc.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Error;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.tools.GetRowCountTool;
import org.peacetalk.jmcp.jdbc.tools.ListSchemasTool;
import org.peacetalk.jmcp.jdbc.tools.ListTablesTool;

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

    // ========== ListSchemasTool Tests ==========

    @Test
    void testListSchemasToolSchemaIsValid() {
        ListSchemasTool tool = new ListSchemasTool();
        JsonNode schema = tool.getInputSchema();

        assertNotNull(schema);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    void testListSchemasToolValidatesEmptyInput() {
        ListSchemasTool tool = new ListSchemasTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String validInput = "{}";

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Empty input should pass validation for list-schemas: " + errors);
    }

    @Test
    void testListSchemasToolHasNoRequiredFields() {
        ListSchemasTool tool = new ListSchemasTool();
        JsonNode schema = tool.getInputSchema();

        // Should have no required fields or an empty required array
        JsonNode required = schema.get("required");
        assertTrue(required == null || required.isEmpty(), "list-schemas should have no required fields");
    }

    // ========== ListTablesTool Tests ==========

    @Test
    void testListTablesToolSchemaIsValid() {
        ListTablesTool tool = new ListTablesTool();
        JsonNode schema = tool.getInputSchema();

        assertNotNull(schema);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    void testListTablesToolValidatesEmptyInput() {
        ListTablesTool tool = new ListTablesTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String validInput = "{}";

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Empty input should pass validation for list-tables: " + errors);
    }

    @Test
    void testListTablesToolValidatesInputWithSchema() {
        ListTablesTool tool = new ListTablesTool();
        JsonNode schema = tool.getInputSchema();
        Schema validator = SCHEMA_REGISTRY.getSchema(schema.toString(), InputFormat.JSON);

        String validInput = """
            {
                "schema": "public"
            }
            """;

        List<Error> errors = validator.validate(validInput, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "Input with optional schema should pass validation: " + errors);
    }

    @Test
    void testListTablesToolHasNoRequiredFields() {
        ListTablesTool tool = new ListTablesTool();
        JsonNode schema = tool.getInputSchema();

        // Should have no required fields or an empty required array
        JsonNode required = schema.get("required");
        assertTrue(required == null || required.isEmpty(), "list-tables should have no required fields");
    }
}


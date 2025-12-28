package test.org.peacetalk.jmcp.core.validation;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.*;
import org.peacetalk.jmcp.core.validation.McpValidator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValidTool() {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        Tool tool = new Tool("test-tool", "Description", schema);

        Set<String> violations = McpValidator.validate(tool);
        assertTrue(violations.isEmpty(), "Valid tool should have no violations");
        assertTrue(McpValidator.isValid(tool));
    }

    @Test
    void testToolWithBlankName() {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");

        // Blank name will fail in compact constructor, not validation
        assertThrows(IllegalArgumentException.class, () ->
            new Tool("", "Description", schema));
    }

    @Test
    void testToolWithNullSchema() {
        // Null schema will fail in compact constructor, not validation
        assertThrows(IllegalArgumentException.class, () ->
            new Tool("test-tool", "Description", null));
    }

    @Test
    void testValidCallToolRequest() {
        ObjectNode args = mapper.createObjectNode().put("param", "value");
        CallToolRequest request = new CallToolRequest("tool-name", args);

        assertTrue(McpValidator.isValid(request));
    }

    @Test
    void testCallToolRequestWithBlankName() {
        // Blank name will fail in compact constructor
        assertThrows(IllegalArgumentException.class, () ->
            new CallToolRequest("  ", null));
    }

    @Test
    void testValidContent() {
        Content textContent = Content.text("Hello, world!");
        assertTrue(McpValidator.isValid(textContent));

        Content imageContent = Content.image("base64data", "image/png");
        assertTrue(McpValidator.isValid(imageContent));
    }

    @Test
    void testContentWithInvalidType() {
        // This will fail in compact constructor before validation
        assertThrows(IllegalArgumentException.class, () ->
            new Content("invalid", "text", null, null));
    }

    @Test
    void testValidImplementation() {
        Implementation impl = new Implementation("test", "1.0.0");
        assertTrue(McpValidator.isValid(impl));
    }

    @Test
    void testImplementationWithBlankFields() {
        // Blank fields will fail in compact constructor
        assertThrows(IllegalArgumentException.class, () ->
            new Implementation("", ""));
    }

    @Test
    void testValidJsonRpcRequest() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "test/method", null);
        assertTrue(McpValidator.isValid(request));
    }

    @Test
    void testJsonRpcRequestWithInvalidVersion() {
        // Invalid version (defaults to "2.0" if null, but JSR-380 will validate)
        // Create with valid version first
        JsonRpcRequest validRequest = new JsonRpcRequest("2.0", 1, "test/method", null);
        assertTrue(McpValidator.isValid(validRequest));

        // Direct instantiation with invalid version will pass constructor but fail validation
        JsonRpcRequest request = new JsonRpcRequest("1.0", 1, "test/method", null);
        Set<String> violations = McpValidator.validate(request);
        assertFalse(violations.isEmpty(), "Version 1.0 should violate pattern constraint");
        assertTrue(violations.stream().anyMatch(v -> v.contains("jsonrpc")),
            "Violation should mention jsonrpc field");
    }

    @Test
    void testJsonRpcRequestWithBlankMethod() {
        // Blank method will fail in compact constructor
        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcRequest("2.0", 1, "", null));
    }

    @Test
    void testValidJsonRpcResponse() {
        JsonRpcResponse response = JsonRpcResponse.success(1, "result");
        assertTrue(McpValidator.isValid(response));

        // Also test error case
        JsonRpcError error = new JsonRpcError(500, "Error", null);
        JsonRpcResponse errorResponse = JsonRpcResponse.error(1, error);
        assertTrue(McpValidator.isValid(errorResponse));
    }

    @Test
    void testJsonRpcResponseWithBothResultAndError() {
        // Both result and error provided will fail in compact constructor
        JsonRpcError error = new JsonRpcError(500, "Error", null);
        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcResponse("2.0", 1, "result", error));
    }

    @Test
    void testJsonRpcResponseWithNeitherResultNorError() {
        // Neither result nor error provided will fail in compact constructor
        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcResponse("2.0", 1, null, null));
    }

    @Test
    void testValidateAndThrow() {
        Tool validTool = new Tool("test", "desc", mapper.createObjectNode());
        assertDoesNotThrow(() -> McpValidator.validateAndThrow(validTool));

        // Invalid Tool (blank name) will fail in compact constructor
        assertThrows(IllegalArgumentException.class, () ->
            new Tool("", "desc", mapper.createObjectNode()));
    }

    @Test
    void testNestedValidation() {
        Implementation serverInfo = new Implementation("test-server", "1.0.0");
        ServerCapabilities capabilities = new ServerCapabilities(
            null, null, null, null,
            new ServerCapabilities.ToolsCapability(false)
        );

        InitializeResult result = new InitializeResult("2024-11-05", capabilities, serverInfo);
        assertTrue(McpValidator.isValid(result));
    }

    @Test
    void testNestedValidationFailure() {
        // Blank fields in Implementation will fail in compact constructor
        assertThrows(IllegalArgumentException.class, () -> {
            Implementation invalidServerInfo = new Implementation("", "");  // Both blank
            ServerCapabilities capabilities = new ServerCapabilities(null, null, null, null, null);
            new InitializeResult("2024-11-05", capabilities, invalidServerInfo);
        });
    }

    @Test
    void testCallToolResultWithInvalidContent() {
        // Invalid content (text type but no text) will fail in compact constructor
        assertThrows(IllegalArgumentException.class, () ->
            new Content("text", null, null, null));
    }
}


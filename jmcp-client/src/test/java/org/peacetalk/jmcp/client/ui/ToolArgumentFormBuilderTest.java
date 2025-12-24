package org.peacetalk.jmcp.client.ui;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.Tool;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolArgumentFormBuilder.
 * Note: These tests use JavaFX components, so JavaFX toolkit must be initialized.
 */
class ToolArgumentFormBuilderTest {
    private ToolArgumentFormBuilder builder;
    private VBox container;
    private ObjectMapper mapper;

    @BeforeAll
    static void initJavaFX() {
        // Initialize JavaFX toolkit
        try {
            new javafx.embed.swing.JFXPanel();
        } catch (Exception e) {
            // Already initialized or headless environment
        }
    }

    @BeforeEach
    void setUp() {
        builder = new ToolArgumentFormBuilder();
        container = new VBox();
        mapper = new ObjectMapper();
    }

    @Test
    void testBuildFormWithNoProperties() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        Tool tool = new Tool("test", "Test tool", schema);

        Map<String, TextField> fields = builder.buildForm(tool, container);

        assertTrue(fields.isEmpty());
        assertEquals(1, container.getChildren().size()); // Should have "No arguments required" label
        assertTrue(container.getChildren().get(0) instanceof Label);
        Label label = (Label) container.getChildren().get(0);
        assertEquals("No arguments required", label.getText());
    }

    @Test
    void testBuildFormWithOneProperty() {
        ObjectNode schema = createSchemaWithProperty("name", "string", "User name");

        Tool tool = new Tool("test", "Test tool", schema);

        Map<String, TextField> fields = builder.buildForm(tool, container);

        assertEquals(1, fields.size());
        assertTrue(fields.containsKey("name"));

        // Should have label and text field
        assertEquals(2, container.getChildren().size());
        assertTrue(container.getChildren().get(0) instanceof Label);
        assertTrue(container.getChildren().get(1) instanceof TextField);

        Label label = (Label) container.getChildren().get(0);
        assertEquals("name", label.getText());

        TextField textField = fields.get("name");
        assertEquals("User name", textField.getPromptText());
    }

    @Test
    void testBuildFormWithMultipleProperties() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        properties.set("name", createProperty("string", "User name"));
        properties.set("age", createProperty("integer", "User age"));
        schema.set("properties", properties);

        Tool tool = new Tool("test", "Test tool", schema);

        Map<String, TextField> fields = builder.buildForm(tool, container);

        assertEquals(2, fields.size());
        assertTrue(fields.containsKey("name"));
        assertTrue(fields.containsKey("age"));

        // Should have 2 labels and 2 text fields
        assertEquals(4, container.getChildren().size());
    }

    @Test
    void testBuildFormWithRequiredField() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        properties.set("name", createProperty("string", "User name"));
        schema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("name");
        schema.set("required", required);

        Tool tool = new Tool("test", "Test tool", schema);

        Map<String, TextField> fields = builder.buildForm(tool, container);

        Label label = (Label) container.getChildren().get(0);
        assertTrue(label.getText().contains("*")); // Should have asterisk for required field
    }

    @Test
    void testClearForm() {
        ObjectNode schema = createSchemaWithProperty("name", "string", "User name");
        Tool tool = new Tool("test", "Test tool", schema);

        builder.buildForm(tool, container);
        assertFalse(container.getChildren().isEmpty());

        builder.clearForm(container);
        assertTrue(container.getChildren().isEmpty());
    }

    @Test
    void testCollectArgumentsEmpty() {
        ValueParser valueParser = new ValueParser();
        Map<String, TextField> fields = Map.of();

        Map<String, Object> arguments = builder.collectArguments(fields, valueParser);

        assertTrue(arguments.isEmpty());
    }

    @Test
    void testCollectArgumentsWithValues() {
        ValueParser valueParser = new ValueParser();

        TextField nameField = new TextField();
        nameField.setText("Alice");

        TextField ageField = new TextField();
        ageField.setText("30");

        Map<String, TextField> fields = Map.of(
            "name", nameField,
            "age", ageField
        );

        Map<String, Object> arguments = builder.collectArguments(fields, valueParser);

        assertEquals(2, arguments.size());
        assertEquals("Alice", arguments.get("name"));
        assertEquals(30, arguments.get("age")); // Should be parsed as integer
    }

    @Test
    void testCollectArgumentsSkipsEmptyFields() {
        ValueParser valueParser = new ValueParser();

        TextField nameField = new TextField();
        nameField.setText("Alice");

        TextField ageField = new TextField();
        ageField.setText(""); // Empty

        Map<String, TextField> fields = Map.of(
            "name", nameField,
            "age", ageField
        );

        Map<String, Object> arguments = builder.collectArguments(fields, valueParser);

        assertEquals(1, arguments.size());
        assertTrue(arguments.containsKey("name"));
        assertFalse(arguments.containsKey("age"));
    }

    @Test
    void testCollectArgumentsTrimsWhitespace() {
        ValueParser valueParser = new ValueParser();

        TextField nameField = new TextField();
        nameField.setText("  Alice  ");

        Map<String, TextField> fields = Map.of("name", nameField);

        Map<String, Object> arguments = builder.collectArguments(fields, valueParser);

        assertEquals("Alice", arguments.get("name"));
    }

    @Test
    void testCollectArgumentsSkipsWhitespaceOnlyFields() {
        ValueParser valueParser = new ValueParser();

        TextField nameField = new TextField();
        nameField.setText("   "); // Only whitespace

        Map<String, TextField> fields = Map.of("name", nameField);

        Map<String, Object> arguments = builder.collectArguments(fields, valueParser);

        assertTrue(arguments.isEmpty());
    }

    @Test
    void testBuildFormReplacesOldContent() {
        ObjectNode schema1 = createSchemaWithProperty("field1", "string", "Field 1");
        Tool tool1 = new Tool("test1", "Test tool 1", schema1);

        builder.buildForm(tool1, container);
        int childrenAfterFirstBuild = container.getChildren().size();

        ObjectNode schema2 = createSchemaWithProperty("field2", "string", "Field 2");
        Tool tool2 = new Tool("test2", "Test tool 2", schema2);

        Map<String, TextField> fields = builder.buildForm(tool2, container);

        // Should have replaced old content
        assertEquals(1, fields.size());
        assertTrue(fields.containsKey("field2"));
        assertFalse(fields.containsKey("field1"));
    }

    @Test
    void testFieldWithoutDescription() {
        ObjectNode property = mapper.createObjectNode();
        property.put("type", "string");
        // No description

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        properties.set("field", property);
        schema.set("properties", properties);

        Tool tool = new Tool("test", "Test tool", schema);

        Map<String, TextField> fields = builder.buildForm(tool, container);

        TextField textField = fields.get("field");
        assertEquals("Enter string", textField.getPromptText());
    }

    /**
     * Helper method to create a schema with one property.
     */
    private ObjectNode createSchemaWithProperty(String name, String type, String description) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        properties.set(name, createProperty(type, description));
        schema.set("properties", properties);

        return schema;
    }

    /**
     * Helper method to create a property definition.
     */
    private ObjectNode createProperty(String type, String description) {
        ObjectNode property = mapper.createObjectNode();
        property.put("type", type);
        property.put("description", description);
        return property;
    }
}


package org.peacetalk.jmcp.client.ui;

import tools.jackson.databind.JsonNode;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.peacetalk.jmcp.core.model.Tool;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds dynamic argument input forms from tool JSON schemas.
 */
public class ToolArgumentFormBuilder {

    /**
     * Build an argument input form for the given tool.
     *
     * @param tool The tool to build the form for
     * @param container The VBox container to add form fields to
     * @return Map of field names to TextField controls
     */
    public Map<String, TextField> buildForm(Tool tool, VBox container) {
        Map<String, TextField> argumentFields = new HashMap<>();

        container.getChildren().clear();

        // Parse schema to build input fields
        JsonNode schema = tool.inputSchema();

        if (schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            JsonNode requiredFields = schema.has("required") ? schema.get("required") : null;

            for (Map.Entry<String, JsonNode> entry : properties.properties()) {
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();

                boolean isRequired = requiredFields != null &&
                        requiredFields.toString().contains(fieldName);

                // Create label with style class
                Label label = new Label(fieldName + (isRequired ? " *" : ""));
                label.getStyleClass().add("field-label");

                // Create text field
                TextField textField = new TextField();
                textField.setPromptText(getPromptText(fieldSchema));

                argumentFields.put(fieldName, textField);

                container.getChildren().addAll(label, textField);
            }
        }

        if (argumentFields.isEmpty()) {
            Label noArgs = new Label("No arguments required");
            container.getChildren().add(noArgs);
        }

        return argumentFields;
    }

    /**
     * Clear the form from the container.
     *
     * @param container The VBox container to clear
     */
    public void clearForm(VBox container) {
        container.getChildren().clear();
    }

    /**
     * Collect argument values from the form fields.
     *
     * @param fields Map of field names to TextField controls
     * @param valueParser Parser to convert string values to appropriate types
     * @return Map of argument names to values
     */
    public Map<String, Object> collectArguments(Map<String, TextField> fields, ValueParser valueParser) {
        Map<String, Object> arguments = new HashMap<>();

        for (Map.Entry<String, TextField> entry : fields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (!value.isEmpty()) {
                arguments.put(entry.getKey(), valueParser.parse(value));
            }
        }

        return arguments;
    }

    /**
     * Get prompt text for a field based on its schema.
     */
    private String getPromptText(JsonNode fieldSchema) {
        if (fieldSchema.has("type")) {
            String type = fieldSchema.get("type").asString();
            if (fieldSchema.has("description")) {
                return fieldSchema.get("description").asString();
            }
            return "Enter " + type;
        }
        return "";
    }
}


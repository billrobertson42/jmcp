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

package org.peacetalk.jmcp.client.ui;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.peacetalk.jmcp.core.model.Tool;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds dynamic argument input forms from tool JSON schemas.
 */
public class ToolArgumentFormBuilder {

    /**
     * A form input field together with its declared JSON-schema type
     * (null when the schema declares no type for the field).
     */
    public record FieldInput(TextField textField, String schemaType) {}

    /**
     * Build an argument input form for the given tool.
     *
     * @param tool The tool to build the form for
     * @param container The VBox container to add form fields to
     * @param onEnterAction Action to execute when Enter is pressed in any field
     * @return Map of field names to form inputs (text field + declared schema type)
     */
    public Map<String, FieldInput> buildForm(Tool tool, VBox container, Runnable onEnterAction) {
        Map<String, FieldInput> argumentFields = new HashMap<>();

        container.getChildren().clear();

        // Parse schema to build input fields
        JsonNode schema = tool.inputSchema();

        if (schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            JsonNode requiredFields = schema.has("required") ? schema.get("required") : null;

            for (Map.Entry<String, JsonNode> entry : properties.properties()) {
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();

                boolean isRequired = isRequired(requiredFields, fieldName);

                // Create label with style class
                Label label = new Label(fieldName + (isRequired ? " *" : ""));
                label.getStyleClass().add("field-label");

                // Create text field
                TextField textField = new TextField();
                textField.setPromptText(getPromptText(fieldSchema));

                // Set Enter key action to trigger execute button
                if (onEnterAction != null) {
                    textField.setOnAction(event -> onEnterAction.run());
                }

                argumentFields.put(fieldName, new FieldInput(textField, getSchemaType(fieldSchema)));

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
     * Check whether a field is listed in the schema's "required" array.
     * Compares element values by equality, not substring matching.
     *
     * @param requiredFields The schema's "required" array node, or null
     * @param fieldName The field name to look for
     * @return true if the field name appears in the required array
     */
    public static boolean isRequired(JsonNode requiredFields, String fieldName) {
        if (requiredFields == null || !requiredFields.isArray()) {
            return false;
        }
        for (int i = 0; i < requiredFields.size(); i++) {
            JsonNode element = requiredFields.get(i);
            if (element.isString() && fieldName.equals(element.stringValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract a field's declared JSON-schema type, or null when the schema
     * has no (string-valued) "type".
     */
    private static String getSchemaType(JsonNode fieldSchema) {
        JsonNode type = fieldSchema.path("type");
        return type.isString() ? type.stringValue() : null;
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
     * Collect argument values from the form fields, coercing each non-empty
     * value to its declared schema type.
     *
     * @param fields Map of field names to form inputs
     * @param valueParser Coercer that converts string values to the declared types
     * @return Map of argument names to values
     * @throws IllegalArgumentException if a value cannot be coerced to its
     *         declared schema type
     */
    public Map<String, Object> collectArguments(Map<String, FieldInput> fields, ValueParser valueParser) {
        Map<String, Object> arguments = new HashMap<>();

        for (Map.Entry<String, FieldInput> entry : fields.entrySet()) {
            String value = entry.getValue().textField().getText().trim();
            if (!value.isEmpty()) {
                arguments.put(entry.getKey(),
                        valueParser.coerce(entry.getKey(), entry.getValue().schemaType(), value));
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

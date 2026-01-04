package org.peacetalk.jmcp.client.ui;

import javafx.scene.control.ListCell;
import org.peacetalk.jmcp.core.model.ResourceDescriptor;

/**
 * Custom ListCell for displaying resources in a ComboBox.
 * Shows the resource name with URI as tooltip.
 */
public class ResourceListCell extends ListCell<ResourceDescriptor> {

    @Override
    protected void updateItem(ResourceDescriptor resource, boolean empty) {
        super.updateItem(resource, empty);

        if (empty || resource == null) {
            setText(null);
            setTooltip(null);
        } else {
            setText(resource.name());

            // Show URI and description as tooltip
            StringBuilder tooltip = new StringBuilder();
            tooltip.append("URI: ").append(resource.uri());
            if (resource.description() != null && !resource.description().isEmpty()) {
                tooltip.append("\n").append(resource.description());
            }
            if (resource.mimeType() != null) {
                tooltip.append("\nMIME Type: ").append(resource.mimeType());
            }
            setTooltip(new javafx.scene.control.Tooltip(tooltip.toString()));
        }
    }
}


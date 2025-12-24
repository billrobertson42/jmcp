package org.peacetalk.jmcp.client.ui;

import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import org.peacetalk.jmcp.core.model.Tool;

/**
 * Factory for creating custom list cells that display tool names with descriptions as tooltips.
 */
public class ToolListCell extends ListCell<Tool> {
    @Override
    protected void updateItem(Tool tool, boolean empty) {
        super.updateItem(tool, empty);

        if (empty || tool == null) {
            setText(null);
            setTooltip(null);
            setGraphic(null);
        } else {
            // Display tool name as the main text
            setText(tool.name());

            // Add description as tooltip if available
            if (tool.description() != null && !tool.description().isBlank()) {
                Tooltip tooltip = new Tooltip(tool.description());
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(400);
                setTooltip(tooltip);
            } else {
                setTooltip(null);
            }
        }
    }
}



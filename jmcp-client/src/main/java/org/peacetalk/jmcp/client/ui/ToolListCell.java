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



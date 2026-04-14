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


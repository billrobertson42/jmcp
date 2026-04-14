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

import javafx.scene.control.Hyperlink;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.function.Consumer;

/**
 * A view component that displays JSON content with clickable URI links.
 * URIs matching known patterns are rendered as hyperlinks that can be navigated.
 *
 * The font must be set via setFont() to match the TextArea font for visual consistency.
 */
public class NavigableResourceView extends VBox {

    private final TextFlow textFlow;
    private Consumer<String> onNavigate;
    private Font font;

    public NavigableResourceView() {
        this.textFlow = new TextFlow();
        getChildren().add(textFlow);

        // Match TextArea styling: white background, 8px padding
        setStyle("-fx-background-color: white; -fx-padding: 8px;");
    }

    /**
     * Set the font to use for text display. This should be called with the
     * TextArea's font to ensure visual consistency.
     *
     * @param font The font to use
     */
    public void setFont(Font font) {
        this.font = font;
    }

    /**
     * Set the callback to invoke when a URI link is clicked.
     *
     * @param onNavigate Consumer that receives the URI to navigate to
     */
    public void setOnNavigate(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
    }

    /**
     * Create a styled Text node using the configured font.
     */
    private Text createText(String content) {
        Text text = new Text(content);
        if (font != null) {
            text.setFont(font);
        }
        text.setFill(Color.BLACK);
        return text;
    }

    /**
     * Display content with navigable URI links.
     *
     * @param content The JSON content to display
     */
    public void setContent(String content) {
        textFlow.getChildren().clear();

        if (content == null || content.isEmpty()) {
            return;
        }

        // Find all navigable URIs
        List<NavigableUriDetector.NavigableUri> uris = NavigableUriDetector.findNavigableUris(content);

        if (uris.isEmpty()) {
            // No URIs - just display as plain text
            textFlow.getChildren().add(createText(content));
            return;
        }

        // Build text flow with hyperlinks for URIs
        int lastEnd = 0;
        for (NavigableUriDetector.NavigableUri uri : uris) {
            // Add text before this URI
            if (uri.startIndex() > lastEnd) {
                String before = content.substring(lastEnd, uri.startIndex());
                textFlow.getChildren().add(createText(before));
            }

            // Add the field name part (before the URI value)
            String fullMatch = content.substring(uri.startIndex(), uri.endIndex());
            int uriValueStart = fullMatch.lastIndexOf('"' + uri.uri() + '"');
            if (uriValueStart > 0) {
                String fieldPart = fullMatch.substring(0, uriValueStart + 1); // Include opening quote
                textFlow.getChildren().add(createText(fieldPart));
            }

            // Add the URI as a hyperlink
            Hyperlink link = new Hyperlink(uri.uri());
            if (font != null) {
                link.setFont(font);
            }
            link.setStyle("-fx-padding: 0; -fx-border-width: 0;");
            link.setOnAction(e -> {
                if (onNavigate != null) {
                    onNavigate.accept(uri.uri());
                }
            });
            link.setTooltip(new javafx.scene.control.Tooltip("Navigate to: " + uri.uri()));
            textFlow.getChildren().add(link);

            // Add closing quote
            textFlow.getChildren().add(createText("\""));

            lastEnd = uri.endIndex();
        }

        // Add remaining text after last URI
        if (lastEnd < content.length()) {
            String remaining = content.substring(lastEnd);
            textFlow.getChildren().add(createText(remaining));
        }
    }

    /**
     * Clear the content.
     */
    public void clear() {
        textFlow.getChildren().clear();
    }

    /**
     * Set prompt text when empty.
     *
     * @param prompt The prompt text
     */
    public void setPromptText(String prompt) {
        if (textFlow.getChildren().isEmpty()) {
            Text text = new Text(prompt);
            if (font != null) {
                text.setFont(font);
            }
            text.setFill(Color.GRAY);
            textFlow.getChildren().add(text);
        }
    }
}


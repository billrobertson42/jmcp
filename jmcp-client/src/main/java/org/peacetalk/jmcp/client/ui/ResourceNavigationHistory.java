package org.peacetalk.jmcp.client.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Manages navigation history for resource browsing using a stack model.
 * When navigating back, entries are popped and lost (no forward navigation).
 */
public class ResourceNavigationHistory {

    /**
     * Represents a single entry in the navigation history.
     */
    public record HistoryEntry(
        String uri,
        String displayContent
    ) {}

    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private HistoryEntry current;

    /**
     * Navigate to a new resource. Pushes current to history if present.
     *
     * @param uri The URI being navigated to
     * @param content The display content for this resource
     */
    public void navigateTo(String uri, String content) {
        if (current != null) {
            history.push(current);
        }
        current = new HistoryEntry(uri, content);
    }

    /**
     * Go back to the previous entry. Returns the previous entry and discards current.
     *
     * @return The previous history entry, or empty if at the beginning
     */
    public Optional<HistoryEntry> goBack() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        current = history.pop();
        return Optional.of(current);
    }

    /**
     * Check if there is history to go back to.
     *
     * @return true if back navigation is possible
     */
    public boolean canGoBack() {
        return !history.isEmpty();
    }

    /**
     * Get the current entry without modifying history.
     *
     * @return The current entry, or empty if none
     */
    public Optional<HistoryEntry> current() {
        return Optional.ofNullable(current);
    }

    /**
     * Get the current URI if present.
     *
     * @return The current URI, or empty if none
     */
    public Optional<String> currentUri() {
        return current().map(HistoryEntry::uri);
    }

    /**
     * Get the number of entries in the back history.
     *
     * @return The history depth
     */
    public int historyDepth() {
        return history.size();
    }

    /**
     * Clear all history and current entry.
     */
    public void clear() {
        history.clear();
        current = null;
    }
}


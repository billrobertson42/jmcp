package org.peacetalk.jmcp.client;

import java.util.Optional;
import java.util.prefs.Preferences;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages user preferences for the MCP Client.
 * Provides a clean API without requiring null checks throughout the code.
 */
public class ClientPreferences {
    private static final Logger LOG = LogManager.getLogger(ClientPreferences.class);
    private static final String PREF_SERVER_COMMAND = "server.command";

    private final Preferences preferences;

    /**
     * Create a new ClientPreferences instance.
     */
    public ClientPreferences() {
        this.preferences = Preferences.userNodeForPackage(ClientPreferences.class);
    }

    /**
     * Get the saved server command.
     *
     * @return The saved server command, or empty string if not set
     */
    public Optional<String> getServerCommand() {
        return Optional.ofNullable(preferences.get(PREF_SERVER_COMMAND, null));
    }

    /**
     * Save the server command.
     *
     * @param command The server command to save
     */
    public void setServerCommand(String command) {
        if (command != null && !command.isBlank()) {
            preferences.put(PREF_SERVER_COMMAND, command);
        }
    }

    /**
     * Clear all saved preferences.
     */
    public void clear() {
        try {
            preferences.clear();
        } catch (Exception e) {
            LOG.error("Error clearing preferences: {}", e.getMessage(), e);
        }
    }
}


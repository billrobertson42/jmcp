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


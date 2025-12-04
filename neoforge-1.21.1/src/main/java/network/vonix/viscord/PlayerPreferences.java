package network.vonix.viscord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player preferences for Viscord features.
 */
public class PlayerPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path preferencesFile;
    private final Map<UUID, PlayerPreference> preferences;

    public PlayerPreferences(Path configDir) throws IOException {
        this.preferencesFile = configDir.resolve("viscord-player-preferences.json");
        this.preferences = new HashMap<>();
        loadPreferences();
    }

    /**
     * Check if a player has server message filtering enabled.
     * Returns false by default (show all messages).
     */
    public boolean hasServerMessagesFiltered(UUID playerUuid) {
        PlayerPreference pref = preferences.get(playerUuid);
        if (pref != null) {
            return pref.filterServerMessages;
        }
        // Default: false (show all messages)
        return false;
    }

    /**
     * Set whether a player wants to filter server messages.
     */
    public void setServerMessagesFiltered(UUID playerUuid, boolean filtered) {
        PlayerPreference pref = preferences.computeIfAbsent(playerUuid, k -> new PlayerPreference());
        pref.filterServerMessages = filtered;
        savePreferences();
    }

    /**
     * Check if a player has event message filtering enabled.
     * Returns false by default (show all events).
     */
    public boolean hasEventsFiltered(UUID playerUuid) {
        PlayerPreference pref = preferences.get(playerUuid);
        if (pref != null) {
            return pref.filterEvents;
        }
        // Default: false (show all events)
        return false;
    }

    /**
     * Set whether a player wants to filter event messages (achievements, join/leave).
     */
    public void setEventsFiltered(UUID playerUuid, boolean filtered) {
        PlayerPreference pref = preferences.computeIfAbsent(playerUuid, k -> new PlayerPreference());
        pref.filterEvents = filtered;
        savePreferences();
    }

    /**
     * Load preferences from file.
     */
    private void loadPreferences() throws IOException {
        if (!Files.exists(preferencesFile)) {
            Viscord.LOGGER.info("Player preferences file not found, creating new one");
            savePreferences();
            return;
        }

        try {
            String json = Files.readString(preferencesFile);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            if (root != null && root.has("players")) {
                JsonObject playersObj = root.getAsJsonObject("players");
                for (String uuidStr : playersObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        JsonObject prefObj = playersObj.getAsJsonObject(uuidStr);
                        PlayerPreference pref = new PlayerPreference();

                        if (prefObj.has("filterServerMessages")) {
                            pref.filterServerMessages = prefObj.get("filterServerMessages").getAsBoolean();
                        }
                        if (prefObj.has("filterEvents")) {
                            pref.filterEvents = prefObj.get("filterEvents").getAsBoolean();
                        }

                        preferences.put(uuid, pref);
                    } catch (IllegalArgumentException e) {
                        Viscord.LOGGER.warn("Invalid UUID in preferences file: {}", uuidStr);
                    }
                }
            }

            Viscord.LOGGER.info("Loaded preferences for {} players", preferences.size());
        } catch (Exception e) {
            Viscord.LOGGER.error("Failed to load player preferences", e);
        }
    }

    /**
     * Save preferences to file.
     */
    private void savePreferences() {
        try {
            JsonObject root = new JsonObject();
            JsonObject playersObj = new JsonObject();

            for (Map.Entry<UUID, PlayerPreference> entry : preferences.entrySet()) {
                JsonObject prefObj = new JsonObject();
                prefObj.addProperty("filterServerMessages", entry.getValue().filterServerMessages);
                prefObj.addProperty("filterEvents", entry.getValue().filterEvents);
                playersObj.add(entry.getKey().toString(), prefObj);
            }

            root.add("players", playersObj);

            String json = GSON.toJson(root);
            Files.writeString(preferencesFile, json);

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Saved player preferences to file");
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Failed to save player preferences", e);
        }
    }

    /**
     * Internal class to hold player preferences.
     */
    private static class PlayerPreference {
        boolean filterServerMessages = false; // Default to showing all messages
        boolean filterEvents = false; // Default to showing all events (achievements, join/leave)
    }
}

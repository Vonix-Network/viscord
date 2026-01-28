package com.hypixel.hytale.server.core.universe;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.Collection;
import java.util.Collections;

/**
 * Stub for Hytale's Universe - provides access to all players.
 */
public class Universe {

    private static Universe instance;

    public static Universe getInstance() {
        if (instance == null) {
            instance = new Universe();
        }
        return instance;
    }

    /**
     * Get all online players.
     */
    public Collection<Player> getPlayers() {
        return Collections.emptyList(); // Stub
    }

    /**
     * Broadcast a message to all players.
     */
    public void broadcast(Message message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }
    }

    /**
     * Broadcast a raw string to all players.
     */
    public void broadcast(String message) {
        broadcast(Message.raw(message));
    }

    /**
     * Get the number of online players.
     */
    public int getPlayerCount() {
        return getPlayers().size();
    }
}

package com.hypixel.hytale.server.core.entity.entities;

import com.hypixel.hytale.server.core.Message;
import java.util.UUID;

/**
 * Stub for Hytale's Player entity.
 * Correct path: com.hypixel.hytale.server.core.entity.entities.Player
 */
public interface Player {

    UUID getUuid();

    String getDisplayName();

    String getName();

    void sendMessage(Message message);

    void sendMessage(String message);
}

# Server Messages Command Implementation Guide

This guide explains how to add the `/discord servermessages enable/disable` command to all 3 versions of Viscord.

## Overview

The `/discord servermessages` command allows players to control whether they see messages from bots and webhooks (messages from other servers) in their Minecraft chat. When disabled, players only see messages from Discord users and their own server.

## Implementation Status

### Forge 1.20.1 ✅
- [x] Config option added (`FILTER_SERVER_MESSAGES`)
- [x] PlayerPreferences class created
- [x] DiscordManager updated with preference system
- [x] Message filtering logic implemented
- [ ] Command registration (needs manual completion)

### Forge 1.21.1 ⏳
- Needs full implementation

### NeoForge 1.21.1 ⏳
- Needs full implementation

## Files Modified/Created

### 1. Config.java
**Location:** `src/main/java/network/vonix/viscord/Config.java`

**Change:** Add new config option in the Multi-Server Settings section (around line 298-307):

```java
public static final ForgeConfigSpec.BooleanValue FILTER_SERVER_MESSAGES =
    BUILDER.comment(
        "Filter chat messages from bots and webhooks (other servers) in Minecraft",
        "When enabled, players only see messages from Discord users and their own server",
        "Can be toggled per-player with /discord servermessages enable/disable",
        "Default: false (show all messages)"
    ).define("filterServerMessages", false);
```

### 2. PlayerPreferences.java (NEW FILE)
**Location:** `src/main/java/network/vonix/viscord/PlayerPreferences.java`

**Purpose:** Manages per-player settings for message filtering

**Full file content:** (See the created file in forge-1.20.1-47.4.0-mdk)

### 3. DiscordManager.java

**Changes needed:**

a) **Add import:**
```java
import java.util.UUID;
```

b) **Add field (around line 46):**
```java
private PlayerPreferences playerPreferences = null; // Per-player preferences
```

c) **Initialize in `initialize()` method (after extractWebhookId() call):**
```java
// Initialize player preferences
try {
    playerPreferences = new PlayerPreferences(server.getServerDirectory().toPath().resolve("config"));
    Viscord.LOGGER.info("Player preferences system initialized");
} catch (Exception e) {
    Viscord.LOGGER.error("Failed to initialize player preferences", e);
}
```

d) **Add helper methods (after `unlinkAccount()` method):**
```java
// ========= Player Preferences =========

/**
 * Check if a player has server message filtering enabled.
 */
public boolean hasServerMessagesFiltered(UUID playerUuid) {
    if (playerPreferences == null) {
        return Config.FILTER_SERVER_MESSAGES.get();
    }
    return playerPreferences.hasServerMessagesFiltered(playerUuid);
}

/**
 * Set whether a player wants to filter server messages.
 */
public void setServerMessagesFiltered(UUID playerUuid, boolean filtered) {
    if (playerPreferences != null) {
        playerPreferences.setServerMessagesFiltered(playerUuid, filtered);
    }
}
```

e) **Update message broadcasting in `processJavacordMessage()` method:**

Find the section where messages are sent to players (around line 506-518) and replace:

```java
if (server != null) {
    Component component = toMinecraftComponentWithLinks(formattedMessage);
    server.getPlayerList().getPlayers()
        .forEach(player -> player.sendSystemMessage(component));
    // ... debug logging ...
}
```

With:

```java
if (server != null) {
    Component component = toMinecraftComponentWithLinks(formattedMessage);
    
    // Check if this is a message that can be filtered (from bots or webhooks)
    boolean isFilterableMessage = isBot || isWebhook;
    
    // Send to each player based on their preferences
    server.getPlayerList().getPlayers().forEach(player -> {
        // If it's a filterable message, check player preferences
        if (isFilterableMessage && hasServerMessagesFiltered(player.getUUID())) {
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Filtered server message for player: {}", player.getName().getString());
            }
            return; // Skip this player
        }
        player.sendSystemMessage(component);
    });
    
    // ... debug logging ...
}
```

### 4. MinecraftEventHandler.java

**Change:** Add the `/discord servermessages` command before the closing of the discord command registration.

**Location:** After the `/discord  unlink` command (around line 100), add:

```java
.then(Commands.literal("servermessages")
    .then(Commands.literal("enable")
        .executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DiscordManager.getInstance().setServerMessagesFiltered(player.getUUID(), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§aServer messages enabled! You will now see messages from other servers and bots in Discord."
            ), false);
            return 1;
        })
    )
    .then(Commands.literal("disable")
        .executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DiscordManager.getInstance().setServerMessagesFiltered(player.getUUID(), true);
            context.getSource().sendSuccess(() -> Component.literal(
                "§cServer messages disabled! You will only see messages from Discord users and your own server."
            ), false);
            return 1;
        })
    )
    .executes(context -> {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean isFiltered = DiscordManager.getInstance().hasServerMessagesFiltered(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal(
            "§7Server messages are currently: " + (isFiltered ? "§cDisabled" : "§aEnabled") + "\n" +
            "§7Use §b/discord servermessages enable§7 or §b/discord servermessages disable§7 to change."
        ), false);
        return 1;
    })
)
```

**Also update the help text** (around line 112) to include:
```java
"§b/discord servermessages§7 - Toggle server messages on/off\n" +
```

## Usage

Once implemented, players can use:
- `/discord servermessages` - Check current status
- `/discord servermessages enable` - Enable seeing messages from other servers/bots
- `/discord servermessages disable` - Disable seeing messages from other servers/bots (only see Discord users and own server)

## Configuration Files

Player preferences are stored in: `config/viscord-player-preferences.json`

Example:
```json
{
  "players": {
    "uuid-here": {
      "filterServerMessages": true
    }
  }
}
```

## Next Steps

1. Complete the MinecraftEventHandler.java changes for Forge 1.20.1
2. Copy all changes to Forge 1.21.1 version
3. Copy all changes to NeoForge 1.21.1 version
4. Test the implementation
5. Build all versions

## Notes

- The player preference system persists across server restarts
- If a player hasn't set a preference, it defaults to the global config value
- Bot and webhook messages are identified by `isBot || isWebhook` flags
- Regular Discord user messages are NEVER filtered

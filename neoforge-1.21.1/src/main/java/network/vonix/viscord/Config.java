package network.vonix.viscord;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER =
        new ModConfigSpec.Builder();

    // ====================================================================
    // DISCORD CONNECTION SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                    DISCORD CONNECTION SETTINGS                ",
            "═══════════════════════════════════════════════════════════════",
            "Configure your Discord bot token, channel, and webhook here.",
            "Required for the mod to function."
        ).push("discord");
    }

    public static final ModConfigSpec.ConfigValue<String> DISCORD_BOT_TOKEN =
        BUILDER.comment(
            "Your Discord Bot Token",
            "Get this from: https://discord.com/developers/applications",
            "1. Create or select your application",
            "2. Go to 'Bot' section",
            "3. Copy the token",
            "4. Paste it here"
        ).define("botToken", "YOUR_BOT_TOKEN_HERE");

    public static final ModConfigSpec.ConfigValue<String> DISCORD_CHANNEL_ID =
        BUILDER.comment(
            "Discord Channel ID where messages will be sent/received",
            "How to get: Right-click the channel → Copy ID",
            "(Enable Developer Mode in Discord settings first)"
        ).define("channelId", "YOUR_CHANNEL_ID_HERE");

    public static final ModConfigSpec.ConfigValue<String> DISCORD_WEBHOOK_URL =
        BUILDER.comment(
            "Discord Webhook URL for sending messages from Minecraft to Discord",
            "How to create:",
            "1. Right-click your Discord channel",
            "2. Edit Channel → Integrations → Webhooks",
            "3. New Webhook → Copy Webhook URL",
            "4. Paste it here"
        ).define(
            "webhookUrl",
            "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL"
        );

    public static final ModConfigSpec.ConfigValue<String> DISCORD_WEBHOOK_ID =
        BUILDER.comment(
            "Discord Webhook ID for message filtering (prevents loops)",
            "⚙️ Leave empty to auto-extract from webhook URL (recommended)",
            "",
            "Manual setup (only if auto-extract fails):",
            "1. Enable Discord Developer Mode",
            "2. Send a test message from Minecraft",
            "3. Right-click the webhook's name/avatar in Discord",
            "4. Copy ID and paste here"
        ).define("webhookId", "");

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // SERVER IDENTITY SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                     SERVER IDENTITY SETTINGS                  ",
            "═══════════════════════════════════════════════════════════════",
            "Customize how your server appears in Discord.",
            "Useful for multi-server setups sharing one channel."
        ).push("server");
    }

    public static final ModConfigSpec.ConfigValue<String> SERVER_PREFIX =
        BUILDER.comment(
            "Server prefix shown before player names in Discord",
            "Examples: [BMC], [Survival], [Creative], [SMP]",
            "Used in webhook username: '[BMC]PlayerName'"
        ).define("prefix", "[BMC]");

    public static final ModConfigSpec.ConfigValue<String> SERVER_NAME =
        BUILDER.comment(
            "Full server name for embeds and status messages",
            "Shown in join/leave/death/advancement notifications"
        ).define("name", "Minecraft Server");

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // MESSAGE FORMAT SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                     MESSAGE FORMAT SETTINGS                   ",
            "═══════════════════════════════════════════════════════════════",
            "Customize how messages appear in Discord and Minecraft."
        ).push("messages");
    }

    public static final ModConfigSpec.ConfigValue<
        String
    > MINECRAFT_TO_DISCORD_FORMAT = BUILDER.comment(
        "Format for chat messages sent from Minecraft to Discord",
        "Placeholder: {message}",
        "Note: Username is automatically shown in webhook name"
    ).define("minecraftToDiscord", "{message}");

    public static final ModConfigSpec.ConfigValue<
        String
    > DISCORD_TO_MINECRAFT_FORMAT = BUILDER.comment(
        "Format for messages sent from Discord to Minecraft",
        "Placeholders: {username}, {message}",
        "Minecraft color codes supported (§b = aqua, §f = white, etc.)"
    ).define(
        "discordToMinecraft",
        "§b[Discord] §f<{username}> {message}"
    );

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // EVENT NOTIFICATION SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                   EVENT NOTIFICATION SETTINGS                 ",
            "═══════════════════════════════════════════════════════════════",
            "Toggle which Minecraft events are sent to Discord.",
            "All events use rich embeds with colors and formatting."
        ).push("events");
    }

    public static final ModConfigSpec.BooleanValue SEND_JOIN_MESSAGES =
        BUILDER.comment(
            "Send player join notifications to Discord",
            "Shows as green embed with player avatar"
        ).define(
            "playerJoin",
            true
        );

    public static final ModConfigSpec.BooleanValue SEND_LEAVE_MESSAGES =
        BUILDER.comment(
            "Send player leave notifications to Discord",
            "Shows as red embed with player count"
        ).define(
            "playerLeave",
            true
        );

    public static final ModConfigSpec.BooleanValue SEND_DEATH_MESSAGES =
        BUILDER.comment(
            "Send player death messages to Discord",
            "Shows death cause (e.g., 'killed by Zombie')"
        ).define(
            "playerDeath",
            true
        );

    public static final ModConfigSpec.BooleanValue SEND_ADVANCEMENT_MESSAGES =
        BUILDER.comment(
            "Send player advancement notifications to Discord",
            "Includes advancement title and description"
        ).define(
            "playerAdvancement",
            true
        );

    public static final ModConfigSpec.ConfigValue<String> EVENT_CHANNEL_ID =
        BUILDER.comment(
            "Discord Channel ID for event messages (join/leave/death/advancement)",
            "Leave empty to use the default channel (discordChannelId)",
            "How to get: Right-click the channel → Copy ID",
            "(Enable Developer Mode in Discord settings first)"
        ).define("eventChannelId", "");

    public static final ModConfigSpec.ConfigValue<String> EVENT_WEBHOOK_URL =
        BUILDER.comment(
            "Discord Webhook URL for event messages",
            "Leave empty to use the default webhook (webhookUrl)",
            "How to create:",
            "1. Right-click your Discord channel",
            "2. Edit Channel → Integrations → Webhooks",
            "3. New Webhook → Copy Webhook URL",
            "4. Paste it here"
        ).define(
            "eventWebhookUrl",
            ""
        );

    public static final ModConfigSpec.ConfigValue<String> EVENT_WEBHOOK_ID =
        BUILDER.comment(
            "Discord Event Webhook ID for message filtering (prevents loops)",
            "⚙️ Leave empty to auto-extract from event webhook URL (recommended)",
            "",
            "Only needed if using a separate event webhook.",
            "Manual setup (only if auto-extract fails):",
            "1. Enable Discord Developer Mode",
            "2. Send a test event message from Minecraft",
            "3. Right-click the webhook's name/avatar in Discord",
            "4. Copy ID and paste here"
        ).define("eventWebhookId", "");

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // LOOP PREVENTION SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                    LOOP PREVENTION SETTINGS                   ",
            "═══════════════════════════════════════════════════════════════",
            "Prevent infinite message loops between Discord and Minecraft.",
            "⚠️ Your own webhook is ALWAYS filtered automatically!"
        ).push("loopPrevention");
    }

    public static final ModConfigSpec.BooleanValue IGNORE_BOTS =
        BUILDER.comment(
            "Ignore messages from Discord bots",
            "Recommended: true (prevents loops with other bots)"
        ).define("ignoreBots", true);

    public static final ModConfigSpec.BooleanValue IGNORE_WEBHOOKS =
        BUILDER.comment(
            "Ignore messages from OTHER Discord webhooks",
            "📌 Single server: Keep TRUE",
            "📌 Multi-server (shared channel): Set FALSE to see other servers"
        ).define("ignoreOtherWebhooks", false);

    public static final ModConfigSpec.BooleanValue IGNORE_OWN_MESSAGES =
        BUILDER.comment(
            "Ignore messages from this bot itself",
            "Recommended: true"
        ).define(
            "ignoreSelfBot",
            true
        );

    public static final ModConfigSpec.BooleanValue FILTER_BY_PREFIX =
        BUILDER.comment(
            "Only ignore webhooks that match your server prefix",
            "Allows multiple servers with different prefixes in same channel",
            "Only applies when ignoreOtherWebhooks=true"
        ).define("filterByPrefix", true);

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // MULTI-SERVER SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                      MULTI-SERVER SETTINGS                    ",
            "═══════════════════════════════════════════════════════════════",
            "Settings for running multiple servers in one Discord channel."
        ).push("multiServer");
    }

    public static final ModConfigSpec.BooleanValue SHOW_SERVER_PREFIX_IN_GAME =
        BUILDER.comment(
            "Show server prefix in-game for messages from other servers",
            "Example: '[Creative]PlayerName: Hello' instead of 'PlayerName: Hello'"
        ).define("showPrefixInGame", true);

    public static final ModConfigSpec.BooleanValue SHOW_OTHER_SERVER_EVENTS =
        BUILDER.comment(
            "Show join/leave/death/advancement events from other servers in Minecraft chat",
            "When enabled, you'll see '[Creative] PlayerName joined the server' etc.",
            "Uses embed footer detection (Viscord · Join, Viscord · Leave, etc.)"
        ).define("showOtherServerEvents", true);

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // WEBHOOK APPEARANCE SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                   WEBHOOK APPEARANCE SETTINGS                 ",
            "═══════════════════════════════════════════════════════════════",
            "Customize how player messages appear in Discord."
        ).push("webhook");
    }

    public static final ModConfigSpec.ConfigValue<
        String
    > WEBHOOK_USERNAME_FORMAT = BUILDER.comment(
        "Format for webhook username (player name in Discord)",
        "Placeholders: {prefix}, {username}",
        "Example: '[BMC]Steve' or just 'Steve'"
    ).define("usernameFormat", "{prefix}{username}");

    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_AVATAR_URL =
        BUILDER.comment(
            "URL template for player avatars in Discord",
            "Placeholders: {uuid}, {username}",
            "Default uses Crafatar for Minecraft skins",
            "Leave empty to use default Discord avatar"
        ).define(
            "avatarUrl",
            "https://crafatar.com/avatars/{uuid}?overlay"
        );

    public static final ModConfigSpec.ConfigValue<String> SERVER_AVATAR_URL =
        BUILDER.comment(
            "URL for server/event message avatars in Discord",
            "Used for startup, shutdown, join, leave, death, and advancement messages",
            "Leave empty to use default Discord avatar"
        ).define(
            "serverAvatarUrl",
            "https://i.ibb.co/PvmgMHJR/image.png"
        );

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // BOT STATUS SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                      BOT STATUS SETTINGS                      ",
            "═══════════════════════════════════════════════════════════════",
            "Configure the bot's presence/activity status."
        ).push("botStatus");
    }

    public static final ModConfigSpec.BooleanValue SET_BOT_STATUS =
        BUILDER.comment(
            "Update bot status to show current player count",
            "Shows as 'Playing X/Y players online'"
        ).define(
            "enabled",
            true
        );

    public static final ModConfigSpec.ConfigValue<String> BOT_STATUS_FORMAT =
        BUILDER.comment(
            "Format for bot status text",
            "Placeholders: {online}, {max}",
            "Example: '5/20 players online' or 'Minecraft | 5 players'"
        ).define("format", "{online}/{max} players online");

    static {
        BUILDER.pop();
    }

    // ====================================================================
    // ADVANCED SETTINGS
    // ====================================================================
    
    static {
        BUILDER.comment(
            "═══════════════════════════════════════════════════════════════",
            "                        ADVANCED SETTINGS                      ",
            "═══════════════════════════════════════════════════════════════",
            "⚠️ Only modify these if you know what you're doing!"
        ).push("advanced");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING =
        BUILDER.comment(
            "Enable detailed debug logging to server console",
            "Useful for troubleshooting connection issues",
            "⚠️ Creates more log output"
        ).define(
            "debugLogging",
            false
        );

    public static final ModConfigSpec.IntValue MESSAGE_QUEUE_SIZE =
        BUILDER.comment(
            "Maximum number of messages to queue before dropping",
            "Prevents memory issues if Discord is slow/offline",
            "Range: 10-1000, Default: 100"
        ).defineInRange("messageQueueSize", 100, 10, 1000);

    public static final ModConfigSpec.IntValue RATE_LIMIT_DELAY =
        BUILDER.comment(
            "Delay between messages in milliseconds",
            "Prevents Discord rate limiting",
            "Range: 100-5000ms, Default: 1000ms (1 second)"
        ).defineInRange("rateLimitDelay", 1000, 100, 5000);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}

package network.vonix.viscord;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {

    private static final ForgeConfigSpec.Builder BUILDER =
        new ForgeConfigSpec.Builder();

    // Discord Bot Settings
    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_BOT_TOKEN =
        BUILDER.comment(
            "Discord Bot Token - Get this from Discord Developer Portal"
        ).define("discordBotToken", "YOUR_BOT_TOKEN_HERE");

    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_CHANNEL_ID =
        BUILDER.comment(
            "Discord Channel ID where messages will be sent/received"
        ).define("discordChannelId", "YOUR_CHANNEL_ID_HERE");

    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_WEBHOOK_URL =
        BUILDER.comment(
            "Discord Webhook URL for sending messages from Minecraft to Discord"
        ).define(
            "discordWebhookUrl",
            "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL"
        );

    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_WEBHOOK_ID =
        BUILDER.comment(
            "Discord Webhook ID for filtering (prevents message loops).",
            "Leave empty to auto-extract from webhook URL (recommended).",
            "",
            "To get manually if auto-extraction fails:",
            "1. Enable Discord Developer Mode (User Settings > Advanced > Developer Mode)",
            "2. Send a message from Minecraft to Discord",
            "3. In Discord, right-click the webhook's username/avatar (e.g., '[BMC]PlayerName')",
            "4. Click 'Copy ID'",
            "5. Paste that ID here",
            "",
            "Example: 1234567890123456789"
        ).define("discordWebhookId", "");

    // Server Identity Settings
    public static final ForgeConfigSpec.ConfigValue<String> SERVER_PREFIX =
        BUILDER.comment(
            "Server prefix for webhook messages (e.g., [BMC], [Survival], [Creative])"
        ).define("serverPrefix", "[BMC]");

    public static final ForgeConfigSpec.ConfigValue<String> SERVER_NAME =
        BUILDER.comment(
            "Server name to identify this server in multi-server setups"
        ).define("serverName", "Minecraft Server");

    // Message Format Settings
    public static final ForgeConfigSpec.ConfigValue<
        String
    > MINECRAFT_TO_DISCORD_FORMAT = BUILDER.comment(
        "Format for messages sent from Minecraft to Discord. Placeholder: {message} (username is already shown in webhook name)"
    ).define("minecraftToDiscordFormat", "{message}");

    public static final ForgeConfigSpec.ConfigValue<
        String
    > DISCORD_TO_MINECRAFT_FORMAT = BUILDER.comment(
        "Format for messages sent from Discord to Minecraft. Placeholders: {username}, {message}"
    ).define(
        "discordToMinecraftFormat",
        "§b[Discord] §f<{username}> {message}"
    );

    // Join/Leave Messages
    public static final ForgeConfigSpec.BooleanValue SEND_JOIN_MESSAGES =
        BUILDER.comment("Send player join messages to Discord").define(
            "sendJoinMessages",
            true
        );

    public static final ForgeConfigSpec.BooleanValue SEND_LEAVE_MESSAGES =
        BUILDER.comment("Send player leave messages to Discord").define(
            "sendLeaveMessages",
            true
        );

    public static final ForgeConfigSpec.BooleanValue SEND_DEATH_MESSAGES =
        BUILDER.comment("Send player death messages to Discord").define(
            "sendDeathMessages",
            true
        );

    public static final ForgeConfigSpec.BooleanValue SEND_ADVANCEMENT_MESSAGES =
        BUILDER.comment("Send player advancement messages to Discord").define(
            "sendAdvancementMessages",
            true
        );

    // Loop Prevention Settings
    public static final ForgeConfigSpec.BooleanValue IGNORE_BOTS =
        BUILDER.comment(
            "Ignore messages from Discord bots (prevents message loops)"
        ).define("ignoreBots", true);

    public static final ForgeConfigSpec.BooleanValue IGNORE_WEBHOOKS =
        BUILDER.comment(
            "Ignore messages from OTHER Discord webhooks (for single-server setups).",
            "NOTE: Your own server's webhook is ALWAYS filtered to prevent loops.",
            "Set to FALSE for multi-server setups where you want to see messages from other servers."
        ).define("ignoreWebhooks", false);

    public static final ForgeConfigSpec.BooleanValue IGNORE_OWN_MESSAGES =
        BUILDER.comment("Ignore messages from this bot itself").define(
            "ignoreOwnMessages",
            true
        );

    // Multi-Server Support
    public static final ForgeConfigSpec.BooleanValue FILTER_BY_PREFIX =
        BUILDER.comment(
            "When ignoreWebhooks=true, only ignore webhooks matching your prefix (allows different-prefix servers).",
            "When ignoreWebhooks=false, this setting has no effect (all non-owned webhooks are allowed)."
        ).define("filterByPrefix", true);

    public static final ForgeConfigSpec.BooleanValue SHOW_SERVER_PREFIX_IN_GAME =
        BUILDER.comment(
            "Show the server prefix in-game for messages from other servers"
        ).define("showServerPrefixInGame", true);

    public static final ForgeConfigSpec.BooleanValue SHOW_OTHER_SERVER_EVENTS =
        BUILDER.comment(
            "Show join/leave/death/advancement events from other servers in Minecraft chat",
            "When enabled, you'll see '[Creative] PlayerName joined the server' etc.",
            "Uses embed footer detection (Viscord · Join, Viscord · Leave, etc.)"
        ).define("showOtherServerEvents", true);

    // Advanced Settings
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING =
        BUILDER.comment("Enable debug logging for troubleshooting").define(
            "enableDebugLogging",
            false
        );

    public static final ForgeConfigSpec.IntValue MESSAGE_QUEUE_SIZE =
        BUILDER.comment(
            "Maximum number of messages to queue before dropping them"
        ).defineInRange("messageQueueSize", 100, 10, 1000);

    public static final ForgeConfigSpec.IntValue RATE_LIMIT_DELAY =
        BUILDER.comment(
            "Delay between messages in milliseconds to prevent rate limiting"
        ).defineInRange("rateLimitDelay", 1000, 100, 5000);

    // Webhook Settings
    public static final ForgeConfigSpec.ConfigValue<
        String
    > WEBHOOK_USERNAME_FORMAT = BUILDER.comment(
        "Format for webhook username. Placeholders: {prefix}, {username}"
    ).define("webhookUsernameFormat", "{prefix}{username}");

    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_AVATAR_URL =
        BUILDER.comment(
            "URL template for player avatars in Discord",
            "Placeholders: {uuid}, {username}",
            "Default uses Crafatar for Minecraft skins",
            "Leave empty to use default Discord avatar"
        ).define(
            "avatarUrl",
            "https://crafatar.com/avatars/{uuid}?overlay"
        );

    public static final ForgeConfigSpec.ConfigValue<String> SERVER_AVATAR_URL =
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

    public static final ForgeConfigSpec.BooleanValue SET_BOT_STATUS =
        BUILDER.comment("Set bot status to show player count").define(
            "setBotStatus",
            true
        );

    public static final ForgeConfigSpec.ConfigValue<String> BOT_STATUS_FORMAT =
        BUILDER.comment(
            "Bot status format. Placeholders: {online}, {max}"
        ).define("botStatusFormat", "{online}/{max} players online");

    static final ForgeConfigSpec SPEC = BUILDER.build();
}

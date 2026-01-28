package network.vonix.viscord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Configuration system for Viscord using JSON file storage.
 * Mirrors all options from the NeoForge/Forge version.
 */
public class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "viscord.json";
    private static Path configPath;
    private static JsonObject configData;

    // ====================================================================
    // DISCORD CONNECTION SETTINGS
    // ====================================================================

    public static final ConfigValue<String> DISCORD_BOT_TOKEN = new ConfigValue<>(
            "discord.botToken", "YOUR_BOT_TOKEN_HERE");

    public static final ConfigValue<String> DISCORD_CHANNEL_ID = new ConfigValue<>(
            "discord.channelId", "YOUR_CHANNEL_ID_HERE");

    public static final ConfigValue<String> DISCORD_WEBHOOK_URL = new ConfigValue<>(
            "discord.webhookUrl", "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL");

    public static final ConfigValue<String> DISCORD_INVITE_URL = new ConfigValue<>(
            "discord.discordInviteUrl", "");

    public static final ConfigValue<String> DISCORD_WEBHOOK_ID = new ConfigValue<>(
            "discord.webhookId", "");

    // ====================================================================
    // SERVER IDENTITY SETTINGS
    // ====================================================================

    public static final ConfigValue<String> SERVER_PREFIX = new ConfigValue<>(
            "server.prefix", "[BMC]");

    public static final ConfigValue<String> SERVER_NAME = new ConfigValue<>(
            "server.name", "Minecraft Server");

    // ====================================================================
    // MESSAGE FORMAT SETTINGS
    // ====================================================================

    public static final ConfigValue<String> MINECRAFT_TO_DISCORD_FORMAT = new ConfigValue<>(
            "messages.minecraftToDiscord", "{message}");

    public static final ConfigValue<String> DISCORD_TO_MINECRAFT_FORMAT = new ConfigValue<>(
            "messages.discordToMinecraft", "§b[Discord] §f<{username}> {message}");

    // ====================================================================
    // EVENT NOTIFICATION SETTINGS
    // ====================================================================

    public static final ConfigValue<Boolean> SEND_JOIN_MESSAGES = new ConfigValue<>(
            "events.playerJoin", true);

    public static final ConfigValue<Boolean> SEND_LEAVE_MESSAGES = new ConfigValue<>(
            "events.playerLeave", true);

    public static final ConfigValue<Boolean> SEND_DEATH_MESSAGES = new ConfigValue<>(
            "events.playerDeath", true);

    public static final ConfigValue<Boolean> SEND_ADVANCEMENT_MESSAGES = new ConfigValue<>(
            "events.playerAdvancement", true);

    public static final ConfigValue<String> EVENT_CHANNEL_ID = new ConfigValue<>(
            "events.eventChannelId", "");

    public static final ConfigValue<String> EVENT_WEBHOOK_URL = new ConfigValue<>(
            "events.eventWebhookUrl", "");

    public static final ConfigValue<String> EVENT_WEBHOOK_ID = new ConfigValue<>(
            "events.eventWebhookId", "");

    // ====================================================================
    // LOOP PREVENTION SETTINGS
    // ====================================================================

    public static final ConfigValue<Boolean> IGNORE_BOTS = new ConfigValue<>(
            "loopPrevention.ignoreBots", false);

    public static final ConfigValue<Boolean> IGNORE_WEBHOOKS = new ConfigValue<>(
            "loopPrevention.ignoreOtherWebhooks", false);

    public static final ConfigValue<Boolean> IGNORE_OWN_MESSAGES = new ConfigValue<>(
            "loopPrevention.ignoreSelfBot", true);

    public static final ConfigValue<Boolean> FILTER_BY_PREFIX = new ConfigValue<>(
            "loopPrevention.filterByPrefix", true);

    // ====================================================================
    // MULTI-SERVER SETTINGS
    // ====================================================================

    public static final ConfigValue<Boolean> SHOW_SERVER_PREFIX_IN_GAME = new ConfigValue<>(
            "multiServer.showPrefixInGame", true);

    public static final ConfigValue<Boolean> SHOW_OTHER_SERVER_EVENTS = new ConfigValue<>(
            "multiServer.showOtherServerEvents", true);

    // ====================================================================
    // WEBHOOK APPEARANCE SETTINGS
    // ====================================================================

    public static final ConfigValue<String> WEBHOOK_USERNAME_FORMAT = new ConfigValue<>(
            "webhook.usernameFormat", "{prefix}{username}");

    public static final ConfigValue<String> WEBHOOK_AVATAR_URL = new ConfigValue<>(
            "webhook.avatarUrl", "https://minotar.net/armor/bust/{uuid}/100.png");

    public static final ConfigValue<String> SERVER_AVATAR_URL = new ConfigValue<>(
            "webhook.serverAvatarUrl", "https://i.ibb.co/PvmgMHJR/image.png");

    // ====================================================================
    // BOT STATUS SETTINGS
    // ====================================================================

    public static final ConfigValue<Boolean> SET_BOT_STATUS = new ConfigValue<>(
            "botStatus.enabled", true);

    public static final ConfigValue<String> BOT_STATUS_FORMAT = new ConfigValue<>(
            "botStatus.format", "{online}/{max} players online");

    // ====================================================================
    // ACCOUNT LINKING SETTINGS
    // ====================================================================

    public static final ConfigValue<Boolean> ENABLE_ACCOUNT_LINKING = new ConfigValue<>(
            "accountLinking.enableAccountLinking", true);

    public static final ConfigValue<Boolean> SHOW_LINKED_NAMES_IN_EMBEDS = new ConfigValue<>(
            "accountLinking.showLinkedNames", true);

    public static final ConfigValue<Integer> LINK_CODE_EXPIRY_SECONDS = new ConfigValue<>(
            "accountLinking.linkCodeExpiry", 300);

    // ====================================================================
    // UPDATE CHECKER SETTINGS
    // ====================================================================

    public static final ConfigValue<Boolean> ENABLE_UPDATE_CHECKER = new ConfigValue<>(
            "updateChecker.enableUpdateChecker", true);

    public static final ConfigValue<Boolean> NOTIFY_OPS_IN_GAME = new ConfigValue<>(
            "updateChecker.notifyOpsInGame", true);

    // ====================================================================
    // ADVANCED SETTINGS
    // ====================================================================

    public static final ConfigValue<Boolean> ENABLE_DEBUG_LOGGING = new ConfigValue<>(
            "advanced.debugLogging", false);

    public static final ConfigValue<Integer> MESSAGE_QUEUE_SIZE = new ConfigValue<>(
            "advanced.messageQueueSize", 100);

    public static final ConfigValue<Integer> RATE_LIMIT_DELAY = new ConfigValue<>(
            "advanced.rateLimitDelay", 1000);

    // ====================================================================
    // CONFIG LOADING/SAVING
    // ====================================================================

    public static void load() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);

        if (Files.exists(configPath)) {
            try (Reader reader = new FileReader(configPath.toFile())) {
                configData = GSON.fromJson(reader, JsonObject.class);
                if (configData == null) {
                    configData = new JsonObject();
                }
                Viscord.LOGGER.info("Loaded Viscord configuration from {}", configPath);
            } catch (Exception e) {
                Viscord.LOGGER.error("Failed to load config, using defaults", e);
                configData = new JsonObject();
            }
        } else {
            configData = new JsonObject();
            Viscord.LOGGER.info("Config file not found, creating default config at {}", configPath);
        }

        // Save to ensure all keys exist with defaults
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = new FileWriter(configPath.toFile())) {
                // Build complete config structure
                JsonObject fullConfig = buildConfigStructure();
                GSON.toJson(fullConfig, writer);
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Failed to save config", e);
        }
    }

    public static void reload() {
        load();
        Viscord.LOGGER.info("Viscord configuration reloaded");
    }

    private static JsonObject buildConfigStructure() {
        JsonObject root = new JsonObject();

        // Discord settings
        JsonObject discord = new JsonObject();
        discord.addProperty("botToken", DISCORD_BOT_TOKEN.get());
        discord.addProperty("channelId", DISCORD_CHANNEL_ID.get());
        discord.addProperty("webhookUrl", DISCORD_WEBHOOK_URL.get());
        discord.addProperty("discordInviteUrl", DISCORD_INVITE_URL.get());
        discord.addProperty("webhookId", DISCORD_WEBHOOK_ID.get());
        root.add("discord", discord);

        // Server settings
        JsonObject server = new JsonObject();
        server.addProperty("prefix", SERVER_PREFIX.get());
        server.addProperty("name", SERVER_NAME.get());
        root.add("server", server);

        // Message settings
        JsonObject messages = new JsonObject();
        messages.addProperty("minecraftToDiscord", MINECRAFT_TO_DISCORD_FORMAT.get());
        messages.addProperty("discordToMinecraft", DISCORD_TO_MINECRAFT_FORMAT.get());
        root.add("messages", messages);

        // Event settings
        JsonObject events = new JsonObject();
        events.addProperty("playerJoin", SEND_JOIN_MESSAGES.get());
        events.addProperty("playerLeave", SEND_LEAVE_MESSAGES.get());
        events.addProperty("playerDeath", SEND_DEATH_MESSAGES.get());
        events.addProperty("playerAdvancement", SEND_ADVANCEMENT_MESSAGES.get());
        events.addProperty("eventChannelId", EVENT_CHANNEL_ID.get());
        events.addProperty("eventWebhookUrl", EVENT_WEBHOOK_URL.get());
        events.addProperty("eventWebhookId", EVENT_WEBHOOK_ID.get());
        root.add("events", events);

        // Loop prevention settings
        JsonObject loopPrevention = new JsonObject();
        loopPrevention.addProperty("ignoreBots", IGNORE_BOTS.get());
        loopPrevention.addProperty("ignoreOtherWebhooks", IGNORE_WEBHOOKS.get());
        loopPrevention.addProperty("ignoreSelfBot", IGNORE_OWN_MESSAGES.get());
        loopPrevention.addProperty("filterByPrefix", FILTER_BY_PREFIX.get());
        root.add("loopPrevention", loopPrevention);

        // Multi-server settings
        JsonObject multiServer = new JsonObject();
        multiServer.addProperty("showPrefixInGame", SHOW_SERVER_PREFIX_IN_GAME.get());
        multiServer.addProperty("showOtherServerEvents", SHOW_OTHER_SERVER_EVENTS.get());
        root.add("multiServer", multiServer);

        // Webhook settings
        JsonObject webhook = new JsonObject();
        webhook.addProperty("usernameFormat", WEBHOOK_USERNAME_FORMAT.get());
        webhook.addProperty("avatarUrl", WEBHOOK_AVATAR_URL.get());
        webhook.addProperty("serverAvatarUrl", SERVER_AVATAR_URL.get());
        root.add("webhook", webhook);

        // Bot status settings
        JsonObject botStatus = new JsonObject();
        botStatus.addProperty("enabled", SET_BOT_STATUS.get());
        botStatus.addProperty("format", BOT_STATUS_FORMAT.get());
        root.add("botStatus", botStatus);

        // Account linking settings
        JsonObject accountLinking = new JsonObject();
        accountLinking.addProperty("enableAccountLinking", ENABLE_ACCOUNT_LINKING.get());
        accountLinking.addProperty("showLinkedNames", SHOW_LINKED_NAMES_IN_EMBEDS.get());
        accountLinking.addProperty("linkCodeExpiry", LINK_CODE_EXPIRY_SECONDS.get());
        root.add("accountLinking", accountLinking);

        // Update checker settings
        JsonObject updateChecker = new JsonObject();
        updateChecker.addProperty("enableUpdateChecker", ENABLE_UPDATE_CHECKER.get());
        updateChecker.addProperty("notifyOpsInGame", NOTIFY_OPS_IN_GAME.get());
        root.add("updateChecker", updateChecker);

        // Advanced settings
        JsonObject advanced = new JsonObject();
        advanced.addProperty("debugLogging", ENABLE_DEBUG_LOGGING.get());
        advanced.addProperty("messageQueueSize", MESSAGE_QUEUE_SIZE.get());
        advanced.addProperty("rateLimitDelay", RATE_LIMIT_DELAY.get());
        root.add("advanced", advanced);

        return root;
    }

    /**
     * Config value wrapper that provides lazy loading from JSON
     */
    public static class ConfigValue<T> implements Supplier<T> {
        private final String path;
        private final T defaultValue;

        public ConfigValue(String path, T defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get() {
            if (configData == null) {
                return defaultValue;
            }

            String[] parts = path.split("\\.");
            JsonObject current = configData;

            for (int i = 0; i < parts.length - 1; i++) {
                if (current.has(parts[i]) && current.get(parts[i]).isJsonObject()) {
                    current = current.getAsJsonObject(parts[i]);
                } else {
                    return defaultValue;
                }
            }

            String key = parts[parts.length - 1];
            if (!current.has(key)) {
                return defaultValue;
            }

            try {
                if (defaultValue instanceof String) {
                    return (T) current.get(key).getAsString();
                } else if (defaultValue instanceof Boolean) {
                    return (T) Boolean.valueOf(current.get(key).getAsBoolean());
                } else if (defaultValue instanceof Integer) {
                    return (T) Integer.valueOf(current.get(key).getAsInt());
                } else if (defaultValue instanceof Long) {
                    return (T) Long.valueOf(current.get(key).getAsLong());
                } else if (defaultValue instanceof Double) {
                    return (T) Double.valueOf(current.get(key).getAsDouble());
                }
            } catch (Exception e) {
                Viscord.LOGGER.warn("Failed to parse config value {}, using default", path);
            }

            return defaultValue;
        }

        public void set(T value) {
            if (configData == null) {
                return;
            }

            String[] parts = path.split("\\.");
            JsonObject current = configData;

            for (int i = 0; i < parts.length - 1; i++) {
                if (!current.has(parts[i])) {
                    current.add(parts[i], new JsonObject());
                }
                current = current.getAsJsonObject(parts[i]);
            }

            String key = parts[parts.length - 1];
            if (value instanceof String) {
                current.addProperty(key, (String) value);
            } else if (value instanceof Boolean) {
                current.addProperty(key, (Boolean) value);
            } else if (value instanceof Number) {
                current.addProperty(key, (Number) value);
            }

            save();
        }
    }
}

package network.vonix.viscord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import okhttp3.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandInteraction;

/**
 * Discord integration using Javacord + Webhooks.
 * - Game → Discord: Webhooks for messages and embeds (fast and efficient)
 * - Discord → Game: Javacord gateway for message reception
 * - Bot Status: Javacord API for real-time player count updates
 * - Slash Commands: Javacord API for /list command
 */
public class DiscordManager {

    private static DiscordManager instance;
    private Object server; // Hytale server instance
    private final OkHttpClient httpClient;
    private final BlockingQueue<WebhookMessage> messageQueue;
    private Thread messageQueueThread;
    private boolean running = false;
    private String ourWebhookId = null;
    private String eventWebhookId = null;
    private DiscordApi discordApi = null;
    private LinkedAccountsManager linkedAccountsManager = null;
    private PlayerPreferences playerPreferences = null;

    // Simple Markdown-style link pattern: [text](https://url)
    private static final Pattern DISCORD_MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");

    private DiscordManager() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.messageQueue = new LinkedBlockingQueue<>(
                Config.MESSAGE_QUEUE_SIZE.get());
    }

    public static DiscordManager getInstance() {
        if (instance == null) {
            instance = new DiscordManager();
        }
        return instance;
    }

    public void initialize(Object server) {
        this.server = server;
        String token = Config.DISCORD_BOT_TOKEN.get();

        if (!ConfigValidator.requireConfigured(token, "YOUR_BOT_TOKEN_HERE", "Discord bot token")) {
            return;
        }

        Viscord.LOGGER.info("Starting Discord integration (Javacord + Webhooks mode)...");

        // Extract webhook ID from URL for precise filtering
        extractWebhookId();

        // Initialize player preferences
        try {
            Path configDir = Path.of("config");
            playerPreferences = new PlayerPreferences(configDir);
            Viscord.LOGGER.info("Player preferences system initialized");
        } catch (Exception e) {
            Viscord.LOGGER.error("Failed to initialize player preferences", e);
        }

        running = true;
        startMessageQueueThread();

        // Initialize Javacord for bot status and slash commands
        initializeJavacord(token);

        // Send startup embed
        String serverName = Config.SERVER_NAME.get();
        sendStartupEmbed(serverName);

        Viscord.LOGGER.info("Discord integration initialized successfully!");
        Viscord.LOGGER.info("Mode: Javacord gateway + Webhooks (full feature support)");
        if (ourWebhookId != null) {
            Viscord.LOGGER.info("Chat Webhook ID: {} (messages from this webhook will be filtered)", ourWebhookId);
        }
        if (eventWebhookId != null) {
            Viscord.LOGGER.info("Event Webhook ID: {} (messages from this webhook will be filtered)", eventWebhookId);
        }
    }

    public void shutdown() {
        Viscord.LOGGER.info("Shutting down Discord integration...");
        running = false;

        // Stop message queue thread
        if (messageQueueThread != null && messageQueueThread.isAlive()) {
            messageQueueThread.interrupt();
            try {
                messageQueueThread.join(2000);
                if (messageQueueThread.isAlive()) {
                    Viscord.LOGGER.warn("Message queue thread did not stop gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Properly disconnect Javacord
        if (discordApi != null) {
            try {
                Viscord.LOGGER.info("Disconnecting Javacord...");
                discordApi.disconnect().get(5, TimeUnit.SECONDS);
                Viscord.LOGGER.info("Javacord disconnected successfully");
            } catch (Exception e) {
                Viscord.LOGGER.warn("Javacord disconnect timeout or error: {}", e.getMessage());
            } finally {
                discordApi = null;
            }
        }

        // Properly shutdown HTTP client resources
        if (httpClient != null) {
            try {
                httpClient.dispatcher().executorService().shutdown();
                httpClient.connectionPool().evictAll();
                if (!httpClient.dispatcher().executorService().awaitTermination(3, TimeUnit.SECONDS)) {
                    Viscord.LOGGER.warn("HTTP client executor did not terminate, forcing shutdown");
                    httpClient.dispatcher().executorService().shutdownNow();
                }
                Viscord.LOGGER.info("HTTP client shut down");
            } catch (InterruptedException e) {
                httpClient.dispatcher().executorService().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        Viscord.LOGGER.info("Discord integration shut down complete");
    }

    public boolean isRunning() {
        return running;
    }

    // ========= Account Linking =========

    public String generateLinkCode(UUID playerUuid, String playerName) {
        if (linkedAccountsManager == null || !Config.ENABLE_ACCOUNT_LINKING.get()) {
            return null;
        }
        return linkedAccountsManager.generateLinkCode(playerUuid, playerName);
    }

    public boolean unlinkAccount(UUID uuid) {
        if (linkedAccountsManager == null || !Config.ENABLE_ACCOUNT_LINKING.get()) {
            return false;
        }
        return linkedAccountsManager.unlinkMinecraft(uuid);
    }

    public void reloadConfig() {
        Viscord.LOGGER.info("Reloading Viscord configuration...");

        // Reload config from file
        Config.reload();

        // Re-extract webhook IDs from potentially updated config
        extractWebhookId();
        Viscord.LOGGER.info("Webhook IDs refreshed from config");

        // Update bot status with new settings
        if (discordApi != null && running) {
            updateBotStatus();
            Viscord.LOGGER.info("Bot status updated");
        }

        Viscord.LOGGER.info("Config reload complete!");
    }

    // ========= Player Preferences =========

    public boolean hasServerMessagesFiltered(UUID playerUuid) {
        if (playerPreferences == null) {
            return false;
        }
        return playerPreferences.hasServerMessagesFiltered(playerUuid);
    }

    public void setServerMessagesFiltered(UUID playerUuid, boolean filtered) {
        if (playerPreferences != null) {
            playerPreferences.setServerMessagesFiltered(playerUuid, filtered);
        }
    }

    public boolean hasEventsFiltered(UUID playerUuid) {
        if (playerPreferences == null) {
            return false;
        }
        return playerPreferences.hasEventsFiltered(playerUuid);
    }

    public void setEventsFiltered(UUID playerUuid, boolean filtered) {
        if (playerPreferences != null) {
            playerPreferences.setEventsFiltered(playerUuid, filtered);
        }
    }

    // ========= Webhook ID Extraction =========

    private void extractWebhookId() {
        ourWebhookId = extractWebhookIdFromConfig(
                Config.DISCORD_WEBHOOK_ID.get(),
                Config.DISCORD_WEBHOOK_URL.get(),
                "chat");
        eventWebhookId = extractWebhookIdFromConfig(
                Config.EVENT_WEBHOOK_ID.get(),
                Config.EVENT_WEBHOOK_URL.get(),
                "event");
    }

    private String extractWebhookIdFromConfig(String manualId, String webhookUrl, String type) {
        if (manualId != null && !manualId.isEmpty()) {
            Viscord.LOGGER.info("Using manually configured {} webhook ID: {}", type, manualId);
            return manualId;
        }

        if (ConfigValidator.isConfigured(webhookUrl, "YOUR_WEBHOOK_URL")) {
            String id = extractIdFromWebhookUrl(webhookUrl);
            if (id != null) {
                Viscord.LOGGER.info("Auto-extracted {} webhook ID from URL: {}", type, id);
            }
            return id;
        }

        if (type.equals("chat")) {
            Viscord.LOGGER.warn("Chat webhook ID not configured. Filtering may not work properly.");
        }
        return null;
    }

    private String extractIdFromWebhookUrl(String webhookUrl) {
        try {
            String[] parts = webhookUrl.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("webhooks".equals(parts[i]) && i + 1 < parts.length) {
                    return parts[i + 1];
                }
            }
            Viscord.LOGGER.warn("Could not extract webhook ID from URL");
        } catch (Exception e) {
            Viscord.LOGGER.error("Error extracting webhook ID from URL", e);
        }
        return null;
    }

    // ========= Javacord Initialization =========

    private void initializeJavacord(String botToken) {
        try {
            String channelId = Config.DISCORD_CHANNEL_ID.get();
            if (channelId == null || channelId.equals("YOUR_CHANNEL_ID_HERE")) {
                Viscord.LOGGER.warn("Channel ID not configured, Javacord features limited");
            }

            Viscord.LOGGER.info("Connecting to Discord via Javacord...");

            discordApi = new DiscordApiBuilder()
                    .setToken(botToken)
                    .setIntents(Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)
                    .login()
                    .join();

            Viscord.LOGGER.info("Javacord connected successfully! Bot: {}", discordApi.getYourself().getName());

            // Register message listener
            if (channelId != null && !channelId.equals("YOUR_CHANNEL_ID_HERE")) {
                long channelIdLong = Long.parseLong(channelId);
                String eventChannelId = Config.EVENT_CHANNEL_ID.get();
                Long eventChannelIdLong = null;

                if (eventChannelId != null && !eventChannelId.isEmpty()) {
                    try {
                        eventChannelIdLong = Long.parseLong(eventChannelId);
                    } catch (NumberFormatException e) {
                        Viscord.LOGGER.warn("Invalid event channel ID: {}", eventChannelId);
                    }
                }

                final Long finalEventChannelId = eventChannelIdLong;
                discordApi.addMessageCreateListener(event -> {
                    long msgChannelId = event.getChannel().getId();
                    if (msgChannelId == channelIdLong
                            || (finalEventChannelId != null && msgChannelId == finalEventChannelId)) {
                        processJavacordMessage(event);
                    }
                });

                Viscord.LOGGER.info("Message listener registered for channel {}", channelId);
            }

            // Initialize account linking manager
            if (Config.ENABLE_ACCOUNT_LINKING.get()) {
                try {
                    Path configDir = Path.of("config");
                    linkedAccountsManager = new LinkedAccountsManager(configDir);
                    Viscord.LOGGER.info("Account linking system initialized ({} accounts linked)",
                            linkedAccountsManager.getLinkedCount());
                } catch (Exception e) {
                    Viscord.LOGGER.error("Failed to initialize account linking", e);
                }
            }

            // Register slash commands
            registerListCommand();
            if (Config.ENABLE_ACCOUNT_LINKING.get() && linkedAccountsManager != null) {
                registerLinkCommands();
            }

            // Set initial bot status
            updateBotStatus();

        } catch (Exception e) {
            Viscord.LOGGER.error("Failed to initialize Javacord", e);
            discordApi = null;
        }
    }

    private void processJavacordMessage(org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            boolean isBot = event.getMessageAuthor().asUser().map(user -> user.isBot()).orElse(false);
            boolean isWebhook = !event.getMessageAuthor().asUser().isPresent();
            String content = event.getMessageContent();
            String authorName = event.getMessageAuthor().getDisplayName();

            // Handle !list command
            if (content.trim().equalsIgnoreCase("!list")) {
                handleTextListCommand(event);
                return;
            }

            // Filter our own webhooks
            if (isWebhook) {
                String authorId = String.valueOf(event.getMessageAuthor().getId());
                if ((ourWebhookId != null && ourWebhookId.equals(authorId)) ||
                        (eventWebhookId != null && eventWebhookId.equals(authorId))) {
                    return;
                }
            }

            // Filter bots if configured
            if (Config.IGNORE_BOTS.get() && isBot && !isWebhook) {
                return;
            }

            // Filter other webhooks if configured
            if (Config.IGNORE_WEBHOOKS.get() && isWebhook) {
                if (Config.FILTER_BY_PREFIX.get()) {
                    String ourPrefix = Config.SERVER_PREFIX.get();
                    if (authorName.contains(ourPrefix)) {
                        return;
                    }
                } else {
                    return;
                }
            }

            if (content.isEmpty()) {
                return;
            }

            String formattedMessage = Config.DISCORD_TO_MINECRAFT_FORMAT.get()
                    .replace("{username}", authorName)
                    .replace("{message}", content);

            // Broadcast to all players - Hytale specific implementation needed
            broadcastToPlayers(formattedMessage, isBot || isWebhook);

        } catch (Exception e) {
            Viscord.LOGGER.error("Error processing Discord message", e);
        }
    }

    /**
     * Broadcast a message to all players in-game.
     * Uses Hytale's ServerPlayerListModule for player messaging.
     */
    private void broadcastToPlayers(String message, boolean isFilterable) {
        try {
            // Use reflection to access Hytale's Universe and send messages
            // This prevents NoClassDefFoundError if the API classes are different

            Class<?> universeClass = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            java.lang.reflect.Method getInstanceMethod = universeClass.getMethod("getInstance");
            Object universe = getInstanceMethod.invoke(null);

            if (universe == null) {
                Viscord.LOGGER.debug("Universe is null, cannot broadcast");
                return;
            }

            // Get players from universe
            java.lang.reflect.Method getPlayersMethod = universeClass.getMethod("getPlayers");
            Object playersCollection = getPlayersMethod.invoke(universe);

            if (playersCollection == null) {
                Viscord.LOGGER.debug("Players collection is null");
                return;
            }

            // Create Hytale Message using reflection
            Class<?> messageClass = Class.forName("com.hypixel.hytale.server.core.Message");
            java.lang.reflect.Method rawMethod = messageClass.getMethod("raw", String.class);
            Object hytaleMessage = rawMethod.invoke(null, message);

            // Iterate over players and send message
            int sentCount = 0;
            for (Object player : (Iterable<?>) playersCollection) {
                try {
                    // Check if player has messages filtered (if filterable)
                    if (isFilterable) {
                        java.lang.reflect.Method getUuidMethod = player.getClass().getMethod("getUuid");
                        Object uuidObj = getUuidMethod.invoke(player);
                        if (uuidObj != null) {
                            UUID playerUuid = uuidObj instanceof UUID ? (UUID) uuidObj
                                    : UUID.fromString(uuidObj.toString());
                            if (hasServerMessagesFiltered(playerUuid)) {
                                continue;
                            }
                        }
                    }

                    // Try sendMessage with Message object first
                    try {
                        java.lang.reflect.Method sendMsgMethod = player.getClass().getMethod("sendMessage",
                                messageClass);
                        sendMsgMethod.invoke(player, hytaleMessage);
                        sentCount++;
                    } catch (NoSuchMethodException e) {
                        // Try sendMessage with String as fallback
                        try {
                            java.lang.reflect.Method sendMsgMethod = player.getClass().getMethod("sendMessage",
                                    String.class);
                            sendMsgMethod.invoke(player, message);
                            sentCount++;
                        } catch (NoSuchMethodException e2) {
                            Viscord.LOGGER.debug("No sendMessage method found on player");
                        }
                    }
                } catch (Exception e) {
                    if (Config.ENABLE_DEBUG_LOGGING.get()) {
                        Viscord.LOGGER.debug("Failed to send message to player: {}", e.getMessage());
                    }
                }
            }

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Broadcast to {} players: {}", sentCount, message);
            }
        } catch (ClassNotFoundException e) {
            Viscord.LOGGER.warn("Hytale Universe/Message class not found. Discord→Hytale chat disabled.");
        } catch (Exception e) {
            Viscord.LOGGER.error("Failed to broadcast message to players", e);
        }
    }

    // ========= Hytale → Discord =========

    /**
     * Send a Hytale chat message to Discord.
     * Alias for sendMinecraftMessage for Hytale compatibility.
     */
    public void sendHytaleMessage(String username, String message) {
        sendMinecraftMessage(username, message);
    }

    public void sendMinecraftMessage(String username, String message) {
        if (!running) {
            return;
        }

        String webhookUrl = Config.DISCORD_WEBHOOK_URL.get();
        if (!ConfigValidator.warnIfNotConfigured(webhookUrl, "YOUR_WEBHOOK_URL", "Discord webhook URL")) {
            return;
        }

        String prefix = Config.SERVER_PREFIX.get();
        String formattedUsername = Config.WEBHOOK_USERNAME_FORMAT.get()
                .replace("{prefix}", prefix)
                .replace("{username}", username);

        String formattedMessage = Config.MINECRAFT_TO_DISCORD_FORMAT.get()
                .replace("{message}", message);

        String avatarUrl = Config.WEBHOOK_AVATAR_URL.get();
        // Note: In Hytale, we'd need to get the player UUID differently
        // For now, just use the username
        if (!avatarUrl.isEmpty()) {
            avatarUrl = avatarUrl.replace("{username}", username);
        }

        WebhookMessage webhookMessage = new WebhookMessage(
                webhookUrl,
                formattedMessage,
                formattedUsername,
                avatarUrl.isEmpty() ? null : avatarUrl);

        if (!messageQueue.offer(webhookMessage)) {
            Viscord.LOGGER.warn("Message queue is full! Dropping message.");
        }
    }

    public void sendSystemMessage(String message) {
        if (!running || message == null || message.isEmpty()) {
            return;
        }

        if (message.startsWith("💀")) {
            sendEventEmbed(embed -> {
                embed.addProperty("title", "Player Died");
                embed.addProperty("description", message);
                embed.addProperty("color", 0xF04747);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "Viscord · Death");
                embed.add("footer", footer);
            });
        } else {
            sendMinecraftMessage("Server", message);
        }
    }

    // ========= Event Embeds =========

    private String getEventWebhookUrl() {
        String eventWebhookUrl = Config.EVENT_WEBHOOK_URL.get();
        if (eventWebhookUrl != null && !eventWebhookUrl.isEmpty()) {
            return eventWebhookUrl;
        }
        return Config.DISCORD_WEBHOOK_URL.get();
    }

    public void sendStartupEmbed(String serverName) {
        sendEventEmbed(EmbedFactory.createServerStatusEmbed(
                "Server Online",
                "The server is now online.",
                0x43B581,
                serverName,
                "Viscord · Startup"));
    }

    public void sendShutdownEmbed(String serverName) {
        sendEventEmbed(EmbedFactory.createServerStatusEmbed(
                "Server Shutting Down",
                "The server is shutting down...",
                0xF04747,
                serverName,
                "Viscord · Shutdown"));
    }

    private String getPlayerAvatarUrl(String username) {
        String avatarUrl = Config.WEBHOOK_AVATAR_URL.get();
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return null;
        }
        return avatarUrl.replace("{username}", username);
    }

    public void sendJoinEmbed(String username) {
        if (!Config.SEND_JOIN_MESSAGES.get()) {
            return;
        }
        String serverName = Config.SERVER_NAME.get();
        String thumbnailUrl = getPlayerAvatarUrl(username);
        sendEventEmbed(EmbedFactory.createPlayerEventEmbed(
                "Player Joined",
                "A player joined the server.",
                0x5865F2,
                username,
                serverName == null ? "Unknown" : serverName,
                "Viscord · Join",
                thumbnailUrl));
    }

    public void sendLeaveEmbed(String username) {
        if (!Config.SEND_LEAVE_MESSAGES.get()) {
            return;
        }
        String serverName = Config.SERVER_NAME.get();
        String thumbnailUrl = getPlayerAvatarUrl(username);
        sendEventEmbed(EmbedFactory.createPlayerEventEmbed(
                "Player Left",
                "A player left the server.",
                0x99AAB5,
                username,
                serverName == null ? "Unknown" : serverName,
                "Viscord · Leave",
                thumbnailUrl));
    }

    public void sendDeathEmbed(String username, String deathMessage) {
        if (!Config.SEND_DEATH_MESSAGES.get()) {
            return;
        }
        String serverName = Config.SERVER_NAME.get();
        String thumbnailUrl = getPlayerAvatarUrl(username);
        sendEventEmbed(EmbedFactory.createPlayerEventEmbed(
                "Player Died",
                deathMessage != null && !deathMessage.isEmpty() ? deathMessage : username + " died",
                0xE74C3C,
                username,
                serverName == null ? "Unknown" : serverName,
                "Viscord · Death",
                thumbnailUrl));
    }

    public void sendAdvancementEmbed(String username, String advancementTitle, String advancementDescription,
            String type) {
        if (!Config.SEND_ADVANCEMENT_MESSAGES.get()) {
            return;
        }
        sendEventEmbed(EmbedFactory.createAdvancementEmbed(
                "🏆",
                0xFAA61A,
                username,
                advancementTitle,
                advancementDescription));
    }

    public void updateBotStatus() {
        if (discordApi == null || !Config.SET_BOT_STATUS.get()) {
            return;
        }

        try {
            // TODO: Get actual player count from Hytale server
            int onlinePlayers = 0;
            int maxPlayers = 20; // Default max, should be from config

            String statusText = Config.BOT_STATUS_FORMAT.get()
                    .replace("{online}", String.valueOf(onlinePlayers))
                    .replace("{max}", String.valueOf(maxPlayers));

            discordApi.updateActivity(ActivityType.PLAYING, statusText);

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Updated bot status: {}", statusText);
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("Error updating bot status", e);
        }
    }

    // ========= Slash Commands =========

    private void registerListCommand() {
        if (discordApi == null) {
            return;
        }

        try {
            SlashCommand.with("list", "Show online players")
                    .createGlobal(discordApi)
                    .join();

            Viscord.LOGGER.info("Registered /list slash command");

            discordApi.addSlashCommandCreateListener(event -> {
                SlashCommandInteraction interaction = event.getSlashCommandInteraction();
                if (interaction.getCommandName().equals("list")) {
                    handleListCommand(interaction);
                }
            });
        } catch (Exception e) {
            Viscord.LOGGER.error("Error registering /list command", e);
        }
    }

    private EmbedBuilder buildPlayerListEmbed() {
        // TODO: Get actual player list from Hytale server
        int onlinePlayers = 0;
        int maxPlayers = 20;
        String serverName = Config.SERVER_NAME.get();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 " + serverName)
                .setColor(Color.GREEN)
                .setFooter("Viscord · Player List");

        if (onlinePlayers == 0) {
            embed.setDescription("No players are currently online.");
        } else {
            // Build player list when we have Hytale API access
            embed.addField("Players " + onlinePlayers + "/" + maxPlayers, "Player list not yet implemented", false);
        }

        return embed;
    }

    private void handleListCommand(SlashCommandInteraction interaction) {
        try {
            EmbedBuilder embed = buildPlayerListEmbed();
            interaction.createImmediateResponder()
                    .addEmbed(embed)
                    .respond();
        } catch (Exception e) {
            Viscord.LOGGER.error("Error handling /list command", e);
            interaction.createImmediateResponder()
                    .setContent("❌ An error occurred while fetching the player list")
                    .respond();
        }
    }

    private void handleTextListCommand(org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            EmbedBuilder embed = buildPlayerListEmbed();
            event.getChannel().sendMessage(embed);
        } catch (Exception e) {
            Viscord.LOGGER.error("Error handling !list command", e);
        }
    }

    private void registerLinkCommands() {
        if (discordApi == null || linkedAccountsManager == null) {
            return;
        }

        try {
            SlashCommand.with("link", "Link your Hytale account to Discord",
                    java.util.Arrays.asList(
                            org.javacord.api.interaction.SlashCommandOption.create(
                                    org.javacord.api.interaction.SlashCommandOptionType.STRING,
                                    "code",
                                    "The 6-digit code from /discord link in-game",
                                    true)))
                    .createGlobal(discordApi).join();

            SlashCommand.with("unlink", "Unlink your Discord account from Hytale")
                    .createGlobal(discordApi).join();

            Viscord.LOGGER.info("Registered /link and /unlink slash commands");

            discordApi.addSlashCommandCreateListener(event -> {
                SlashCommandInteraction interaction = event.getSlashCommandInteraction();

                if (interaction.getCommandName().equals("link")) {
                    String code = interaction.getArgumentStringValueByName("code").orElse("");
                    String discordId = String.valueOf(interaction.getUser().getId());
                    String discordUsername = interaction.getUser().getName();

                    LinkedAccountsManager.LinkResult result = linkedAccountsManager.verifyAndLink(code, discordId,
                            discordUsername);

                    interaction.createImmediateResponder()
                            .setContent((result.success ? "✅ " : "❌ ") + result.message)
                            .respond();
                } else if (interaction.getCommandName().equals("unlink")) {
                    String discordId = String.valueOf(interaction.getUser().getId());
                    boolean success = linkedAccountsManager.unlinkDiscord(discordId);

                    interaction.createImmediateResponder()
                            .setContent(success ? "✅ Your Hytale account has been unlinked."
                                    : "❌ You don't have a linked Hytale account.")
                            .respond();
                }
            });
        } catch (Exception e) {
            Viscord.LOGGER.error("Error registering link commands", e);
        }
    }

    // ========= Webhook Sending =========

    private void sendEventEmbed(java.util.function.Consumer<JsonObject> customize) {
        String webhookUrl = getEventWebhookUrl();
        sendWebhookEmbedToUrl(webhookUrl, customize);
    }

    private void sendWebhookEmbedToUrl(String webhookUrl, java.util.function.Consumer<JsonObject> customize) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK_URL")) {
            return;
        }

        JsonObject payload = new JsonObject();

        String prefix = Config.SERVER_PREFIX.get();
        String serverName = Config.SERVER_NAME.get();
        String baseUsername = serverName == null ? "Server" : serverName;
        String formattedUsername = Config.WEBHOOK_USERNAME_FORMAT.get()
                .replace("{prefix}", prefix)
                .replace("{username}", baseUsername);

        payload.addProperty("username", formattedUsername);

        String avatarUrl = Config.SERVER_AVATAR_URL.get();
        if (!avatarUrl.isEmpty()) {
            payload.addProperty("avatar_url", avatarUrl);
        }

        JsonObject embed = new JsonObject();
        customize.accept(embed);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Viscord.LOGGER.error("Failed to send webhook embed: {}", response.code());
            } else if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Sent webhook embed successfully");
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Error sending webhook embed", e);
        }
    }

    private void startMessageQueueThread() {
        messageQueueThread = new Thread(
                () -> {
                    while (running && !Thread.currentThread().isInterrupted()) {
                        try {
                            WebhookMessage webhookMessage = messageQueue.poll(1, TimeUnit.SECONDS);
                            if (webhookMessage != null) {
                                sendWebhookMessage(webhookMessage);

                                int delay = Config.RATE_LIMIT_DELAY.get();
                                if (delay > 0) {
                                    Thread.sleep(delay);
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            Viscord.LOGGER.error("Error processing message queue", e);
                        }
                    }
                },
                "Discord-Message-Queue");
        messageQueueThread.setDaemon(true);
        messageQueueThread.start();
    }

    private void sendWebhookMessage(WebhookMessage webhookMessage) {
        JsonObject json = new JsonObject();
        json.addProperty("content", webhookMessage.content);
        json.addProperty("username", webhookMessage.username);

        if (webhookMessage.avatarUrl != null) {
            json.addProperty("avatar_url", webhookMessage.avatarUrl);
        }

        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(webhookMessage.webhookUrl)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Viscord.LOGGER.error("Failed to send webhook message: {}", response.code());
            } else if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Sent webhook message: {}", webhookMessage.username);
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Error sending webhook message", e);
        }
    }

    private static class WebhookMessage {
        final String webhookUrl;
        final String content;
        final String username;
        final String avatarUrl;

        WebhookMessage(String webhookUrl, String content, String username, String avatarUrl) {
            this.webhookUrl = webhookUrl;
            this.content = content;
            this.username = username;
            this.avatarUrl = avatarUrl;
        }
    }
}

package network.vonix.viscord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import okhttp3.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandInteraction;
import java.awt.Color;

/**
 * Discord integration using Javacord + Webhooks.
 * - Minecraft → Discord: Webhooks for messages and embeds (fast and efficient)
 * - Discord → Minecraft: Javacord gateway for message reception
 * - Bot Status: Javacord API for real-time player count updates
 * - Slash Commands: Javacord API for /list command
 */
public class DiscordManager {

    private static DiscordManager instance;
    private MinecraftServer server;
    private final OkHttpClient httpClient;
    private final BlockingQueue<WebhookMessage> messageQueue;
    private Thread messageQueueThread;
    private boolean running = false;
    private String ourWebhookId = null; // Extracted from webhook URL for precise filtering
    private DiscordApi discordApi = null; // Javacord API for bot status and commands

    private DiscordManager() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
        this.messageQueue = new LinkedBlockingQueue<>(
            Config.MESSAGE_QUEUE_SIZE.get()
        );
    }

    public static DiscordManager getInstance() {
        if (instance == null) {
            instance = new DiscordManager();
        }
        return instance;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        String token = Config.DISCORD_BOT_TOKEN.get();

        if (
            token == null ||
            token.isEmpty() ||
            token.equals("YOUR_BOT_TOKEN_HERE")
        ) {
            Viscord.LOGGER.error(
                "Discord bot token not configured! Please set it in the config file."
            );
            return;
        }

        Viscord.LOGGER.info("Starting Discord integration (Javacord + Webhooks mode)...");

        // Extract webhook ID from URL for precise filtering
        extractWebhookId();

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
            Viscord.LOGGER.info("Webhook ID: {} (messages from this webhook will be filtered)", ourWebhookId);
        }
    }

    public void shutdown() {
        running = false;

        if (messageQueueThread != null && messageQueueThread.isAlive()) {
            messageQueueThread.interrupt();
            try {
                messageQueueThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (discordApi != null) {
            discordApi.disconnect();
            Viscord.LOGGER.info("Javacord API disconnected");
        }

        // Properly shutdown HTTP client resources
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
            try {
                if (!httpClient.dispatcher().executorService().awaitTermination(2, TimeUnit.SECONDS)) {
                    httpClient.dispatcher().executorService().shutdownNow();
                }
            } catch (InterruptedException e) {
                httpClient.dispatcher().executorService().shutdownNow();
                Thread.currentThread().interrupt();
            }
            Viscord.LOGGER.info("HTTP client shut down");
        }

        Viscord.LOGGER.info("Discord integration shut down");
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Extract webhook ID from config or webhook URL.
     * Priority: 1) Manual config, 2) Auto-extract from URL
     * Webhook URL format: https://discord.com/api/webhooks/{id}/{token}
     */
    private void extractWebhookId() {
        // First, check if manually configured
        String manualId = Config.DISCORD_WEBHOOK_ID.get();
        if (manualId != null && !manualId.isEmpty()) {
            ourWebhookId = manualId;
            Viscord.LOGGER.info("Using manually configured webhook ID: {}", ourWebhookId);
            return;
        }

        // Try to auto-extract from webhook URL
        String webhookUrl = Config.DISCORD_WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK_URL")) {
            Viscord.LOGGER.warn("Webhook URL not configured. Webhook ID filtering disabled.");
            return;
        }

        try {
            // URL format: https://discord.com/api/webhooks/{id}/{token}
            String[] parts = webhookUrl.split("/");
            
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Webhook URL parts: {}", java.util.Arrays.toString(parts));
            }
            
            // Find the 'webhooks' part and get the ID after it
            for (int i = 0; i < parts.length - 1; i++) {
                if ("webhooks".equals(parts[i]) && i + 1 < parts.length) {
                    ourWebhookId = parts[i + 1];
                    Viscord.LOGGER.info("Auto-extracted webhook ID from URL: {}", ourWebhookId);
                    return;
                }
            }
            
            Viscord.LOGGER.warn("Could not extract webhook ID from URL. Please set 'discordWebhookId' manually in config.");
            Viscord.LOGGER.warn("To get webhook ID: Send a message in-game, then in Discord Developer Mode,");
            Viscord.LOGGER.warn("right-click the webhook's username/avatar and select 'Copy ID'");
        } catch (Exception e) {
            Viscord.LOGGER.error("Error extracting webhook ID from URL", e);
        }
    }

    // ========= Discord → Minecraft (Javacord Gateway) =========

    private void initializeJavacord(String botToken) {
        try {
            String channelId = Config.DISCORD_CHANNEL_ID.get();
            if (channelId == null || channelId.equals("YOUR_CHANNEL_ID_HERE")) {
                Viscord.LOGGER.warn("Channel ID not configured, Javacord features limited");
            }

            Viscord.LOGGER.info("Connecting to Discord via Javacord...");
            
            discordApi = new DiscordApiBuilder()
                .setToken(botToken)
                .setAllIntents()
                .login()
                .join();
            
            Viscord.LOGGER.info("Javacord connected successfully! Bot: {}", discordApi.getYourself().getName());
            
            // Register message listener
            if (channelId != null && !channelId.equals("YOUR_CHANNEL_ID_HERE")) {
                long channelIdLong = Long.parseLong(channelId);
                discordApi.addMessageCreateListener(event -> {
                    // Only process messages from the configured channel
                    if (event.getChannel().getId() != channelIdLong) {
                        return;
                    }
                    
                    processJavacordMessage(event);
                });
                Viscord.LOGGER.info("Message listener registered for channel {}", channelId);
            }
            
            // Register /list slash command
            registerListCommand();
            
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

            // Always log received messages to diagnose filtering
            Viscord.LOGGER.info("Received Discord message from '{}': '{}' [isBot={}, isWebhook={}]", 
                authorName, content, isBot, isWebhook);

            // Detailed webhook info logging
            if (isWebhook) {
                long authorId = event.getMessageAuthor().getId();
                Viscord.LOGGER.info("  → Webhook details: author.id={}, configured={}", 
                    authorId, ourWebhookId);
                Viscord.LOGGER.info("  → Config: IGNORE_WEBHOOKS={}, FILTER_BY_PREFIX={}", 
                    Config.IGNORE_WEBHOOKS.get(), Config.FILTER_BY_PREFIX.get());
            }

            if (Config.IGNORE_BOTS.get() && isBot && !isWebhook) {
                Viscord.LOGGER.info("  → FILTERED: Message from bot (ignoreBots=true)");
                return;
            }

            // ALWAYS filter our own webhook to prevent message loops
            if (isWebhook && ourWebhookId != null) {
                String authorId = String.valueOf(event.getMessageAuthor().getId());
                
                if (ourWebhookId.equals(authorId)) {
                    Viscord.LOGGER.info("  → FILTERED: Message from our own webhook (matched author.id: {})", authorId);
                    return;
                }
            }

            // Optional: Filter ALL OTHER webhooks (for single-server setups)
            if (Config.IGNORE_WEBHOOKS.get() && isWebhook) {
                if (Config.FILTER_BY_PREFIX.get()) {
                    // Filter other webhooks by prefix
                    String webhookName = authorName;
                    String ourPrefix = Config.SERVER_PREFIX.get();
                    
                    if (webhookName.contains(ourPrefix)) {
                        Viscord.LOGGER.info("  → FILTERED: Other webhook by prefix match (username contains '{}')", ourPrefix);
                        return;
                    }
                } else {
                    // Ignore all other webhooks
                    Viscord.LOGGER.info("  → FILTERED: All other webhooks ignored (ignoreWebhooks=true, filterByPrefix=false)");
                    return;
                }
            }

            // Check for event embeds from other servers (join/leave/death/advancement)
            if (Config.SHOW_OTHER_SERVER_EVENTS.get() && isWebhook && !event.getMessage().getEmbeds().isEmpty()) {
                String eventMessage = parseEventEmbed(event, authorName);
                if (eventMessage != null) {
                    Viscord.LOGGER.info("  → RELAYING EVENT to Minecraft: {}", eventMessage);
                    if (server != null) {
                        Component component = Component.literal(eventMessage);
                        server.getPlayerList().getPlayers()
                            .forEach(player -> player.sendSystemMessage(component));
                    }
                    return; // Event was processed, don't process as regular message
                }
            }

            if (content.isEmpty()) {
                Viscord.LOGGER.info("  → FILTERED: Empty message content");
                return;
            }

            String formattedMessage = Config.DISCORD_TO_MINECRAFT_FORMAT.get()
                .replace("{username}", authorName)
                .replace("{message}", content);
            
            Viscord.LOGGER.info("  → RELAYING to Minecraft: {}", formattedMessage);

            if (server != null) {
                Component component = Component.literal(formattedMessage);
                server
                    .getPlayerList()
                    .getPlayers()
                    .forEach(player -> player.sendSystemMessage(component));

                if (Config.ENABLE_DEBUG_LOGGING.get()) {
                    Viscord.LOGGER.debug(
                        "Relayed Discord message to Minecraft: {}",
                        formattedMessage
                    );
                }
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("Error processing Discord message", e);
        }
    }

    /**
     * Parse event embeds from other servers and format them for Minecraft chat.
     * Returns null if the embed is not a recognized event type.
     */
    private String parseEventEmbed(org.javacord.api.event.message.MessageCreateEvent event, String serverName) {
        try {
            var embeds = event.getMessage().getEmbeds();
            if (embeds.isEmpty()) {
                return null;
            }

            var embed = embeds.get(0); // Get first embed
            var footer = embed.getFooter();
            if (!footer.isPresent()) {
                return null;
            }

            String footerText = footer.get().getText().orElse("");
            String title = embed.getTitle().orElse("");
            
            // Check if it's a Viscord event based on footer
            if (!footerText.startsWith("Viscord ·")) {
                return null;
            }

            // Extract server prefix from webhook name (e.g., "[Creative]ServerName")
            String serverPrefix = "";
            if (serverName.startsWith("[") && serverName.contains("]")) {
                serverPrefix = serverName.substring(0, serverName.indexOf("]") + 1) + " ";
            }

            // Parse event type from footer
            if (footerText.contains("· Join")) {
                // Player joined - extract from fields
                var fields = embed.getFields();
                String playerName = fields.stream()
                    .filter(f -> f.getName().equals("Player"))
                    .map(f -> f.getValue())
                    .findFirst().orElse("Unknown");
                return String.format("§a[+] %s%s joined the server", serverPrefix, playerName);
                
            } else if (footerText.contains("· Leave")) {
                // Player left
                var fields = embed.getFields();
                String playerName = fields.stream()
                    .filter(f -> f.getName().equals("Player"))
                    .map(f -> f.getValue())
                    .findFirst().orElse("Unknown");
                return String.format("§c[-] %s%s left the server", serverPrefix, playerName);
                
            } else if (footerText.contains("· Death")) {
                // Player death
                String description = embed.getDescription().orElse("");
                if (description.startsWith("💀 ")) {
                    description = description.substring(2); // Remove skull emoji
                }
                return String.format("§4☠ %s%s", serverPrefix, description);
                
            } else if (footerText.contains("· Advancement")) {
                // Player advancement
                var fields = embed.getFields();
                String playerName = fields.stream()
                    .filter(f -> f.getName().equals("Player"))
                    .map(f -> f.getValue())
                    .findFirst().orElse("Unknown");
                String advTitle = fields.stream()
                    .filter(f -> f.getName().equals("Title"))
                    .map(f -> f.getValue())
                    .findFirst().orElse("advancement");
                return String.format("§e⭐ %s%s completed: %s", serverPrefix, playerName, advTitle);
                
            } else if (footerText.contains("· Startup")) {
                return String.format("§a✓ %sserver is now online", serverPrefix);
                
            } else if (footerText.contains("· Shutdown")) {
                return String.format("§c✗ %sserver is shutting down", serverPrefix);
            }

            return null; // Unknown event type
            
        } catch (Exception e) {
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.warn("Error parsing event embed", e);
            }
            return null;
        }
    }

    // ========= Minecraft → Discord (Webhooks) =========

    public void sendMinecraftMessage(String username, String message) {
        if (!running) {
            return;
        }

        String webhookUrl = Config.DISCORD_WEBHOOK_URL.get();
        if (
            webhookUrl == null ||
            webhookUrl.isEmpty() ||
            webhookUrl.contains("YOUR_WEBHOOK_URL")
        ) {
            Viscord.LOGGER.warn("Discord webhook URL not configured!");
            return;
        }

        String prefix = Config.SERVER_PREFIX.get();
        String formattedUsername = Config.WEBHOOK_USERNAME_FORMAT.get()
            .replace("{prefix}", prefix)
            .replace("{username}", username);

        String formattedMessage = Config.MINECRAFT_TO_DISCORD_FORMAT.get()
            .replace("{message}", message);

        String avatarUrl = Config.WEBHOOK_AVATAR_URL.get();
        if (!avatarUrl.isEmpty() && server != null) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(username);
            if (player != null) {
                String uuid = player.getUUID().toString().replace("-", "");
                avatarUrl = avatarUrl
                    .replace("{uuid}", uuid)
                    .replace("{username}", username);
            }
        }

        WebhookMessage webhookMessage = new WebhookMessage(
            webhookUrl,
            formattedMessage,
            formattedUsername,
            avatarUrl.isEmpty() ? null : avatarUrl
        );

        if (!messageQueue.offer(webhookMessage)) {
            Viscord.LOGGER.warn("Message queue is full! Dropping message.");
        }
    }

    public void sendSystemMessage(String message) {
        if (!running || message == null || message.isEmpty()) {
            return;
        }

        // Send as webhook embed for death messages
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

    /**
     * Get the webhook URL for event messages.
     * Returns the event-specific webhook if configured, otherwise the default webhook.
     */
    private String getEventWebhookUrl() {
        String eventWebhookUrl = Config.EVENT_WEBHOOK_URL.get();
        if (eventWebhookUrl != null && !eventWebhookUrl.isEmpty()) {
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Using event-specific webhook URL");
            }
            return eventWebhookUrl;
        }
        
        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Using default webhook URL for events");
        }
        return Config.DISCORD_WEBHOOK_URL.get();
    }

    public void sendStartupEmbed(String serverName) {
        sendWebhookEmbed(embed -> {
            embed.addProperty("title", "Server Online");
            embed.addProperty("description", "The server is now online.");
            embed.addProperty("color", 0x43B581);

            JsonArray fields = new JsonArray();
            JsonObject serverField = new JsonObject();
            serverField.addProperty("name", "Server");
            serverField.addProperty(
                "value",
                serverName == null ? "Unknown" : serverName
            );
            serverField.addProperty("inline", true);
            fields.add(serverField);
            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Viscord · Startup");
            embed.add("footer", footer);
        });
    }

    public void sendShutdownEmbed(String serverName) {
        sendWebhookEmbed(embed -> {
            embed.addProperty("title", "Server Shutting Down");
            embed.addProperty("description", "The server is shutting down...");
            embed.addProperty("color", 0xF04747);

            JsonArray fields = new JsonArray();
            JsonObject serverField = new JsonObject();
            serverField.addProperty("name", "Server");
            serverField.addProperty(
                "value",
                serverName == null ? "Unknown" : serverName
            );
            serverField.addProperty("inline", true);
            fields.add(serverField);
            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Viscord · Shutdown");
            embed.add("footer", footer);
        });
    }

    public void sendJoinEmbed(String username) {
        String serverName = Config.SERVER_NAME.get();
        sendEventEmbed(embed -> {
            embed.addProperty("title", "Player Joined");
            embed.addProperty("description", "A player joined the server.");
            embed.addProperty("color", 0x5865F2);

            JsonArray fields = new JsonArray();

            JsonObject playerField = new JsonObject();
            playerField.addProperty("name", "Player");
            playerField.addProperty("value", username);
            playerField.addProperty("inline", true);
            fields.add(playerField);

            JsonObject serverField = new JsonObject();
            serverField.addProperty("name", "Server");
            serverField.addProperty(
                "value",
                serverName == null ? "Unknown" : serverName
            );
            serverField.addProperty("inline", true);
            fields.add(serverField);

            embed.add("fields", fields);

            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", "https://mc-heads.net/head/" + username);
            embed.add("thumbnail", thumbnail);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Viscord · Join");
            embed.add("footer", footer);
        });
    }

    public void sendLeaveEmbed(String username) {
        String serverName = Config.SERVER_NAME.get();
        sendEventEmbed(embed -> {
            embed.addProperty("title", "Player Left");
            embed.addProperty("description", "A player left the server.");
            embed.addProperty("color", 0x99AAB5);

            JsonArray fields = new JsonArray();

            JsonObject playerField = new JsonObject();
            playerField.addProperty("name", "Player");
            playerField.addProperty("value", username);
            playerField.addProperty("inline", true);
            fields.add(playerField);

            JsonObject serverField = new JsonObject();
            serverField.addProperty("name", "Server");
            serverField.addProperty(
                "value",
                serverName == null ? "Unknown" : serverName
            );
            serverField.addProperty("inline", true);
            fields.add(serverField);

            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Viscord · Leave");
            embed.add("footer", footer);
        });
    }

    public void sendAdvancementEmbed(
        String username,
        String advancementTitle,
        String advancementDescription,
        String type
    ) {
        String emoji;
        int colorInt;
        if ("CHALLENGE".equalsIgnoreCase(type)) {
            emoji = "🏆";
            colorInt = 0xFAA61A;
        } else if ("GOAL".equalsIgnoreCase(type)) {
            emoji = "🎯";
            colorInt = 0x7289DA;
        } else {
            emoji = "✅";
            colorInt = 0x43B581;
        }

        sendEventEmbed(embed -> {
            embed.addProperty("title", emoji + " Advancement Made");
            embed.addProperty(
                "description",
                "A player has completed an advancement."
            );
            embed.addProperty("color", colorInt);

            JsonArray fields = new JsonArray();

            JsonObject playerField = new JsonObject();
            playerField.addProperty("name", "Player");
            playerField.addProperty("value", username);
            playerField.addProperty("inline", true);
            fields.add(playerField);

            JsonObject titleField = new JsonObject();
            titleField.addProperty("name", "Title");
            titleField.addProperty("value", advancementTitle);
            titleField.addProperty("inline", true);
            fields.add(titleField);

            JsonObject descField = new JsonObject();
            descField.addProperty("name", "Description");
            descField.addProperty(
                "value",
                advancementDescription == null || advancementDescription.isBlank()
                    ? "—"
                    : advancementDescription
            );
            descField.addProperty("inline", false);
            fields.add(descField);

            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Viscord · Advancement");
            embed.add("footer", footer);
        });
    }

    public void updateBotStatus() {
        if (discordApi == null || !Config.SET_BOT_STATUS.get()) {
            return;
        }

        try {
            if (server == null) {
                return;
            }

            int onlinePlayers = server.getPlayerList().getPlayerCount();
            int maxPlayers = server.getPlayerList().getMaxPlayers();
            
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
            
            // Add listener for the command
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
    
    private void handleListCommand(SlashCommandInteraction interaction) {
        try {
            if (server == null) {
                interaction.createImmediateResponder()
                    .setContent("❌ Server is not available")
                    .respond();
                return;
            }
            
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            int onlinePlayers = players.size();
            int maxPlayers = server.getPlayerList().getMaxPlayers();
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Online Players")
                .setColor(Color.GREEN)
                .setFooter("Viscord · Player List");
            
            if (onlinePlayers == 0) {
                embed.setDescription("No players are currently online.");
            } else {
                String playerList = players.stream()
                    .map(player -> player.getName().getString())
                    .collect(Collectors.joining("\n"));
                
                embed.addField("Players " + onlinePlayers + "/" + maxPlayers, playerList, false);
            }
            
            interaction.createImmediateResponder()
                .addEmbed(embed)
                .respond();
            
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("/list command executed by {}", interaction.getUser().getName());
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("Error handling /list command", e);
            interaction.createImmediateResponder()
                .setContent("❌ An error occurred while fetching the player list")
                .respond();
        }
    }

    // ========= Webhook Sending =========

    /**
     * Send an event embed using the event-specific webhook URL (or default if not configured).
     */
    private void sendEventEmbed(
        java.util.function.Consumer<JsonObject> customize
    ) {
        String webhookUrl = getEventWebhookUrl();
        sendWebhookEmbedToUrl(webhookUrl, customize);
    }

    /**
     * Send a regular embed using the default webhook URL.
     */
    private void sendWebhookEmbed(
        java.util.function.Consumer<JsonObject> customize
    ) {
        String webhookUrl = Config.DISCORD_WEBHOOK_URL.get();
        sendWebhookEmbedToUrl(webhookUrl, customize);
    }

    /**
     * Core method to send webhook embeds to a specific URL.
     */
    private void sendWebhookEmbedToUrl(
        String webhookUrl,
        java.util.function.Consumer<JsonObject> customize
    ) {
        if (
            webhookUrl == null ||
            webhookUrl.isEmpty() ||
            webhookUrl.contains("YOUR_WEBHOOK_URL")
        ) {
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

        // Use server avatar URL for event messages
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
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Viscord.LOGGER.error(
                    "Failed to send webhook embed: {}",
                    response.code()
                );
                if (response.body() != null && Config.ENABLE_DEBUG_LOGGING.get()) {
                    Viscord.LOGGER.error(
                        "Response: {}",
                        response.body().string()
                    );
                }
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
                        WebhookMessage webhookMessage = messageQueue.poll(
                            1,
                            TimeUnit.SECONDS
                        );
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
                        Viscord.LOGGER.error(
                            "Error processing message queue",
                            e
                        );
                    }
                }
            },
            "Discord-Message-Queue"
        );
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
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(webhookMessage.webhookUrl)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Viscord.LOGGER.error(
                    "Failed to send webhook message: {}",
                    response.code()
                );
                if (response.body() != null && Config.ENABLE_DEBUG_LOGGING.get()) {
                    Viscord.LOGGER.error(
                        "Response: {}",
                        response.body().string()
                    );
                }
            } else if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug(
                    "Sent webhook message: {}",
                    webhookMessage.username
                );
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

        WebhookMessage(
            String webhookUrl,
            String content,
            String username,
            String avatarUrl
        ) {
            this.webhookUrl = webhookUrl;
            this.content = content;
            this.username = username;
            this.avatarUrl = avatarUrl;
        }
    }
}

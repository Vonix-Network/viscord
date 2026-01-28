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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import okhttp3.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandInteraction;

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
    private String eventWebhookId = null; // Extracted from event webhook URL for precise filtering
    private DiscordApi discordApi = null; // Javacord API for bot status and commands
    private LinkedAccountsManager linkedAccountsManager = null; // Account linking system
    private PlayerPreferences playerPreferences = null; // Per-player message filtering preferences
    
    // Embed Processing Components
    private final AdvancementEmbedDetector advancementDetector;
    private final AdvancementDataExtractor advancementExtractor;
    private final EventEmbedDetector eventDetector;
    private final EventDataExtractor eventExtractor;
    private final VanillaComponentBuilder componentBuilder;
    private final ServerPrefixConfig serverPrefixConfig;

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
        
        // Initialize Detectors, Extractors, and Builders
        this.advancementDetector = new AdvancementEmbedDetector();
        this.advancementExtractor = new AdvancementDataExtractor();
        this.eventDetector = new EventEmbedDetector();
        this.eventExtractor = new EventDataExtractor();
        this.componentBuilder = new VanillaComponentBuilder();
        this.serverPrefixConfig = new ServerPrefixConfig();
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

        if (!ConfigValidator.requireConfigured(token, "YOUR_BOT_TOKEN_HERE", "Discord bot token")) {
            return;
        }

        Viscord.LOGGER.info("Starting Discord integration (Javacord + Webhooks mode)...");

        // Extract webhook ID from URL for precise filtering
        extractWebhookId();
        
        // Initialize Server Prefix Config
        String myPrefix = Config.SERVER_PREFIX.get();
        if (myPrefix != null && !myPrefix.isEmpty()) {
            serverPrefixConfig.setFallbackPrefix(myPrefix);
        }

        // Initialize player preferences
        try {
            Path configDir = server.getServerDirectory().toPath().resolve("config");
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
                messageQueueThread.join(2000); // Wait up to 2 seconds for thread to finish
                if (messageQueueThread.isAlive()) {
                    Viscord.LOGGER.warn("Message queue thread did not stop gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Properly disconnect Javacord with timeout to prevent stuck threads
        if (discordApi != null) {
            try {
                Viscord.LOGGER.info("Disconnecting Javacord...");
                // Use CompletableFuture with timeout to force shutdown if needed
                discordApi.disconnect().get(5, TimeUnit.SECONDS);
                Viscord.LOGGER.info("Javacord disconnected successfully");
            } catch (Exception e) {
                Viscord.LOGGER.warn("Javacord disconnect timeout or error: {}", e.getMessage());
                // Force cleanup even if disconnect times out
                try {
                    discordApi.setAutomaticMessageCacheCleanupEnabled(false);
                } catch (Exception ignored) {
                }
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
                    // Wait a bit more for forced shutdown
                    if (!httpClient.dispatcher().executorService().awaitTermination(2, TimeUnit.SECONDS)) {
                        Viscord.LOGGER.error("HTTP client executor still running after forced shutdown");
                    }
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

    public String generateLinkCode(net.minecraft.server.level.ServerPlayer player) {
        if (linkedAccountsManager == null || !Config.ENABLE_ACCOUNT_LINKING.get()) {
            return null;
        }
        return linkedAccountsManager.generateLinkCode(player.getUUID(), player.getName().getString());
    }

    public boolean unlinkAccount(java.util.UUID uuid) {
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
        
        // Update fallback prefix
        String myPrefix = Config.SERVER_PREFIX.get();
        if (myPrefix != null && !myPrefix.isEmpty()) {
            serverPrefixConfig.setFallbackPrefix(myPrefix);
        }

        // Update bot status with new settings
        if (discordApi != null && running) {
            updateBotStatus();
            Viscord.LOGGER.info("Bot status updated");
        }

        // Reinitialize account linking if settings changed
        if (Config.ENABLE_ACCOUNT_LINKING.get() && linkedAccountsManager == null) {
            try {
                Path configDir = server.getServerDirectory().toPath().resolve("config");
                linkedAccountsManager = new LinkedAccountsManager(configDir);
                Viscord.LOGGER.info("Account linking enabled and initialized");
            } catch (Exception e) {
                Viscord.LOGGER.error("Failed to initialize account linking", e);
            }
        } else if (!Config.ENABLE_ACCOUNT_LINKING.get() && linkedAccountsManager != null) {
            linkedAccountsManager = null;
            Viscord.LOGGER.info("Account linking disabled");
        }

        Viscord.LOGGER
                .info("Config reload complete! Note: Bot token, channel IDs, and some features require a full restart");
    }

    // ========= Player Preferences =========

    public boolean hasServerMessagesFiltered(UUID playerUuid) {
        if (playerPreferences == null) {
            return false; // Default: show all messages
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
            return false; // Default: show all events
        }
        return playerPreferences.hasEventsFiltered(playerUuid);
    }

    public void setEventsFiltered(UUID playerUuid, boolean filtered) {
        if (playerPreferences != null) {
            playerPreferences.setEventsFiltered(playerUuid, filtered);
        }
    }

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
            // URL format: https://discord.com/api/webhooks/{id}/{token}
            String[] parts = webhookUrl.split("/");

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Webhook URL parts: {}", java.util.Arrays.toString(parts));
            }

            // Find the 'webhooks' part and get the ID after it
            for (int i = 0; i < parts.length - 1; i++) {
                if ("webhooks".equals(parts[i]) && i + 1 < parts.length) {
                    return parts[i + 1];
                }
            }

            Viscord.LOGGER.warn("Could not extract webhook ID from URL. Please configure manually in config.");
        } catch (Exception e) {
            Viscord.LOGGER.error("Error extracting webhook ID from URL", e);
        }
        return null;
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
                    // Only request intents we actually need to minimize memory usage
                    .setIntents(Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)
                    .login()
                    .join();

            Viscord.LOGGER.info("Javacord connected successfully! Bot: {}", discordApi.getYourself().getName());

            // Register message listener for main channel and event channel
            String eventChannelId = Config.EVENT_CHANNEL_ID.get();
            if (channelId != null && !channelId.equals("YOUR_CHANNEL_ID_HERE")) {
                long channelIdLong = Long.parseLong(channelId);
                Long eventChannelIdLong = null;

                // Parse event channel ID if configured
                if (eventChannelId != null && !eventChannelId.isEmpty()) {
                    try {
                        eventChannelIdLong = Long.parseLong(eventChannelId);
                        Viscord.LOGGER.info("Event channel configured: {}", eventChannelId);
                    } catch (NumberFormatException e) {
                        Viscord.LOGGER.warn("Invalid event channel ID: {}", eventChannelId);
                    }
                }

                final Long finalEventChannelId = eventChannelIdLong;
                discordApi.addMessageCreateListener(event -> {
                    long msgChannelId = event.getChannel().getId();

                    // Process messages from main channel OR event channel
                    if (msgChannelId == channelIdLong
                            || (finalEventChannelId != null && msgChannelId == finalEventChannelId)) {
                        processJavacordMessage(event);
                    }
                });

                if (eventChannelIdLong != null) {
                    Viscord.LOGGER.info("Message listener registered for chat channel {} and event channel {}",
                            channelId, eventChannelId);
                } else {
                    Viscord.LOGGER.info("Message listener registered for channel {}", channelId);
                }
            }

            // Initialize account linking manager
            if (Config.ENABLE_ACCOUNT_LINKING.get()) {
                try {
                    Path configDir = server.getServerDirectory().toPath().resolve("config");
                    linkedAccountsManager = new LinkedAccountsManager(configDir);
                    Viscord.LOGGER.info("Account linking system initialized ({} accounts linked)",
                            linkedAccountsManager.getLinkedCount());
                } catch (Exception e) {
                    Viscord.LOGGER.error("Failed to initialize account linking", e);
                }
            }

            // Register /list slash command
            registerListCommand();

            // Register account linking slash commands
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
            long authorId = event.getMessageAuthor().getId();

            // Handle !list command (allows all servers to respond in shared channel)
            if (content.trim().equalsIgnoreCase("!list")) {
                handleTextListCommand(event);
                return;
            }

            // Debug logging
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.info("Received Discord message from '{}': '{}' [isBot={}, isWebhook={}]",
                        authorName, content, isBot, isWebhook);
            }

            // 1. Filter our own webhooks
            if (isWebhook) {
                String authorIdStr = String.valueOf(authorId);
                if ((ourWebhookId != null && ourWebhookId.equals(authorIdStr)) || 
                    (eventWebhookId != null && eventWebhookId.equals(authorIdStr))) {
                    if (Config.ENABLE_DEBUG_LOGGING.get()) {
                        Viscord.LOGGER.debug("  → FILTERED: Message from our own webhook");
                    }
                    return;
                }
            }
            
            // 2. Filter bots if configured (but allow webhooks unless ignored separately)
            if (Config.IGNORE_BOTS.get() && isBot && !isWebhook) {
                if (Config.ENABLE_DEBUG_LOGGING.get()) {
                    Viscord.LOGGER.debug("  → FILTERED: Message from bot (ignoreBots=true)");
                }
                return;
            }

            // 3. Process Embeds (Events/Advancements)
            if (!event.getMessage().getEmbeds().isEmpty()) {
                if (Config.ENABLE_DEBUG_LOGGING.get()) {
                    Viscord.LOGGER.debug("  → Processing embed(s) from message");
                }
                
                Embed embed = event.getMessage().getEmbeds().get(0);
                
                // Try processing as advancement
                if (advancementDetector.isAdvancementEmbed(embed)) {
                    processAdvancementEmbed(embed, event);
                    return;
                }
                
                // Try processing as event (join/leave/death)
                if (eventDetector.isEventEmbed(embed)) {
                    processEventEmbed(embed, event);
                    return;
                }
            }

            // 4. Filter other webhooks (regular messages)
            if (Config.IGNORE_WEBHOOKS.get() && isWebhook) {
                if (Config.FILTER_BY_PREFIX.get()) {
                    String ourPrefix = Config.SERVER_PREFIX.get();
                    if (authorName.contains(ourPrefix)) {
                        if (Config.ENABLE_DEBUG_LOGGING.get()) {
                            Viscord.LOGGER.debug("  → FILTERED: Other webhook by prefix match");
                        }
                        return;
                    }
                } else {
                    if (Config.ENABLE_DEBUG_LOGGING.get()) {
                        Viscord.LOGGER.debug("  → FILTERED: All other webhooks ignored");
                    }
                    return;
                }
            }

            // 5. Process Regular Chat Message
            if (content.isEmpty()) {
                return;
            }

            String formattedMessage = Config.DISCORD_TO_MINECRAFT_FORMAT.get()
                    .replace("{username}", authorName)
                    .replace("{message}", content);

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.info("Discord → Minecraft: {} said '{}' → {}", authorName, content, formattedMessage);
            }

            if (server != null) {
                Component component = toMinecraftComponentWithLinks(formattedMessage);
                
                boolean isFilterableMessage = isBot || isWebhook;

                server.getPlayerList().getPlayers().forEach(player -> {
                    if (isFilterableMessage && hasServerMessagesFiltered(player.getUUID())) {
                        return;
                    }
                    player.sendSystemMessage(component);
                });
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("Error processing Discord message", e);
        }
    }
    
    private void processAdvancementEmbed(Embed embed, org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            if (!Config.SHOW_OTHER_SERVER_EVENTS.get()) {
                return;
            }
            
            AdvancementData data = advancementExtractor.extractFromEmbed(embed);
            
            // Extract server prefix from webhook name
            String serverPrefix = extractServerPrefixFromAuthor(event.getMessageAuthor().getDisplayName());
            
            Component component = componentBuilder.buildAdvancementMessage(data, serverPrefix);
            
            broadcastEventComponent(component);
            
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.info("  → Relayed Advancement: {} - {}", data.getPlayerName(), data.getAdvancementTitle());
            }
            
        } catch (ExtractionException e) {
            Viscord.LOGGER.warn("Failed to extract advancement data: {}", e.getMessage());
            handleAdvancementFallback(embed, event);
        } catch (Exception e) {
            Viscord.LOGGER.error("Error processing advancement embed", e);
        }
    }
    
    private void handleAdvancementFallback(Embed embed, org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            String serverPrefix = extractServerPrefixFromAuthor(event.getMessageAuthor().getDisplayName());
            String playerName = extractPlayerNameFallback(embed);
            String title = extractAdvancementTitleFallback(embed);
            
            Component fallback = componentBuilder.createFallbackComponent(playerName, title, serverPrefix);
            broadcastEventComponent(fallback);
            
        } catch (Exception e) {
            Viscord.LOGGER.error("Error handling advancement fallback", e);
        }
    }
    
    private void processEventEmbed(Embed embed, org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            if (!Config.SHOW_OTHER_SERVER_EVENTS.get()) {
                return;
            }
            
            EventData data = eventExtractor.extractFromEmbed(embed);
            
            // Extract server prefix from webhook name
            String serverPrefix = extractServerPrefixFromAuthorForEvents(event.getMessageAuthor().getDisplayName());
            
            Component component = componentBuilder.buildEventMessage(data, serverPrefix);
            
            broadcastEventComponent(component);
            
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.info("  → Relayed Event: {} {}", data.getPlayerName(), data.getActionString());
            }
            
        } catch (ExtractionException e) {
            Viscord.LOGGER.warn("Failed to extract event data: {}", e.getMessage());
            handleEventFallback(embed, event);
        } catch (Exception e) {
            Viscord.LOGGER.error("Error processing event embed", e);
        }
    }
    
    private void handleEventFallback(Embed embed, org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            String serverPrefix = extractServerPrefixFromAuthorForEvents(event.getMessageAuthor().getDisplayName());
            String playerName = extractPlayerNameFallback(embed);
            
            Component fallback = componentBuilder.createEventFallbackComponent(playerName, "performed an action", serverPrefix);
            broadcastEventComponent(fallback);
            
        } catch (Exception e) {
            Viscord.LOGGER.error("Error handling event fallback", e);
        }
    }
    
    private void broadcastEventComponent(Component component) {
        if (server == null) return;
        
        server.getPlayerList().getPlayers().forEach(player -> {
            if (hasEventsFiltered(player.getUUID())) {
                return;
            }
            player.sendSystemMessage(component);
        });
    }
    
    private String extractServerPrefixFromAuthor(String authorName) {
        // Expected format: "[Prefix] Username" or just "Username"
        // Or webhook override name
        return stripBracketsFromPrefix(authorName);
    }
    
    private String extractServerPrefixFromAuthorForEvents(String authorName) {
        // Similar logic, might be refined if event webhooks have different naming
        return stripBracketsFromPrefix(authorName);
    }
    
    private String stripBracketsFromPrefix(String name) {
        if (name == null) return "";
        
        if (name.startsWith("[") && name.contains("]")) {
             return name.substring(1, name.indexOf("]"));
        }
        return "";
    }
    
    // Helpers for fallback extraction
    private String extractPlayerNameFallback(Embed embed) {
        // Try to find a field named "Player" or similar
        return embed.getFields().stream()
                .filter(f -> f.getName().equalsIgnoreCase("Player") || f.getName().equalsIgnoreCase("User"))
                .map(f -> f.getValue())
                .findFirst()
                .orElse("Someone");
    }
    
    private String extractAdvancementTitleFallback(Embed embed) {
         return embed.getFields().stream()
                .filter(f -> f.getName().equalsIgnoreCase("Title") || f.getName().equalsIgnoreCase("Advancement"))
                .map(f -> f.getValue())
                .findFirst()
                .orElse("Advancement");
    }

    // ========= Minecraft → Discord (Webhooks) =========

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
                avatarUrl.isEmpty() ? null : avatarUrl);

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

        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(username);
            if (player != null) {
                String uuid = player.getUUID().toString().replace("-", "");
                return avatarUrl
                        .replace("{uuid}", uuid)
                        .replace("{username}", username);
            }
        }

        return avatarUrl.replace("{username}", username);
    }

    public void sendJoinEmbed(String username) {
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

    public void sendAdvancementEmbed(
            String username,
            String advancementTitle,
            String advancementDescription,
            String type) {
        String emoji = "🏆";
        int colorInt = 0xFAA61A;

        sendEventEmbed(EmbedFactory.createAdvancementEmbed(
                emoji,
                colorInt,
                username,
                advancementTitle,
                advancementDescription));
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
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        int onlinePlayers = players.size();
        int maxPlayers = server.getPlayerList().getMaxPlayers();

        String serverName = Config.SERVER_NAME.get();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 " + serverName)
                .setColor(Color.GREEN)
                .setFooter("Viscord · Player List");

        if (onlinePlayers == 0) {
            embed.setDescription("No players are currently online.");
        } else {
            StringBuilder playerListBuilder = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                if (i > 0)
                    playerListBuilder.append("\n");
                playerListBuilder.append("• ").append(players.get(i).getName().getString());
            }

            embed.addField("Players " + onlinePlayers + "/" + maxPlayers, playerListBuilder.toString(), false);
        }

        return embed;
    }

    private void handleListCommand(SlashCommandInteraction interaction) {
        try {
            if (server == null) {
                interaction.createImmediateResponder()
                        .setContent("❌ Server is not available")
                        .respond();
                return;
            }

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
            if (server == null) {
                return;
            }
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
            SlashCommand.with("link", "Link your Minecraft account to Discord",
                    java.util.Arrays.asList(
                            org.javacord.api.interaction.SlashCommandOption.create(
                                    org.javacord.api.interaction.SlashCommandOptionType.STRING,
                                    "code",
                                    "The 6-digit code from /discord link in-game",
                                    true)))
                    .createGlobal(discordApi).join();

            SlashCommand.with("unlink", "Unlink your Discord account from Minecraft")
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

                    if (result.success) {
                        interaction.createImmediateResponder()
                                .setContent("✅ " + result.message)
                                .respond();
                    } else {
                        interaction.createImmediateResponder()
                                .setContent("❌ " + result.message)
                                .respond();
                    }
                } else if (interaction.getCommandName().equals("unlink")) {
                    String discordId = String.valueOf(interaction.getUser().getId());
                    boolean success = linkedAccountsManager.unlinkDiscord(discordId);

                    if (success) {
                        interaction.createImmediateResponder()
                                .setContent("✅ Your Minecraft account has been unlinked.")
                                .respond();
                    } else {
                        interaction.createImmediateResponder()
                                .setContent("❌ You don't have a linked Minecraft account.")
                                .respond();
                    }
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

    private void sendWebhookEmbed(java.util.function.Consumer<JsonObject> customize) {
        String webhookUrl = Config.DISCORD_WEBHOOK_URL.get();
        sendWebhookEmbedToUrl(webhookUrl, customize);
    }

    private void sendWebhookEmbedToUrl(
            String webhookUrl,
            java.util.function.Consumer<JsonObject> customize) {
        if (webhookUrl == null ||
                webhookUrl.isEmpty() ||
                webhookUrl.contains("YOUR_WEBHOOK_URL")) {
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
                Viscord.LOGGER.error(
                        "Failed to send webhook embed: {}",
                        response.code());
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Error sending webhook embed", e);
        }
    }

    private Component toMinecraftComponentWithLinks(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        var matcher = DISCORD_MARKDOWN_LINK.matcher(text);
        MutableComponent result = Component.empty();
        int lastEnd = 0;
        boolean hasLink = false;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            if (start > lastEnd) {
                String before = text.substring(lastEnd, start);
                if (!before.isEmpty()) {
                    result.append(Component.literal(before));
                }
            }

            String label = matcher.group(1);
            String url = matcher.group(2);

            Component linkComponent = Component
                    .literal(label)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withUnderlined(true)
                            .withColor(ChatFormatting.AQUA));

            result.append(linkComponent);
            lastEnd = end;
            hasLink = true;
        }

        if (lastEnd < text.length()) {
            String tail = text.substring(lastEnd);
            if (!tail.isEmpty()) {
                result.append(Component.literal(tail));
            }
        }

        if (!hasLink) {
            return Component.literal(text);
        }

        return result;
    }

    private void startMessageQueueThread() {
        messageQueueThread = new Thread(
                () -> {
                    while (running && !Thread.currentThread().isInterrupted()) {
                        try {
                            WebhookMessage webhookMessage = messageQueue.poll(
                                    1,
                                    TimeUnit.SECONDS);
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
                                    e);
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
                Viscord.LOGGER.error(
                        "Failed to send webhook message: {}",
                        response.code());
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
                String avatarUrl) {
            this.webhookUrl = webhookUrl;
            this.content = content;
            this.username = username;
            this.avatarUrl = avatarUrl;
        }
    }
}

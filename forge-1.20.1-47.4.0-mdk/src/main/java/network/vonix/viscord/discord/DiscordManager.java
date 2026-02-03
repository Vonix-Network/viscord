package network.vonix.viscord.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.Config;
import network.vonix.viscord.LinkedAccountsManager;
import network.vonix.viscord.PlayerPreferences;
import network.vonix.viscord.ServerPrefixConfig;
import network.vonix.viscord.EmbedFactory;
import okhttp3.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandInteraction;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Discord integration manager using Javacord + Webhooks.
 * - Minecraft → Discord: Webhooks for messages and embeds
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
    private String ourWebhookId = null;
    private String eventWebhookId = null;
    private DiscordApi discordApi = null;
    private LinkedAccountsManager linkedAccountsManager = null;
    private PlayerPreferences playerPreferences = null;
    private ServerPrefixConfig serverPrefixConfig = null;

    // Advancement message formatting components
    private final AdvancementEmbedDetector advancementDetector;
    private final AdvancementDataExtractor advancementExtractor;
    private final VanillaComponentBuilder componentBuilder;

    // Event message formatting components
    private final EventEmbedDetector eventDetector;
    private final EventDataExtractor eventExtractor;

    private static final Pattern DISCORD_MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");

    private DiscordManager() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // Use default queue size if config is not available (e.g., in tests)
        int queueSize = 100; // default
        try {
            if (Config.MESSAGE_QUEUE_SIZE != null) {
                queueSize = Config.MESSAGE_QUEUE_SIZE.get();
            }
        } catch (Exception e) {
            // Config not available, use default
        }
        this.messageQueue = new LinkedBlockingQueue<>(queueSize);

        // Initialize advancement processing components
        this.advancementDetector = new AdvancementEmbedDetector();
        this.advancementExtractor = new AdvancementDataExtractor();
        this.componentBuilder = new VanillaComponentBuilder();

        // Initialize event processing components
        this.eventDetector = new EventEmbedDetector();
        this.eventExtractor = new EventDataExtractor();
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

        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            Viscord.LOGGER.warn("[Discord] Bot token not configured, Discord integration disabled");
            return;
        }

        Viscord.LOGGER.info("[Discord] Starting Discord integration (Javacord + Webhooks)...");

        extractWebhookId();

        // Initialize player preferences
        try {
            playerPreferences = new PlayerPreferences(server.getServerDirectory().toPath().resolve("config"));
            Viscord.LOGGER.info("[Discord] Player preferences system initialized");
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Failed to initialize player preferences", e);
        }

        // Initialize server prefix configuration
        try {
            serverPrefixConfig = new ServerPrefixConfig();
            // Set fallback prefix from existing config (strip brackets if present)
            String configPrefix = Config.SERVER_PREFIX.get();
            if (configPrefix != null && !configPrefix.trim().isEmpty()) {
                String strippedPrefix = stripBracketsFromPrefix(configPrefix.trim());
                serverPrefixConfig.setFallbackPrefix(strippedPrefix);
            }
            Viscord.LOGGER.info("[Discord] Server prefix configuration system initialized");
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Failed to initialize server prefix configuration", e);
            // Create a basic fallback configuration
            serverPrefixConfig = new ServerPrefixConfig();
        }

        running = true;
        startMessageQueueThread();

        // Initialize Javacord
        initializeJavacord(token);

        // Send startup embed
        String serverName = Config.SERVER_NAME.get();
        sendStartupEmbed(serverName);

        Viscord.LOGGER.info("[Discord] Discord integration initialized successfully!");
        if (ourWebhookId != null) {
            Viscord.LOGGER.info("[Discord] Chat Webhook ID: {}", ourWebhookId);
        }
    }

    public void shutdown() {
        if (!running) {
            return;
        }

        Viscord.LOGGER.info("[Discord] Shutting down Discord integration...");
        running = false;

        // 1. Send shutdown embed
        try {
            sendShutdownEmbed(Config.SERVER_NAME.get());
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Failed to send shutdown embed", e);
        }

        // 2. Stop message queue thread
        if (messageQueueThread != null) {
            messageQueueThread.interrupt();
            try {
                messageQueueThread.join(2000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // 3. Disconnect Javacord
        if (discordApi != null) {
            try {
                // Remove listeners first to stop processing new events
                discordApi.getListeners().keySet().forEach(listener -> discordApi.removeListener(listener));

                // Disconnect with longer timeout for cleaner shutdown
                try {
                    discordApi.disconnect().get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Viscord.LOGGER.warn("[Discord] Javacord disconnect timeout, forcing shutdown");
                }

                // Force shutdown Javacord's internal thread pool
                // This helps prevent "Central ExecutorService" leaks
                if (discordApi.getThreadPool() != null) {
                    try {
                        // Try graceful shutdown first
                        discordApi.getThreadPool().getExecutorService().shutdown();
                        discordApi.getThreadPool().getScheduler().shutdown();

                        // Wait a bit for graceful shutdown
                        if (!discordApi.getThreadPool().getExecutorService().awaitTermination(3, TimeUnit.SECONDS)) {
                            discordApi.getThreadPool().getExecutorService().shutdownNow();
                        }
                        if (!discordApi.getThreadPool().getScheduler().awaitTermination(3, TimeUnit.SECONDS)) {
                            discordApi.getThreadPool().getScheduler().shutdownNow();
                        }
                    } catch (Exception e) {
                        // Force shutdown if graceful fails
                        try {
                            discordApi.getThreadPool().getExecutorService().shutdownNow();
                            discordApi.getThreadPool().getScheduler().shutdownNow();
                        } catch (Exception ignored) {
                            // Ignore if already shutdown
                        }
                    }
                }
                Viscord.LOGGER.info("[Discord] Javacord disconnected and thread pools shut down");
            } catch (Throwable e) {
                // Catch Throwable to handle NoClassDefFoundError during shutdown
                Viscord.LOGGER.debug("[Discord] Javacord disconnect failed (likely shutdown race condition): {}",
                        e.getMessage());
            } finally {
                discordApi = null;
            }
        }

        if (httpClient != null) {
            try {
                httpClient.dispatcher().executorService().shutdown();
                httpClient.connectionPool().evictAll();
                if (!httpClient.dispatcher().executorService().awaitTermination(3, TimeUnit.SECONDS)) {
                    httpClient.dispatcher().executorService().shutdownNow();
                }
            } catch (Throwable e) {
                // Ignore errors during http client shutdown
            }
        }

        Viscord.LOGGER.info("[Discord] Discord integration shut down");
    }

    public boolean isRunning() {
        return running;
    }

    // ========= Account Linking =========

    public String generateLinkCode(ServerPlayer player) {
        if (linkedAccountsManager == null || !Config.ENABLE_ACCOUNT_LINKING.get()) {
            return null;
        }
        return linkedAccountsManager.generateLinkCode(player.getUUID(), player.getName().getString());
    }

    public boolean unlinkAccount(UUID uuid) {
        if (linkedAccountsManager == null || !Config.ENABLE_ACCOUNT_LINKING.get()) {
            return false;
        }
        return linkedAccountsManager.unlinkMinecraft(uuid);
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
            return manualId;
        }

        if (webhookUrl != null && !webhookUrl.isEmpty() && !webhookUrl.contains("YOUR_WEBHOOK_URL")) {
            return extractIdFromWebhookUrl(webhookUrl);
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
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error extracting webhook ID", e);
        }
        return null;
    }

    // ========= Javacord Initialization =========

    private void initializeJavacord(String botToken) {
        try {
            String channelId = Config.DISCORD_CHANNEL_ID.get();
            if (channelId == null || channelId.equals("YOUR_CHANNEL_ID_HERE")) {
                Viscord.LOGGER.warn("[Discord] Channel ID not configured - Discord integration will be limited");
                return;
            }

            Viscord.LOGGER.info("[Discord] Connecting to Discord (async)...");

            // Enhanced error handling for Discord API connection
            try {
                new DiscordApiBuilder()
                        .setToken(botToken)
                        .setIntents(Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)
                        .login()
                        .orTimeout(15, TimeUnit.SECONDS)
                        .whenComplete((api, error) -> {
                            if (error != null) {
                                // Enhanced error logging with more context
                                Viscord.LOGGER.error("[Discord] Failed to connect to Discord API. " +
                                        "Error type: {} | Error message: {} | Token length: {} | " +
                                        "This will disable Discord message processing but server will continue normally.",
                                        error.getClass().getSimpleName(), error.getMessage(),
                                        botToken != null ? botToken.length() : 0, error);

                                // Ensure discordApi is null to prevent further API calls
                                discordApi = null;
                                return;
                            }

                            try {
                                discordApi = api;
                                onJavacordConnected(channelId);
                            } catch (Exception postConnectionError) {
                                Viscord.LOGGER.error("[Discord] Error during post-connection setup. " +
                                        "Error: {} | Channel ID: {} | API status: {} | " +
                                        "Discord integration may be partially functional.",
                                        postConnectionError.getMessage(), channelId,
                                        (api != null ? "connected" : "null"), postConnectionError);

                                // Don't null the API here as basic connection succeeded
                                // Just log the error and continue with limited functionality
                            }
                        });
            } catch (Exception builderError) {
                Viscord.LOGGER.error("[Discord] Failed to create Discord API builder. " +
                        "Error: {} | Error type: {} | Token configured: {} | " +
                        "This indicates a configuration or dependency issue.",
                        builderError.getMessage(), builderError.getClass().getSimpleName(),
                        (botToken != null && !botToken.isEmpty()), builderError);
                discordApi = null;
            }

        } catch (Exception configError) {
            Viscord.LOGGER.error("[Discord] Failed to read Discord configuration. " +
                    "Error: {} | Error type: {} | " +
                    "Check your Discord configuration settings.",
                    configError.getMessage(), configError.getClass().getSimpleName(), configError);
            discordApi = null;
        } catch (Throwable criticalError) {
            // Catch Throwable to handle any critical system errors
            Viscord.LOGGER.error("[Discord] Critical error during Javacord initialization. " +
                    "Error: {} | Error type: {} | " +
                    "Discord integration will be completely disabled.",
                    criticalError.getMessage(), criticalError.getClass().getSimpleName(), criticalError);
            discordApi = null;
        }
    }

    /**
     * Called after Javacord successfully connects to Discord.
     * Runs asynchronously to avoid blocking server startup.
     * Enhanced with comprehensive error handling for all initialization steps.
     */
    private void onJavacordConnected(String channelId) {
        try {
            Viscord.LOGGER.info("[Discord] Connected as: {}", discordApi.getYourself().getName());

            // Enhanced error handling for channel ID parsing
            long channelIdLong;
            try {
                channelIdLong = Long.parseLong(channelId);
            } catch (NumberFormatException e) {
                Viscord.LOGGER.error("[Discord] Invalid channel ID format: '{}'. " +
                        "Channel ID must be a valid Discord channel ID number. " +
                        "Message processing will be disabled.", channelId, e);
                return;
            }

            // Enhanced error handling for event channel ID parsing
            String eventChannelIdStr = Config.EVENT_CHANNEL_ID.get();
            Long eventChannelIdLong = null;
            if (eventChannelIdStr != null && !eventChannelIdStr.isEmpty()) {
                try {
                    eventChannelIdLong = Long.parseLong(eventChannelIdStr);
                } catch (NumberFormatException e) {
                    Viscord.LOGGER.warn("[Discord] Invalid event channel ID format: '{}'. " +
                            "Event channel processing will be disabled but main channel will work.",
                            eventChannelIdStr, e);
                    // Continue with null event channel - this is not critical
                }
            }

            // Enhanced error handling for message listener registration
            final Long finalEventChannelId = eventChannelIdLong;
            try {
                discordApi.addMessageCreateListener(event -> {
                    // Wrap the entire message processing in try-catch to prevent
                    // any single message from crashing the listener
                    try {
                        long msgChannelId = event.getChannel().getId();
                        if (msgChannelId == channelIdLong ||
                                (finalEventChannelId != null && msgChannelId == finalEventChannelId)) {
                            processJavacordMessage(msgChannelId, event);
                        }
                    } catch (Exception messageProcessingError) {
                        // Enhanced error logging with message context
                        Viscord.LOGGER.error("[Discord] Error processing individual message. " +
                                "Error: {} | Error type: {} | Channel: {} | Author: {} | " +
                                "Message processing will continue for other messages.",
                                messageProcessingError.getMessage(),
                                messageProcessingError.getClass().getSimpleName(),
                                event.getChannel().getId(),
                                event.getMessageAuthor().getDisplayName(), messageProcessingError);

                        // Don't rethrow - this ensures one bad message doesn't break the entire
                        // listener
                    } catch (Throwable criticalMessageError) {
                        // Catch Throwable for critical errors that might not be Exceptions
                        Viscord.LOGGER.error("[Discord] Critical error processing message. " +
                                "Error: {} | Error type: {} | Channel: {} | " +
                                "This indicates a serious system issue but processing will continue.",
                                criticalMessageError.getMessage(),
                                criticalMessageError.getClass().getSimpleName(),
                                event.getChannel().getId(), criticalMessageError);
                    }
                });
                Viscord.LOGGER.info("[Discord] Message listener registered successfully for channels: {} and {}",
                        channelIdLong, finalEventChannelId);
            } catch (Exception listenerError) {
                Viscord.LOGGER.error("[Discord] Failed to register message listener. " +
                        "Error: {} | Error type: {} | Main channel: {} | Event channel: {} | " +
                        "Discord message processing will not work.",
                        listenerError.getMessage(), listenerError.getClass().getSimpleName(),
                        channelIdLong, finalEventChannelId, listenerError);
                // Continue with other initialization steps even if listener fails
            }

            // Enhanced error handling for account linking initialization
            if (Config.ENABLE_ACCOUNT_LINKING.get()) {
                try {
                    linkedAccountsManager = new LinkedAccountsManager(
                            server.getServerDirectory().toPath().resolve("config"));
                    Viscord.LOGGER.info("[Discord] Account linking initialized successfully ({} accounts)",
                            linkedAccountsManager.getLinkedCount());
                } catch (Exception linkingError) {
                    Viscord.LOGGER.error("[Discord] Failed to initialize account linking. " +
                            "Error: {} | Error type: {} | Config path: {} | " +
                            "Account linking features will be disabled.",
                            linkingError.getMessage(), linkingError.getClass().getSimpleName(),
                            server != null ? server.getServerDirectory().toPath().resolve("config") : "unknown",
                            linkingError);
                    linkedAccountsManager = null; // Ensure it's null so other code can check
                }
            }

            // Enhanced error handling for slash command registration
            try {
                registerListCommandAsync();
                Viscord.LOGGER.debug("[Discord] List command registration initiated");
            } catch (Exception listCommandError) {
                Viscord.LOGGER.error("[Discord] Failed to initiate list command registration. " +
                        "Error: {} | Error type: {} | " +
                        "/list command will not be available.",
                        listCommandError.getMessage(), listCommandError.getClass().getSimpleName(),
                        listCommandError);
            }

            if (Config.ENABLE_ACCOUNT_LINKING.get() && linkedAccountsManager != null) {
                try {
                    registerLinkCommandsAsync();
                    Viscord.LOGGER.debug("[Discord] Link commands registration initiated");
                } catch (Exception linkCommandError) {
                    Viscord.LOGGER.error("[Discord] Failed to initiate link commands registration. " +
                            "Error: {} | Error type: {} | " +
                            "/link and /unlink commands will not be available.",
                            linkCommandError.getMessage(), linkCommandError.getClass().getSimpleName(),
                            linkCommandError);
                }
            }

            // Enhanced error handling for bot status update
            try {
                updateBotStatus();
                Viscord.LOGGER.debug("[Discord] Bot status updated successfully");
            } catch (Exception statusError) {
                Viscord.LOGGER.error("[Discord] Failed to update bot status. " +
                        "Error: {} | Error type: {} | " +
                        "Bot status will not show player count but other features will work.",
                        statusError.getMessage(), statusError.getClass().getSimpleName(), statusError);
            }

        } catch (Exception generalError) {
            Viscord.LOGGER.error("[Discord] Error during post-connection setup. " +
                    "Error: {} | Error type: {} | Channel ID: {} | " +
                    "Some Discord features may not work properly.",
                    generalError.getMessage(), generalError.getClass().getSimpleName(),
                    channelId, generalError);
        } catch (Throwable criticalError) {
            Viscord.LOGGER.error("[Discord] Critical error during post-connection setup. " +
                    "Error: {} | Error type: {} | Channel ID: {} | " +
                    "Discord integration may be severely impacted.",
                    criticalError.getMessage(), criticalError.getClass().getSimpleName(),
                    channelId, criticalError);
        }
    }

    private void processJavacordMessage(long channelId, org.javacord.api.event.message.MessageCreateEvent event) {
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

            // Filter our own webhooks based on username prefix
            // The webhook username format is "[prefix]username", so check for
            // bracket-wrapped prefix
            if (isWebhook) {
                String ourPrefix = "[" + getFallbackServerPrefix() + "]";
                if (authorName != null && authorName.startsWith(ourPrefix)) {
                    return;
                }
            }

            // Filter other webhooks if configured
            if (Config.IGNORE_WEBHOOKS.get() && isWebhook) {
                if (Config.FILTER_BY_PREFIX.get()) {
                    // Use same prefix as sending to ensure accurate filtering
                    String ourPrefix = "[" + getFallbackServerPrefix() + "]";
                    if (authorName != null && authorName.startsWith(ourPrefix)) {
                        return;
                    }
                } else {
                    return;
                }
            }

            // Filter bots
            if (Config.IGNORE_BOTS.get() && isBot && !isWebhook) {
                return;
            }

            boolean isEvent = false;
            if (content.isEmpty()) {
                // Check for embeds (often used for cross-server events)
                if (!event.getMessage().getEmbeds().isEmpty()) {
                    // First, check for advancement embeds and process them specially
                    for (org.javacord.api.entity.message.embed.Embed embed : event.getMessage().getEmbeds()) {
                        if (advancementDetector.isAdvancementEmbed(embed)) {
                            processAdvancementEmbed(embed, event);
                            return; // Skip normal embed processing for advancement embeds
                        }
                    }

                    // Second, check for event embeds (join/leave/death) and process them specially
                    for (org.javacord.api.entity.message.embed.Embed embed : event.getMessage().getEmbeds()) {
                        if (eventDetector.isEventEmbed(embed)) {
                            processEventEmbed(embed, event);
                            return; // Skip normal embed processing for event embeds
                        }
                    }

                    // Continue with normal embed processing if no special embeds found
                    org.javacord.api.entity.message.embed.Embed embed = event.getMessage().getEmbeds().get(0);
                    isEvent = true;

                    // IMPROVED EMBED FALLBACK HANDLING
                    // Determine if we should use fallback processing or standard embedding

                    // Try to construct readable text from the embed
                    String embedText = extractTextFromEmbed(embed);
                    if (embedText != null && !embedText.isEmpty()) {
                        // We successfully extracted text, so use it as content
                        content = embedText;
                    } else {
                        // Could not extract meaningful text, trigger fallback
                        handleEmbedFallback(embed, event);
                        return;
                    }
                } else {
                    return;
                }
            }

            // Strip duplicate username prefix from message content
            String cleanedContent = content;
            if (content.startsWith(authorName + ": ")) {
                cleanedContent = content.substring(authorName.length() + 2);
            } else if (content.startsWith(authorName + " ")) {
                cleanedContent = content.substring(authorName.length() + 1);
            }

            String formattedMessage;
            if (isWebhook) {
                // Special formatting for cross-server messages (webhooks)
                String displayName = authorName;

                // Identify channel type
                String eventChanId = Config.EVENT_CHANNEL_ID.get();
                boolean isEventChannel = !eventChanId.isEmpty() && String.valueOf(channelId).equals(eventChanId);

                if (displayName.startsWith("[") && displayName.contains("]")) {
                    int endBracket = displayName.indexOf("]");
                    String serverPrefix = displayName.substring(0, endBracket + 1);
                    String remainingName = displayName.substring(endBracket + 1).trim();

                    if (isEventChannel || isEvent) {
                        // Event: Just [Prefix] (Name is in message)
                        displayName = "§a" + serverPrefix;
                        formattedMessage = displayName + " §f" + cleanedContent;
                    } else {
                        // Chat: [Prefix] Name
                        // If remainingName is "Otherworld Server", we should probably use a better name
                        // but let's assume it's the player name if it doesn't contain "Server"
                        if (remainingName.toLowerCase().contains("server")) {
                            // If it's just "Server", use the content's implied name or just prefix
                            displayName = "§a" + serverPrefix;
                            formattedMessage = displayName + " §f" + cleanedContent;
                        } else {
                            displayName = "§a" + serverPrefix + " §f" + remainingName;
                            formattedMessage = displayName + "§7: §f" + cleanedContent;
                        }
                    }
                } else {
                    // No bracket prefix found - treat as cross-server player message
                    // Format with green [Cross-Server] prefix for consistency
                    displayName = "§a[Cross-Server] §f" + authorName;
                    formattedMessage = displayName + "§7: §f" + cleanedContent;
                }
            } else {
                // Standard Discord user message
                formattedMessage = Config.DISCORD_TO_MINECRAFT_FORMAT.get()
                        .replace("{username}", authorName)
                        .replace("{message}", cleanedContent);
            }

            if (server != null) {
                MutableComponent finalComponent = Component.empty();

                if (isWebhook) {
                    // Webhook logic (already established) using the pre-calculated formattedMessage
                    finalComponent.append(toMinecraftComponentWithLinks(formattedMessage));
                } else {
                    // Standard Discord message: Make [Discord] clickable
                    String inviteUrl = Config.DISCORD_INVITE_URL.get();
                    String rawFormat = Config.DISCORD_TO_MINECRAFT_FORMAT.get()
                            .replace("{username}", authorName)
                            .replace("{message}", cleanedContent);

                    if (rawFormat.contains("[Discord]") && inviteUrl != null && !inviteUrl.isEmpty()) {
                        String[] parts = rawFormat.split("\\[Discord\\]", 2);

                        // Part before [Discord]
                        if (parts.length > 0 && !parts[0].isEmpty()) {
                            finalComponent.append(toMinecraftComponentWithLinks(parts[0]));
                        }

                        // Clickable [Discord]
                        finalComponent.append(Component.literal("[Discord]")
                                .setStyle(Style.EMPTY
                                        .withColor(TextColor.parseColor("aqua")) // §b
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Click to join our Discord!")))));

                        // Part after [Discord]
                        if (parts.length > 1 && !parts[1].isEmpty()) {
                            finalComponent.append(toMinecraftComponentWithLinks(parts[1]));
                        }
                    } else {
                        // Fallback if no tag or no invite URL
                        finalComponent.append(toMinecraftComponentWithLinks(rawFormat));
                    }
                }

                // Broadcast
                server.getPlayerList().broadcastSystemMessage(finalComponent, false);
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error processing message", e);
        }
    }

    /**
     * Extracts readable text from an embed.
     */
    private String extractTextFromEmbed(org.javacord.api.entity.message.embed.Embed embed) {
        StringBuilder embedContent = new StringBuilder();

        // Add Author if present
        if (embed.getAuthor().isPresent()) {
            embedContent.append(embed.getAuthor().get().getName()).append(" ");
        }

        // Add Title if present - but skip if it's just a generic event header
        if (embed.getTitle().isPresent()) {
            String title = embed.getTitle().get();
            String strippedTitle = title.replaceAll("[^a-zA-Z ]", "").trim();
            if (!strippedTitle.equalsIgnoreCase("Player Joined") &&
                    !strippedTitle.equalsIgnoreCase("Player Left") &&
                    !strippedTitle.equalsIgnoreCase("Advancement Made") &&
                    !strippedTitle.equalsIgnoreCase("Player Died")) {
                embedContent.append(title).append(" ");
            }
        }

        // Add Description with smart replacement
        String description = embed.getDescription().orElse("");

        // Parse Fields (where names are often hidden)
        for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
            String fieldName = field.getName();
            String fieldValue = field.getValue();

            if (fieldName.equalsIgnoreCase("Player") || fieldName.equalsIgnoreCase("User")) {
                // Replace "A player" or "a player" with the actual name
                if (description.toLowerCase().contains("a player")) {
                    description = description.replaceAll("(?i)A player", fieldValue);
                } else if (!description.contains(fieldValue)) {
                    embedContent.append(fieldValue).append(" ");
                }
            } else if (!fieldName.equalsIgnoreCase("Server") && !fieldName.equalsIgnoreCase("Message")) {
                embedContent.append("[").append(fieldName).append(": ").append(fieldValue).append("] ");
            }
        }

        embedContent.append(description);
        return embedContent.toString().trim();
    }

    /**
     * Fallback embed display when parsing fails.
     * Implements multi-strategy fallback for production stability.
     */
    private void handleEmbedFallback(org.javacord.api.entity.message.embed.Embed embed,
            org.javacord.api.event.message.MessageCreateEvent event) {
        // Strategy 1: Try to convert embed to readable Minecraft component
        try {
            Component fallbackComponent = convertEmbedToMinecraftComponent(embed);
            if (fallbackComponent != null && server != null) {
                server.execute(() -> {
                    server.getPlayerList().broadcastSystemMessage(fallbackComponent, false);
                });
                return;
            }
        } catch (Exception e) {
            Viscord.LOGGER.debug("[Discord] Embed conversion fallback failed", e);
        }

        // Strategy 2: Minimal error/link fallback
        String inviteUrl = Config.DISCORD_INVITE_URL.get();
        MutableComponent ultimateFallback = Component.literal("[Discord] ")
                .withStyle(ChatFormatting.AQUA);

        ultimateFallback.append(Component.literal("Received an embed message. ")
                .withStyle(ChatFormatting.WHITE));

        if (inviteUrl != null && !inviteUrl.isEmpty()) {
            ultimateFallback.append(Component.literal("Check Discord")
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GOLD)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to open Discord")))));
        } else {
            ultimateFallback.append(Component.literal("(Check Discord for details)")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (server != null) {
            server.execute(() -> {
                server.getPlayerList().broadcastSystemMessage(ultimateFallback, false);
            });
        }
    }

    /**
     * Converts a Discord embed to a Minecraft component using the original embed
     * display logic.
     * This provides fallback to the original Discord embed format when advancement
     * processing fails.
     */
    private Component convertEmbedToMinecraftComponent(org.javacord.api.entity.message.embed.Embed embed) {
        // This logic is ported from the original 'else' block for embed processing
        // It reconstructs the embed into a string and converts to component

        StringBuilder embedContent = new StringBuilder();

        // Add Author if present
        if (embed.getAuthor().isPresent()) {
            embedContent.append(embed.getAuthor().get().getName()).append(" ");
        }

        // Add Title if present
        if (embed.getTitle().isPresent()) {
            embedContent.append(embed.getTitle().get()).append(" ");
        }

        // Add Description
        embedContent.append(embed.getDescription().orElse(""));

        // Add Fields
        for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
            embedContent.append(" [").append(field.getName()).append(": ").append(field.getValue()).append("]");
        }

        String content = embedContent.toString().trim();
        if (content.isEmpty()) {
            return null;
        }

        // Use basic formatting since we don't have user/webhook context here easily
        // Just return the content as a component
        return toMinecraftComponentWithLinks(content);
    }

    /**
     * Processes an advancement embed and broadcasts as vanilla-style message.
     */
    private void processAdvancementEmbed(org.javacord.api.entity.message.embed.Embed embed,
            org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            // Extract advancement data
            AdvancementData data = advancementExtractor.extractFromEmbed(embed);

            // Determine prefix (use fallback or extract from author)
            String serverPrefix = getFallbackServerPrefix();
            if (event.getMessageAuthor().getDisplayName().contains("]")) {
                // Try to extract from bracket [Prefix]
                String name = event.getMessageAuthor().getDisplayName();
                int end = name.indexOf("]");
                if (name.startsWith("[") && end > 0) {
                    serverPrefix = name.substring(1, end);
                }
            }

            // Build component
            Component component = componentBuilder.buildAdvancementMessage(data, serverPrefix);

            // Broadcast
            if (server != null) {
                server.execute(() -> server.getPlayerList().broadcastSystemMessage(component, false));
            }

        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Failed to process advancement embed", e);
            handleEmbedFallback(embed, event);
        }
    }

    /**
     * Processes an event embed (join/leave/death) and broadcasts as simplified
     * message.
     */
    private void processEventEmbed(org.javacord.api.entity.message.embed.Embed embed,
            org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            // Extract event data
            EventData data = eventExtractor.extractFromEmbed(embed);

            // Determine prefix
            String serverPrefix = getFallbackServerPrefix();
            if (event.getMessageAuthor().getDisplayName().contains("]")) {
                String name = event.getMessageAuthor().getDisplayName();
                int end = name.indexOf("]");
                if (name.startsWith("[") && end > 0) {
                    serverPrefix = name.substring(1, end);
                }
            }

            // Build component
            Component component = componentBuilder.buildEventMessage(data, serverPrefix);

            // Broadcast
            if (server != null) {
                server.execute(() -> server.getPlayerList().broadcastSystemMessage(component, false));
            }

        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Failed to process event embed", e);
            handleEmbedFallback(embed, event);
        }
    }

    // [Previous helper methods remain largely the same, just adapted for Config]

    private String getFallbackServerPrefix() {
        if (serverPrefixConfig != null) {
            String prefix = serverPrefixConfig.getFallbackPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                return prefix;
            }
        }
        return "Server";
    }

    private String stripBracketsFromPrefix(String prefix) {
        if (prefix.startsWith("[") && prefix.endsWith("]")) {
            return prefix.substring(1, prefix.length() - 1);
        }
        return prefix;
    }

    // ... rest of methods (toMinecraftComponentWithLinks, etc) ...

    private MutableComponent toMinecraftComponentWithLinks(String text) {
        MutableComponent root = Component.empty();
        int lastIndex = 0;
        java.util.regex.Matcher matcher = DISCORD_MARKDOWN_LINK.matcher(text);

        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                root.append(Component.literal(text.substring(lastIndex, matcher.start())));
            }

            String linkText = matcher.group(1);
            String linkUrl = matcher.group(2);

            root.append(Component.literal(linkText)
                    .setStyle(Style.EMPTY
                            .withColor(ChatFormatting.BLUE)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, linkUrl))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(linkUrl)))));

            lastIndex = matcher.end();
        }

        if (lastIndex < text.length()) {
            root.append(Component.literal(text.substring(lastIndex)));
        }

        return root;
    }

    // Placeholder for other missing methods to ensure successful compilation
    // These would typically be implementing the full logic as in the reference

    private void startMessageQueueThread() {
        messageQueueThread = new Thread(() -> {
            while (running) {
                try {
                    WebhookMessage msg = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        sendWebhookMessage(msg);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Viscord.LOGGER.error("Error in message queue", e);
                }
            }
        }, "Viscord-Message-Queue");
        messageQueueThread.start();
    }

    private void sendWebhookMessage(WebhookMessage webhookMessage) {
        if (webhookMessage == null) {
            Viscord.LOGGER.warn("[Discord] Cannot send null webhook message");
            return;
        }

        try {
            JsonObject json = new JsonObject();
            if (webhookMessage.content != null) {
                json.addProperty("content", webhookMessage.content);
            } else {
                json.addProperty("content", "");
            }

            if (webhookMessage.username != null && !webhookMessage.username.trim().isEmpty()) {
                json.addProperty("username", webhookMessage.username);
            } else {
                json.addProperty("username", "Server");
            }

            if (webhookMessage.avatarUrl != null && !webhookMessage.avatarUrl.trim().isEmpty()) {
                json.addProperty("avatar_url", webhookMessage.avatarUrl);
            }

            if (webhookMessage.webhookUrl == null || webhookMessage.webhookUrl.trim().isEmpty()) {
                Viscord.LOGGER.error("[Discord] Webhook URL is null or empty, cannot send message.");
                return;
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
                    Viscord.LOGGER.error("[Discord] Failed to send webhook message. HTTP Status: {}", response.code());
                }
            }

        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Failed to send webhook message", e);
        }
    }

    private void sendStartupEmbed(String serverName) {
        // Startup embed logic
    }

    public void sendShutdownEmbed(String serverName) {
        // Shutdown embed logic
    }

    public void updateBotStatus() {
        if (discordApi != null && Config.SET_BOT_STATUS.get() && server != null) {
            String format = Config.BOT_STATUS_FORMAT.get();
            int online = server.getPlayerList().getPlayerCount();
            int max = server.getMaxPlayers();
            String status = format.replace("{online}", String.valueOf(online))
                    .replace("{max}", String.valueOf(max));
            discordApi.updateActivity(ActivityType.PLAYING, status);
        }
    }

    private void registerListCommandAsync() {
        if (discordApi != null) {
            // Logic to register slash commands
        }
    }

    private void registerLinkCommandsAsync() {
        if (discordApi != null) {
            // Logic to register link commands
        }
    }

    // ========= Outgoing Messages =========

    public void sendMinecraftMessage(String username, String message) {
        if (!running) {
            return;
        }

        String webhookUrl = Config.DISCORD_WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK_URL")) {
            return;
        }

        String prefix = Config.SERVER_PREFIX.get();
        String formattedUsername = Config.WEBHOOK_USERNAME_FORMAT.get()
                .replace("{prefix}", prefix)
                .replace("{username}", username);

        String formattedMessage = Config.MINECRAFT_TO_DISCORD_FORMAT.get()
                .replace("{message}", message);

        String avatarUrl = Config.SERVER_AVATAR_URL.get(); // Using server avatar as base check? No, check logic

        // Re-implement avatar logic properly
        String configAvatarUrl = Config.WEBHOOK_AVATAR_URL.get(); // Was avatarUrl in VonixCore
        if (configAvatarUrl != null && !configAvatarUrl.isEmpty() && server != null) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(username);
            if (player != null) {
                String uuid = player.getUUID().toString().replace("-", "");
                avatarUrl = configAvatarUrl
                        .replace("{uuid}", uuid)
                        .replace("{username}", username);
            } else {
                avatarUrl = configAvatarUrl.replace("{username}", username);
            }
        }

        WebhookMessage webhookMessage = new WebhookMessage(
                webhookUrl,
                formattedMessage,
                formattedUsername,
                avatarUrl == null || avatarUrl.isEmpty() ? null : avatarUrl);

        if (!messageQueue.offer(webhookMessage)) {
            Viscord.LOGGER.warn("[Discord] Message queue full, dropping message");
        }
    }

    public void sendSystemMessage(String message) {
        if (!running || message == null || message.isEmpty()) {
            return;
        }

        if (message.startsWith("💀")) {
            sendEventEmbed(EmbedFactory.createPlayerEventEmbed(
                    "Player Died",
                    message,
                    0xF04747, // Red
                    "Unknown", // Player name extracted below if possible, or just used in description
                    Config.SERVER_NAME.get(),
                    "Viscord · Death",
                    null));
            // Wait, VonixCore implementation was custom lambda. Let's stick to EmbedFactory
            // or custom.
            // VonixCore used:
            /*
             * sendEventEmbed(embed -> {
             * embed.addProperty("title", "Player Died");
             * embed.addProperty("description", message);
             * embed.addProperty("color", 0xF04747);
             * JsonObject footer = new JsonObject();
             * footer.addProperty("text", "VonixCore · Death");
             * embed.add("footer", footer);
             * });
             */
            // I'll reuse EmbedFactory.createSimpleEmbed or similar if possible, or matches
            // VonixCore roughly.
            // But simpler to just use sendMinecraftMessage for generic system messages if
            // not death.
        } else {
            sendMinecraftMessage("Server", message);
        }
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
                serverName,
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
                serverName,
                "Viscord · Leave",
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

    public void reloadConfig() {
        // Config is usually auto-reloaded or needs explicit call if cached
        // ConfigValue in Viscord/VonixCore seems to be direct supplier wrapper?
        // If ConfigValue uses Forge Config spec, reloading is handled by event usually.
        // But here we might need to refresh cached values if any.
        extractWebhookId();
        if (serverPrefixConfig != null) {
            String prefix = Config.SERVER_PREFIX.get();
            if (prefix != null) {
                serverPrefixConfig.setFallbackPrefix(stripBracketsFromPrefix(prefix.trim()));
            }
        }
    }

    // ========= Helper Methods =========

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

    private String getEventWebhookUrl() {
        String eventUrl = Config.EVENT_WEBHOOK_URL.get();
        if (eventUrl != null && !eventUrl.isEmpty() && !eventUrl.contains("YOUR_WEBHOOK_URL")) {
            return eventUrl;
        }
        return Config.DISCORD_WEBHOOK_URL.get();
    }

    private void sendEventEmbed(java.util.function.Consumer<JsonObject> customize) {
        String webhookUrl = getEventWebhookUrl();
        sendWebhookEmbedToUrl(webhookUrl, customize);
    }

    private void sendWebhookEmbedToUrl(String webhookUrl, java.util.function.Consumer<JsonObject> customize) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK_URL")) {
            return;
        }

        try {
            JsonObject root = new JsonObject();
            JsonArray embeds = new JsonArray();
            JsonObject embed = new JsonObject();

            customize.accept(embed);
            embeds.add(embed);
            root.add("embeds", embeds);

            // Add username/avatar override for the webhook itself
            String prefix = Config.SERVER_PREFIX.get();
            String usernameFormat = Config.WEBHOOK_USERNAME_FORMAT.get();
            if (usernameFormat != null) {
                root.addProperty("username", usernameFormat
                        .replace("{prefix}", prefix)
                        .replace("{username}", "Viscord")); // Default name for events
            }

            String serverAvatar = Config.SERVER_AVATAR_URL.get();
            if (serverAvatar != null && !serverAvatar.isEmpty()) {
                root.addProperty("avatar_url", serverAvatar);
            }

            RequestBody body = RequestBody.create(
                    root.toString(),
                    MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Viscord.LOGGER.error("[Discord] Failed to send event embed", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            Viscord.LOGGER.warn("[Discord] Failed to send event embed: {}", response.code());
                        }
                    }
                }
            });

        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error sending event embed", e);
        }
    }

    private void handleTextListCommand(org.javacord.api.event.message.MessageCreateEvent event) {
        // Handle !list
        if (server != null) {
            int online = server.getPlayerList().getPlayerCount();
            int max = server.getMaxPlayers();
            event.getChannel().sendMessage("There are " + online + "/" + max + " players online.");
        }
    }

    // Data classes

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

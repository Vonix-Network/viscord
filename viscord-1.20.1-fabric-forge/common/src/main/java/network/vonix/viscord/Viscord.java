package network.vonix.viscord;

import net.minecraft.server.MinecraftServer;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.discord.DiscordManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Viscord - Discord Multi-Server Integration Mod
 * Ported from VonixCore Discord functionality
 */
public final class Viscord {
    public static final String MOD_ID = "viscord";
    public static final String MOD_NAME = "Viscord";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    // Async executor for non-blocking Discord operations
    public static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "Viscord-Async");
        thread.setDaemon(true);
        return thread;
    });

    private static MinecraftServer server;
    private static boolean discordEnabled = false;

    public static void init() {
        LOGGER.info("[{}] Initializing...", MOD_NAME);

        // Config is loaded automatically by Forge/Fabric config system
        if (ViscordConfig.CONFIG.enabled.get()) {
            LOGGER.info("[{}] Discord integration enabled in config", MOD_NAME);
        } else {
            LOGGER.info("[{}] Discord integration disabled in config", MOD_NAME);
        }
    }

    public static void onServerStarting(MinecraftServer server) {
        Viscord.server = server;
        LOGGER.info("[{}] Server starting...", MOD_NAME);
    }

    public static void onServerStarted(MinecraftServer server) {
        Viscord.server = server;

        // Initialize Discord module (requires server to be fully started)
        if (ViscordConfig.CONFIG.enabled.get()) {
            try {
                // Initialize with timeout protection to prevent hanging server startup
                java.util.concurrent.CompletableFuture<Void> discordInitFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
                    DiscordManager.getInstance().initialize(server);
                }, ASYNC_EXECUTOR);

                // Wait max 10 seconds for Discord initialization
                discordInitFuture.get(10, TimeUnit.SECONDS);
                discordEnabled = true;
                LOGGER.info("[{}] Discord module enabled", MOD_NAME);
            } catch (java.util.concurrent.TimeoutException e) {
                LOGGER.error("[{}] Discord initialization timed out after 10 seconds!", MOD_NAME);
                LOGGER.error("[{}] Discord features will be unavailable.", MOD_NAME);
            } catch (Exception e) {
                LOGGER.error("[{}] Failed to initialize Discord: {}", MOD_NAME, e.getMessage());
            }
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        LOGGER.info("[{}] Server stopping...", MOD_NAME);

        if (discordEnabled) {
            DiscordManager.getInstance().shutdown();
            discordEnabled = false;
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        LOGGER.info("[{}] Server stopped", MOD_NAME);

        // Shutdown async executor
        ASYNC_EXECUTOR.shutdown();
        try {
            if (!ASYNC_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                ASYNC_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            ASYNC_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static boolean isDiscordEnabled() {
        return discordEnabled;
    }

    /**
     * Execute a task asynchronously on the Viscord thread pool.
     * Use this for all Discord-related operations to avoid blocking the main thread.
     */
    public static void executeAsync(Runnable task) {
        ASYNC_EXECUTOR.execute(task);
    }
}

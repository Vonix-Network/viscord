package network.vonix.viscord;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Viscord - Bidirectional Minecraft-Discord Chat Integration
 *
 * A NeoForge mod that enables bidirectional communication between Minecraft and Discord.
 * Features:
 * - Custom webhook username formatting with server prefixes
 * - Multi-server support through a single Discord channel
 * - Loop prevention (ignores own messages and optionally other webhooks)
 * - Player join/leave/death/advancement notifications
 * - Real-time chat relay in both directions
 */
@Mod(Viscord.MODID)
public class Viscord {

    public static final String MODID = "viscord";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Viscord(IEventBus modEventBus, ModContainer modContainer) {
        // Register the common setup method for mod loading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events
        NeoForge.EVENT_BUS.register(this);

        // Register the Minecraft event handler for chat, join, leave, etc.
        NeoForge.EVENT_BUS.register(MinecraftEventHandler.class);

        // Register our mod's config so that FML can create and load the config file
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        LOGGER.info(
            "Viscord initialized - Bidirectional Discord chat mod loaded"
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Viscord common setup complete");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("===========================================");
        LOGGER.info("Viscord - Starting Discord Integration");
        LOGGER.info("===========================================");

        try {
            // Initialize Discord manager with the server instance
            DiscordManager.getInstance().initialize(event.getServer());
            LOGGER.info(
                "Discord integration initialization requested (async). Discord will connect in the background."
            );
            LOGGER.info("Server: {}", Config.SERVER_NAME.get());
            LOGGER.info("Prefix: {}", Config.SERVER_PREFIX.get());
            LOGGER.info("Channel ID: {}", Config.DISCORD_CHANNEL_ID.get());
            
            // Check for updates
            if (Config.ENABLE_UPDATE_CHECKER.get()) {
                UpdateChecker.checkForUpdates().thenAccept(result -> {
                    if (result.updateAvailable) {
                        LOGGER.warn("===============================================");
                        LOGGER.warn("UPDATE AVAILABLE: Viscord {} -> {}", 
                            UpdateChecker.getCurrentVersion(), result.latestVersion);
                        LOGGER.warn("Download: {}", result.downloadUrl);
                        LOGGER.warn("===============================================");
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to start Discord integration", e);
        }

        LOGGER.info("===========================================");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping - shutting down Discord integration");

        try {
            // Send shutdown message to Discord before disconnecting (non-blocking with timeout)
            if (DiscordManager.getInstance().isRunning()) {
                String serverName = Config.SERVER_NAME.get();

                CompletableFuture<Void> shutdownMessage = CompletableFuture.runAsync(() -> {
                    DiscordManager.getInstance().sendShutdownEmbed(serverName);
                });

                // Wait up to 2 seconds for the message to send
                try {
                    shutdownMessage.get(2, TimeUnit.SECONDS);
                } catch (Exception e) {
                    LOGGER.warn("Shutdown message timed out or failed: {}", e.getMessage());
                }
            }

            // Shutdown Discord manager
            DiscordManager.getInstance().shutdown();

            LOGGER.info("Discord integration shut down successfully");
        } catch (Exception e) {
            LOGGER.error("Error during Discord integration shutdown", e);
        }
    }
}

package network.vonix.viscord;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Viscord - Bidirectional Minecraft-Discord Chat Integration
 *
 * A Forge mod that enables bidirectional communication between Minecraft and Discord.
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

    public Viscord(IEventBus modEventBus, ModLoadingContext modLoadingContext) {
        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        // MinecraftEventHandler is automatically registered via @Mod.EventBusSubscriber annotation
        // No need to manually register it here

        // Register our mod's config so that FML can create and load the config file
        modLoadingContext.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "viscord-common.toml");

        LOGGER.info(
            "Viscord initialized - Bidirectional Discord chat mod loaded"
        );
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

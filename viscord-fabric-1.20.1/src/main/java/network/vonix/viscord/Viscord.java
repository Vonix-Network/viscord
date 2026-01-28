package network.vonix.viscord;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Viscord - Bidirectional Minecraft-Discord Chat Integration
 *
 * A Fabric mod that enables bidirectional communication between Minecraft and
 * Discord.
 * Features:
 * - Custom webhook username formatting with server prefixes
 * - Multi-server support through a single Discord channel
 * - Loop prevention (ignores own messages and optionally other webhooks)
 * - Player join/leave/death/advancement notifications
 * - Real-time chat relay in both directions
 */
public class Viscord implements ModInitializer {

	public static final String MOD_ID = "viscord";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Load configuration
		Config.load();

		// Register event handlers
		MinecraftEventHandler.register();

		// Register server lifecycle events
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("===========================================");
			LOGGER.info("Viscord - Starting Discord Integration");
			LOGGER.info("===========================================");

			try {
				// Initialize Discord manager with the server instance
				DiscordManager.getInstance().initialize(server);
				LOGGER.info(
						"Discord integration initialization requested (async). Discord will connect in the background.");
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
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Server stopping - shutting down Discord integration");

			try {
				// Send shutdown message to Discord before disconnecting (non-blocking with
				// timeout)
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
		});

		LOGGER.info(
				"Viscord initialized - Bidirectional Discord chat mod loaded");
	}
}
package network.vonix.viscord.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.discord.DiscordEventHandler;
import network.vonix.viscord.discord.DiscordManager;
import network.vonix.viscord.integration.FluxerIntegration;

/**
 * Fabric entry point for Viscord.
 */
public final class ViscordFabric implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        // Initialize Viscord
        Viscord.init();

        // Initialize Fluxer integration
        FluxerIntegration.initialize();

        // Register event handlers
        DiscordEventHandler.register();

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(Viscord::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(Viscord::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(Viscord::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(Viscord::onServerStopped);

        // Register chat event
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, params) -> {
            if (DiscordManager.getInstance().isRunning()) {
                String content = message.getContent().getString();
                String username = player.getGameProfile().getName();
                String uuid = player.getUUID().toString();

                // Send to Discord
                DiscordManager.getInstance().sendChatMessage(username, content, uuid);
            }
        });
    }
}

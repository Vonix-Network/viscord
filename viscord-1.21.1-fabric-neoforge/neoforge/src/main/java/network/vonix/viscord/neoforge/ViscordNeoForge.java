package network.vonix.viscord.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.discord.DiscordEventHandler;
import network.vonix.viscord.integration.FluxerIntegration;

/**
 * NeoForge entry point for Viscord.
 */
@Mod(Viscord.MOD_ID)
public final class ViscordNeoForge {

    public ViscordNeoForge(IEventBus modBus) {
        // Initialize Viscord
        Viscord.init();

        // Initialize Fluxer integration
        FluxerIntegration.initialize();

        // Register event handlers
        DiscordEventHandler.register();

        // Register server events on the Forge bus
        modBus.addListener(this::onServerStarting);
        modBus.addListener(this::onServerStarted);
        modBus.addListener(this::onServerStopping);
        modBus.addListener(this::onServerStopped);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Viscord.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        Viscord.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        Viscord.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Viscord.onServerStopped(event.getServer());
    }
}

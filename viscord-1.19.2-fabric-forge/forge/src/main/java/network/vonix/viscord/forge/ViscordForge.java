package network.vonix.viscord.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.discord.DiscordEventHandler;
import network.vonix.viscord.integration.FluxerIntegration;

/**
 * Forge entry point for Viscord (1.19.2).
 */
@Mod(Viscord.MOD_ID)
public final class ViscordForge {

    public ViscordForge() {
        // Initialize Viscord
        Viscord.init();

        // Initialize Fluxer integration
        FluxerIntegration.initialize();

        // Register event handlers
        DiscordEventHandler.register();

        // Register server events on the Forge bus
        MinecraftForge.EVENT_BUS.register(this);
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

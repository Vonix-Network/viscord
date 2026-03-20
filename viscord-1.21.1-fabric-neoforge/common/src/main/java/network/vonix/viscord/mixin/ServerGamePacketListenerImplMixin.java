package network.vonix.viscord.mixin;

import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import network.vonix.viscord.discord.DiscordManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle chat messages and send them to Discord.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    private ServerPlayer player;

    /**
     * Inject into the chat handling method to send messages to Discord.
     */
    @Inject(method = "broadcastChatMessage", at = @At("HEAD"))
    private void viscord$onChatMessage(PlayerChatMessage message, CallbackInfo ci) {
        if (player == null || message == null) return;

        String content = message.serverContent().getString();
        String username = player.getGameProfile().getName();
        String uuid = player.getUUID().toString();

        // Send to Discord
        DiscordManager.getInstance().sendChatMessage(username, content, uuid);
    }
}

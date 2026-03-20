package network.vonix.viscord.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.discord.DiscordManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Mixin to handle advancement completions and send them to Discord.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Shadow
    private Map<AdvancementHolder, AdvancementProgress> advancements;

    /**
     * Inject into the award method to send advancement notifications to Discord.
     */
    @Inject(method = "award", at = @At(value = "RETURN"))
    private void viscord$onAward(AdvancementHolder advancement, String criterionKey, CallbackInfo ci) {
        if (player == null || advancement == null) return;

        // Check if this advancement should be announced (has a display)
        if (advancement.value().display().isPresent()) {
            var display = advancement.value().display().get();
            String title = display.getTitle().getString();
            String description = display.getDescription().getString();
            String playerName = player.getName().getString();
            String uuid = player.getUUID().toString();

            // Send to Discord
            DiscordManager.getInstance().sendAdvancementEmbed(playerName, title, description, uuid);
        }
    }
}

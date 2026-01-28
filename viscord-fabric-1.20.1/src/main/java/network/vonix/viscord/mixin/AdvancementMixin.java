package network.vonix.viscord.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.MinecraftEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to capture advancement events since Fabric API doesn't have a direct
 * callback for this.
 */
@Mixin(PlayerAdvancements.class)
public abstract class AdvancementMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void onAdvancementAwarded(Advancement advancement, String criterionKey,
            CallbackInfoReturnable<Boolean> cir) {
        // Only trigger for the first criterion that completes the advancement
        // The inject point is right before rewards are granted, meaning the advancement
        // was just completed
        if (player != null && advancement != null) {
            MinecraftEventHandler.onAdvancementEarned(player, advancement);
        }
    }
}

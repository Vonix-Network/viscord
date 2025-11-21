package network.vonix.viscord;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Viscord.MODID)
public class MinecraftEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("discord")
                .requires(source -> source.hasPermission(0))
                .executes(context -> {
                    String invite = Config.DISCORD_INVITE_URL.get();
                    CommandSourceStack source = context.getSource();

                    if (invite == null || invite.isEmpty()) {
                        source.sendSuccess(
                            () -> Component.literal(
                                "Discord invite URL is not configured. Ask an admin to set 'discordInviteUrl' in viscord-common.toml."
                            ),
                            false
                        );
                    } else {
                        MutableComponent clickable = Component
                            .literal("Click Here to join the Discord!")
                            .withStyle(style ->
                                style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite))
                                    .withUnderlined(true)
                                    .withColor(ChatFormatting.AQUA)
                            );

                        source.sendSuccess(() -> clickable, false);
                    }
                    return 1;
                })
        );
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onServerChat(ServerChatEvent event) {
        if (!DiscordManager.getInstance().isRunning()) {
            return;
        }

        ServerPlayer player = event.getPlayer();
        String username = player.getName().getString();
        String message = event.getRawText();

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Chat message from {}: {}", username, message);
        }

        // Send to Discord via webhook
        DiscordManager.getInstance().sendMinecraftMessage(username, message);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Viscord.LOGGER.info("PlayerJoin event triggered");

        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.warn(
                "DiscordManager is not running, skipping join message"
            );
            return;
        }

        if (!Config.SEND_JOIN_MESSAGES.get()) {
            Viscord.LOGGER.info("Join messages disabled in config");
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        String username = player.getName().getString();

        Viscord.LOGGER.info("Sending join message for player: {}", username);

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Player joined: {}", username);
        }

        DiscordManager.getInstance().sendJoinEmbed(username);

        Viscord.LOGGER.info("Join message sent successfully");

        // Update bot status with new player count (schedule on server thread)
        scheduleStatusUpdate(player.getServer());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        Viscord.LOGGER.info("PlayerLeave event triggered");

        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.warn(
                "DiscordManager is not running, skipping leave message"
            );
            return;
        }

        if (!Config.SEND_LEAVE_MESSAGES.get()) {
            Viscord.LOGGER.info("Leave messages disabled in config");
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        String username = player.getName().getString();

        Viscord.LOGGER.info("Sending leave message for player: {}", username);

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Player left: {}", username);
        }

        DiscordManager.getInstance().sendLeaveEmbed(username);

        Viscord.LOGGER.info("Leave message sent successfully");

        // Update bot status with new player count (schedule on server thread)
        scheduleStatusUpdate(player.getServer());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Viscord.LOGGER.info(
            "PlayerDeath event triggered for: {}",
            player.getName().getString()
        );

        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.warn(
                "DiscordManager is not running, skipping death message"
            );
            return;
        }

        if (!Config.SEND_DEATH_MESSAGES.get()) {
            Viscord.LOGGER.info("Death messages disabled in config");
            return;
        }

        String deathMessage = event
            .getSource()
            .getLocalizedDeathMessage(player)
            .getString();

        Viscord.LOGGER.info("Sending death message: {}", deathMessage);

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Player death: {}", deathMessage);
        }

        DiscordManager.getInstance().sendSystemMessage("💀 " + deathMessage);
        Viscord.LOGGER.info("Death message sent successfully");
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onAdvancement(
        AdvancementEvent.AdvancementEarnEvent event
    ) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        Advancement advancement = event.getAdvancement();

        Viscord.LOGGER.info(
            "Advancement event triggered for: {}",
            player.getName().getString()
        );

        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.warn(
                "DiscordManager is not running, skipping advancement message"
            );
            return;
        }

        if (!Config.SEND_ADVANCEMENT_MESSAGES.get()) {
            Viscord.LOGGER.info("Advancement messages disabled in config");
            return;
        }

        // Only announce advancements that should be announced (not recipes, etc.)
        if (advancement.getDisplay() == null) {
            Viscord.LOGGER.debug("Advancement has no display, skipping");
            return;
        }

        var display = advancement.getDisplay();
        if (!display.shouldAnnounceChat()) {
            Viscord.LOGGER.debug(
                "Advancement should not be announced in chat, skipping"
            );
            return;
        }

        String username = player.getName().getString();
        String advancementTitle = display.getTitle().getString();
        String advancementDescription = display.getDescription().getString();

        Viscord.LOGGER.info(
            "Sending advancement message for: {} - {}",
            username,
            advancementTitle
        );

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug(
                "Player advancement: {} - {}",
                username,
                advancementTitle
            );
        }

        DiscordManager.getInstance().sendAdvancementEmbed(
            username,
            advancementTitle,
            advancementDescription,
            "ADVANCEMENT" // 1.20.1 doesn't expose getType() on DisplayInfo
        );

        Viscord.LOGGER.info("Advancement message sent successfully");
    }

    private static void scheduleStatusUpdate(net.minecraft.server.MinecraftServer server) {
        // Schedule status update on the server thread after a short delay
        if (server != null) {
            server.execute(() -> {
                try {
                    Thread.sleep(100); // Small delay to ensure player list is updated
                    DiscordManager.getInstance().updateBotStatus();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Viscord.LOGGER.warn("Status update interrupted");
                }
            });
        }
    }
}

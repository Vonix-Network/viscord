package network.vonix.viscord;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Viscord.MODID)
public class MinecraftEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /discord command with subcommands
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
                .then(Commands.literal("link")
                    .executes(context -> {
                        if (!Config.ENABLE_ACCOUNT_LINKING.get()) {
                            context.getSource().sendFailure(Component.literal("§cAccount linking is disabled."));
                            return 0;
                        }
                        
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String code = DiscordManager.getInstance().generateLinkCode(player);
                        
                        if (code != null) {
                            context.getSource().sendSuccess(() -> Component.literal(
                                "§aYour link code is: §e" + code + "\n" +
                                "§7Use §b/link " + code + "§7 in Discord to link your account.\n" +
                                "§7Code expires in " + (Config.LINK_CODE_EXPIRY_SECONDS.get() / 60) + " minutes."
                            ), false);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("§cFailed to generate link code."));
                            return 0;
                        }
                    })
                )
                .then(Commands.literal("unlink")
                    .executes(context -> {
                        if (!Config.ENABLE_ACCOUNT_LINKING.get()) {
                            context.getSource().sendFailure(Component.literal("§cAccount linking is disabled."));
                            return 0;
                        }
                        
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        boolean success = DiscordManager.getInstance().unlinkAccount(player.getUUID());
                        
                        if (success) {
                            context.getSource().sendSuccess(() -> Component.literal(
                                "§aYour Discord account has been unlinked."
                            ), false);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("§cYou don't have a linked Discord account."));
                            return 0;
                        }
                    })
                )
        );
        
        // /viscord command for admin functions
        dispatcher.register(
            Commands.literal("viscord")
                .then(Commands.literal("help")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.literal(
                            "§6§l=== Viscord Commands ===\n" +
                            "§b/discord§7 - Show Discord invite link\n" +
                            "§b/discord link§7 - Generate account link code\n" +
                            "§b/discord unlink§7 - Unlink your Discord account\n" +
                            "§b/viscord help§7 - Show this help message\n" +
                            "§b/viscord reload§7 - Reload config (requires op)\n" +
                            "§7Discord: §b/list§7 - Show online players"
                        ), false);
                        return 1;
                    })
                )
                .then(Commands.literal("reload")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.literal(
                            "§eReloading Viscord configuration..."
                        ), false);
                        
                        // Reload config (it auto-reloads from file on next access)
                        DiscordManager.getInstance().reloadConfig();
                        
                        context.getSource().sendSuccess(() -> Component.literal(
                            "§aViscord configuration reloaded! Restart may be required for some changes."
                        ), false);
                        return 1;
                    })
                )
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
        if (!shouldProcessEvent("Join", Config.SEND_JOIN_MESSAGES.get())) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        String username = player.getName().getString();

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Player joined: {}", username);
        }

        DiscordManager.getInstance().sendJoinEmbed(username);
        scheduleStatusUpdate(player.getServer());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!shouldProcessEvent("Leave", Config.SEND_LEAVE_MESSAGES.get())) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        String username = player.getName().getString();

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Player left: {}", username);
        }

        DiscordManager.getInstance().sendLeaveEmbed(username);
        scheduleStatusUpdate(player.getServer());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!shouldProcessEvent("Death", Config.SEND_DEATH_MESSAGES.get())) {
            return;
        }

        String deathMessage = event
            .getSource()
            .getLocalizedDeathMessage(player)
            .getString();

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Player death: {}", deathMessage);
        }

        DiscordManager.getInstance().sendSystemMessage("💀 " + deathMessage);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onAdvancement(
        AdvancementEvent.AdvancementEarnEvent event
    ) {
        if (!shouldProcessEvent("Advancement", Config.SEND_ADVANCEMENT_MESSAGES.get())) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        AdvancementHolder advancement = event.getAdvancement();

        // Only announce advancements that should be announced (not recipes, etc.)
        if (advancement.value().display().isEmpty()) {
            Viscord.LOGGER.debug("Advancement has no display, skipping");
            return;
        }

        var display = advancement.value().display().get();
        if (!display.shouldAnnounceChat()) {
            Viscord.LOGGER.debug(
                "Advancement should not be announced in chat, skipping"
            );
            return;
        }

        String username = player.getName().getString();
        String advancementTitle = display.getTitle().getString();
        String advancementDescription = display.getDescription().getString();

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
            display.getType().name()
        );
    }

    /**
     * Guard method to check if an event should be processed.
     * Reduces boilerplate in event handlers.
     */
    private static boolean shouldProcessEvent(String eventName, boolean configEnabled) {
        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("{} event triggered", eventName);
        }
        
        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.warn(
                "DiscordManager not running, skipping {} message",
                eventName
            );
            return false;
        }
        
        if (!configEnabled) {
            Viscord.LOGGER.info("{} messages disabled in config", eventName);
            return false;
        }
        
        return true;
    }

    private static void scheduleStatusUpdate(net.minecraft.server.MinecraftServer server) {
        // Schedule status update on the server thread after a short delay
        if (server != null) {
            server.executeBlocking(() -> {
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

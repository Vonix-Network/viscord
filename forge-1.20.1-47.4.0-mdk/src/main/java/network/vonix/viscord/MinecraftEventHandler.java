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
                                                "Discord invite URL is not configured. Ask an admin to set 'discordInviteUrl' in viscord-common.toml."),
                                        false);
                            } else {
                                MutableComponent clickable = Component
                                        .literal("Click Here to join the Discord!")
                                        .withStyle(style -> style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite))
                                                .withUnderlined(true)
                                                .withColor(ChatFormatting.AQUA));

                                source.sendSuccess(() -> clickable, false);
                            }
                            return 1;
                        })
                        .then(Commands.literal("link")
                                .executes(context -> {
                                    if (!Config.ENABLE_ACCOUNT_LINKING.get()) {
                                        context.getSource()
                                                .sendFailure(Component.literal("§cAccount linking is disabled."));
                                        return 0;
                                    }

                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String code = DiscordManager.getInstance().generateLinkCode(player);

                                    if (code != null) {
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "§aYour link code is: §e" + code + "\n" +
                                                        "§7Use §b/link " + code
                                                        + "§7 in Discord to link your account.\n" +
                                                        "§7Code expires in "
                                                        + (Config.LINK_CODE_EXPIRY_SECONDS.get() / 60) + " minutes."),
                                                false);
                                        return 1;
                                    } else {
                                        context.getSource()
                                                .sendFailure(Component.literal("§cFailed to generate link code."));
                                        return 0;
                                    }
                                }))
                        .then(Commands.literal("unlink")
                                .executes(context -> {
                                    if (!Config.ENABLE_ACCOUNT_LINKING.get()) {
                                        context.getSource()
                                                .sendFailure(Component.literal("§cAccount linking is disabled."));
                                        return 0;
                                    }

                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean success = DiscordManager.getInstance().unlinkAccount(player.getUUID());

                                    if (success) {
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "§aYour Discord account has been unlinked."), false);
                                        return 1;
                                    } else {
                                        context.getSource().sendFailure(
                                                Component.literal("§cYou don't have a linked Discord account."));
                                        return 0;
                                    }
                                })));

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
                                                    "§b/viscord messages§7 - Toggle server messages on/off\n" +
                                                    "§b/viscord events§7 - Toggle event messages on/off\n" +
                                                    "§b/viscord help§7 - Show this help message\n" +
                                                    "§b/viscord reload§7 - Reload config (requires op)\n" +
                                                    "§7Discord: §b/list§7 - Show online players"),
                                            false);
                                    return 1;
                                }))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "§eReloading Viscord configuration..."), false);

                                    // Reload config (it auto-reloads from file on next access)
                                    DiscordManager.getInstance().reloadConfig();

                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "§aViscord configuration reloaded! Restart may be required for some changes."),
                                            false);
                                    return 1;
                                }))
                        .then(Commands.literal("messages")
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance().setServerMessagesFiltered(player.getUUID(),
                                                    false);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "§aServer messages enabled! You will now see messages from other servers and bots."),
                                                    false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance().setServerMessagesFiltered(player.getUUID(),
                                                    true);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "§cServer messages disabled! You will only see messages from Discord users and your own server."),
                                                    false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasServerMessagesFiltered(player.getUUID());
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "§7Server messages are currently: "
                                                    + (isFiltered ? "§cDisabled" : "§aEnabled") + "\n" +
                                                    "§7Use §b/viscord messages enable§7 or §b/viscord messages disable§7 to change."),
                                            false);
                                    return 1;
                                }))
                        .then(Commands.literal("events")
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance().setEventsFiltered(player.getUUID(), false);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "§aEvent messages enabled! You will now see achievements and join/leave messages."),
                                                    false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance().setEventsFiltered(player.getUUID(), true);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "§cEvent messages disabled! You will no longer see achievements and join/leave messages."),
                                                    false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance().hasEventsFiltered(player.getUUID());
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "§7Event messages are currently: "
                                                    + (isFiltered ? "§cDisabled" : "§aEnabled") + "\n" +
                                                    "§7Use §b/viscord events enable§7 or §b/viscord events disable§7 to change."),
                                            false);
                                    return 1;
                                })));
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
                    "DiscordManager is not running, skipping join message");
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
                    "DiscordManager is not running, skipping leave message");
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
                player.getName().getString());

        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.warn(
                    "DiscordManager is not running, skipping death message");
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
            AdvancementEvent.AdvancementEarnEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        Advancement advancement = event.getAdvancement();

        Viscord.LOGGER.info(
                "Advancement event triggered for: {}",
                player.getName().getString());

        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.warn(
                    "DiscordManager is not running, skipping advancement message");
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
                    "Advancement should not be announced in chat, skipping");
            return;
        }

        String username = player.getName().getString();
        String advancementTitle = display.getTitle().getString();
        String advancementDescription = display.getDescription().getString();

        Viscord.LOGGER.info(
                "Sending advancement message for: {} - {}",
                username,
                advancementTitle);

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug(
                    "Player advancement: {} - {}",
                    username,
                    advancementTitle);
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

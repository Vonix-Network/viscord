package network.vonix.viscord;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles Minecraft events and relays them to Discord.
 * Uses Fabric API callbacks instead of Forge event bus.
 */
public class MinecraftEventHandler {

    /**
     * Register all event callbacks. Called from main mod initializer.
     */
    public static void register() {
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        // Register chat message handler
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (!DiscordManager.getInstance().isRunning()) {
                return;
            }

            String username = sender.getName().getString();
            String messageText = message.signedContent();

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Chat message from {}: {}", username, messageText);
            }

            // Send to Discord via webhook
            DiscordManager.getInstance().sendMinecraftMessage(username, messageText);
        });

        // Register player join handler
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!shouldProcessEvent("Join", Config.SEND_JOIN_MESSAGES.get())) {
                return;
            }

            ServerPlayer player = handler.getPlayer();
            String username = player.getName().getString();

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Player joined: {}", username);
            }

            DiscordManager.getInstance().sendJoinEmbed(username);
            scheduleStatusUpdate(server);
        });

        // Register player disconnect handler
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!shouldProcessEvent("Leave", Config.SEND_LEAVE_MESSAGES.get())) {
                return;
            }

            ServerPlayer player = handler.getPlayer();
            String username = player.getName().getString();

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Player left: {}", username);
            }

            DiscordManager.getInstance().sendLeaveEmbed(username);
            scheduleStatusUpdate(server);
        });

        // Register death handler
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return;
            }

            if (!shouldProcessEvent("Death", Config.SEND_DEATH_MESSAGES.get())) {
                return;
            }

            String deathMessage = damageSource.getLocalizedDeathMessage(player).getString();

            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                Viscord.LOGGER.debug("Player death: {}", deathMessage);
            }

            DiscordManager.getInstance().sendSystemMessage("💀 " + deathMessage);
        });

        Viscord.LOGGER.info("Registered Fabric event callbacks");
    }

    /**
     * Called from AdvancementMixin when a player earns an advancement.
     */
    public static void onAdvancementEarned(ServerPlayer player, net.minecraft.advancements.Advancement advancement) {
        if (!shouldProcessEvent("Advancement", Config.SEND_ADVANCEMENT_MESSAGES.get())) {
            return;
        }

        // Only announce advancements that should be announced (not recipes, etc.)
        if (advancement.getDisplay() == null) {
            Viscord.LOGGER.debug("Advancement has no display, skipping");
            return;
        }

        var display = advancement.getDisplay();
        if (!display.shouldAnnounceChat()) {
            Viscord.LOGGER.debug("Advancement should not be announced in chat, skipping");
            return;
        }

        String username = player.getName().getString();
        String advancementTitle = display.getTitle().getString();
        String advancementDescription = display.getDescription().getString();

        if (Config.ENABLE_DEBUG_LOGGING.get()) {
            Viscord.LOGGER.debug("Player advancement: {} - {}", username, advancementTitle);
        }

        DiscordManager.getInstance().sendAdvancementEmbed(
                username,
                advancementTitle,
                advancementDescription,
                "ADVANCEMENT");
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
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
                                                "Discord invite URL is not configured. Ask an admin to set 'discordInviteUrl' in viscord.json."),
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

                                    // Reload config
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
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasEventsFiltered(player.getUUID());
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "§7Event messages are currently: "
                                                    + (isFiltered ? "§cDisabled" : "§aEnabled") + "\n" +
                                                    "§7Use §b/viscord events enable§7 or §b/viscord events disable§7 to change."),
                                            false);
                                    return 1;
                                })));
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
                    eventName);
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

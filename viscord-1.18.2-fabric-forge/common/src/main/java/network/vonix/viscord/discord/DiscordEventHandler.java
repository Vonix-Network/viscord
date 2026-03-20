package network.vonix.viscord.discord;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.config.ViscordConfig;

/**
 * Minecraft event handler for Discord integration.
 * Handles commands, player join/leave, death, and advancement events.
 */
public class DiscordEventHandler {

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            registerCommands(dispatcher);
        });

        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (DiscordManager.getInstance().isRunning()) {
                DiscordManager.getInstance().sendJoinEmbed(player.getName().getString(), player.getUUID().toString());
                // Schedule status update after delay to ensure accurate player count
                DiscordManager.getInstance().scheduleStatusUpdate(1000);
            }
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (DiscordManager.getInstance().isRunning()) {
                DiscordManager.getInstance().sendLeaveEmbed(player.getName().getString(), player.getUUID().toString());
                // Schedule status update after delay to ensure accurate player count
                DiscordManager.getInstance().scheduleStatusUpdate(1000);
            }
        });

        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) entity;
                if (DiscordManager.getInstance().isRunning() && ViscordConfig.CONFIG.sendDeath.get()) {
                    String deathMessage = source.getLocalizedDeathMessage(player).getString();
                    DiscordManager.getInstance().sendServerStatusMessage("Player Died", "💀 " + deathMessage, 0x000000);
                }
            }
            return EventResult.pass();
        });

        // Chat event is handled via ChatFormatter or Mixin to ensure compatibility
        // Advancement event requires Mixin into PlayerAdvancements
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /discord command - shows Discord invite
        dispatcher.register(Commands.literal("discord")
                .executes(context -> {
                    // Default behavior - show info
                    context.getSource().sendSuccess(() ->
                        Component.literal("Join our Discord server! ")
                            .append(Component.literal("[Click Here]")
                                .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/YOUR_INVITE"))
                                    .withBold(true))), false);
                    return 1;
                }));

        // /link command - link Discord account
        dispatcher.register(Commands.literal("link")
                .requires(source -> source.isPlayer())
                .executes(context -> {
                    if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                        context.getSource().sendFailure(Component.literal("Account linking is disabled."));
                        return 0;
                    }

                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        String code = DiscordManager.getInstance().generateLinkCode(player);
                        if (code != null) {
                            context.getSource().sendSuccess(() ->
                                Component.literal("Your link code is: ")
                                    .append(Component.literal(code)
                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                                    .append(Component.literal(". DM this code to the bot on Discord.")), false);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("Failed to generate link code. Is account linking enabled?"));
                            return 0;
                        }
                    }
                    return 0;
                }));

        // /unlink command - unlink Discord account
        dispatcher.register(Commands.literal("unlink")
                .requires(source -> source.isPlayer())
                .executes(context -> {
                    if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                        context.getSource().sendFailure(Component.literal("Account linking is disabled."));
                        return 0;
                    }

                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        boolean unlinked = DiscordManager.getInstance().unlinkAccount(player.getUUID());
                        if (unlinked) {
                            context.getSource().sendSuccess(() ->
                                Component.literal("Your account has been unlinked.").withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("Your account was not linked."));
                            return 0;
                        }
                    }
                    return 0;
                }));

        // Filter commands for Discord messages
        dispatcher.register(Commands.literal("filter")
                .requires(source -> source.isPlayer())
                .then(Commands.literal("discord")
                    .then(Commands.literal("on")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                DiscordManager.getInstance().setServerMessagesFiltered(player.getUUID(), true);
                                context.getSource().sendSuccess(() ->
                                    Component.literal("Discord messages are now hidden.").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }
                            return 0;
                        }))
                    .then(Commands.literal("off")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                DiscordManager.getInstance().setServerMessagesFiltered(player.getUUID(), false);
                                context.getSource().sendSuccess(() ->
                                    Component.literal("Discord messages are now visible.").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }
                            return 0;
                        })))
                .then(Commands.literal("events")
                    .then(Commands.literal("on")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                DiscordManager.getInstance().setEventsFiltered(player.getUUID(), true);
                                context.getSource().sendSuccess(() ->
                                    Component.literal("Discord event messages are now hidden.").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }
                            return 0;
                        }))
                    .then(Commands.literal("off")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                DiscordManager.getInstance().setEventsFiltered(player.getUUID(), false);
                                context.getSource().sendSuccess(() ->
                                    Component.literal("Discord event messages are now visible.").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }
                            return 0;
                        }))));
    }
}

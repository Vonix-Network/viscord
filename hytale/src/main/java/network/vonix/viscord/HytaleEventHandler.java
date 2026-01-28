package network.vonix.viscord;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Hytale server events using reflection to ensure compatibility.
 * Accepts Generic Object and uses reflection to extract data, preventing
 * NoClassDefFoundError.
 */
public class HytaleEventHandler {

    // ========= Event Handlers =========

    /**
     * Handle player ready event (player joined and ready).
     */
    public static void onPlayerReady(Object event) {
        if (!Viscord.getInstance().isEnabled())
            return;

        try {
            // Reflectively access player data
            Object player = getMethodResult(event, "getPlayer");
            if (player == null)
                return;

            String name = (String) getMethodResult(player, "getDisplayName");
            if (name == null)
                name = (String) getMethodResult(player, "getName");

            if (name != null) {
                Viscord.LOGGER.info("Player joined: {}", name);
                DiscordManager.getInstance().sendJoinEmbed(name);
                DiscordManager.getInstance().updateBotStatus();
            }
        } catch (Exception e) {
            Viscord.LOGGER.debug("Error handling PlayerReadyEvent reflection: {}", e.getMessage());
        }
    }

    /**
     * Handle player disconnect event (player left).
     */
    public static void onPlayerDisconnect(Object event) {
        if (!Viscord.getInstance().isEnabled())
            return;

        try {
            Object player = getMethodResult(event, "getPlayer");
            if (player == null)
                return;

            String name = (String) getMethodResult(player, "getDisplayName");
            if (name == null)
                name = (String) getMethodResult(player, "getName");

            if (name != null) {
                Viscord.LOGGER.info("Player left: {}", name);
                DiscordManager.getInstance().sendLeaveEmbed(name);
                DiscordManager.getInstance().updateBotStatus();
            }
        } catch (Exception e) {
            Viscord.LOGGER.debug("Error handling PlayerDisconnectEvent reflection: {}", e.getMessage());
        }
    }

    /**
     * Handle player chat event.
     */
    public static void onPlayerChat(Object event) {
        if (!Viscord.getInstance().isEnabled())
            return;

        try {
            Object player = getMethodResult(event, "getPlayer");
            String message = (String) getMethodResult(event, "getMessage");

            if (player != null && message != null) {
                String name = (String) getMethodResult(player, "getDisplayName");
                if (name == null)
                    name = (String) getMethodResult(player, "getName");

                if (name != null) {
                    DiscordManager.getInstance().sendHytaleMessage(name, message);
                }
            }
        } catch (Exception e) {
            Viscord.LOGGER.debug("Error handling PlayerChatEvent reflection: {}", e.getMessage());
        }
    }

    /**
     * Handle player death event.
     */
    public static void onPlayerDeath(Object event) {
        if (!Viscord.getInstance().isEnabled())
            return;

        try {
            Object player = getMethodResult(event, "getPlayer");
            if (player == null)
                return;

            String name = (String) getMethodResult(player, "getDisplayName");
            if (name == null)
                name = (String) getMethodResult(player, "getName");

            // Try different death message methods
            String deathMessage = null;
            try {
                Object deathMsgObj = getMethodResult(event, "getDeathMessage");
                if (deathMsgObj != null)
                    deathMessage = deathMsgObj.toString();
            } catch (Exception ignored) {
            }

            if (deathMessage == null) {
                try {
                    Object deathMsgObj = getMethodResult(event, "deathMessage");
                    if (deathMsgObj != null)
                        deathMessage = deathMsgObj.toString();
                } catch (Exception ignored) {
                }
            }

            if (name != null) {
                Viscord.LOGGER.info("Player died: {}", name);
                DiscordManager.getInstance().sendDeathEmbed(name, deathMessage != null ? deathMessage : "Died");
            }
        } catch (Exception e) {
            Viscord.LOGGER.debug("Error handling PlayerDeathEvent reflection: {}", e.getMessage());
        }
    }

    // Helper for reflection
    private static Object getMethodResult(Object obj, String methodName) {
        if (obj == null)
            return null;
        try {
            Method method = obj.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
}

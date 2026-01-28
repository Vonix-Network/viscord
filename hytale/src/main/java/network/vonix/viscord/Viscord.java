package network.vonix.viscord;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Viscord - Discord integration for Hytale servers.
 * Uses reflection for event registration to ensure compatibility across API
 * variations.
 */
public class Viscord extends JavaPlugin {

    public static final String MOD_ID = "viscord";
    public static final String MOD_NAME = "Viscord";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static Viscord instance;
    private boolean enabled = false;

    public Viscord(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        LOGGER.info("===========================================");
        LOGGER.info("  {} v{}", MOD_NAME, VERSION);
        LOGGER.info("  Discord Integration for Hytale");
        LOGGER.info("===========================================");

        // Load configuration
        Config.load();
        LOGGER.info("Configuration loaded");

        // Register event handlers using reflection to avoid NoSuchMethodError
        registerEvents();
    }

    private void registerEvents() {
        try {
            LOGGER.info("Registering Hytale event handlers via reflection...");

            // 1. Get the getEventRegistry method from this class (inherited from
            // JavaPlugin/PluginBase)
            Method getRegistryMethod = null;
            Class<?> current = this.getClass();
            while (current != null && getRegistryMethod == null) {
                try {
                    getRegistryMethod = current.getDeclaredMethod("getEventRegistry");
                } catch (NoSuchMethodException e) {
                    current = current.getSuperclass();
                }
            }

            if (getRegistryMethod == null) {
                LOGGER.error("Could not find getEventRegistry method in class hierarchy!");
                return;
            }

            getRegistryMethod.setAccessible(true);
            Object registry = getRegistryMethod.invoke(this);

            if (registry == null) {
                LOGGER.error("EventRegistry is null!");
                return;
            }

            // 2. Register each event safely by class name
            // Player join/ready events
            registerSafe(registry, HytaleEventHandler::onPlayerReady,
                    "com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent",
                    "com.hypixel.hytale.server.core.event.events.player.PlayerJoinEvent");

            // Player leave/disconnect events
            registerSafe(registry, HytaleEventHandler::onPlayerDisconnect,
                    "com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent",
                    "com.hypixel.hytale.server.core.event.events.player.PlayerLeaveEvent",
                    "com.hypixel.hytale.server.core.event.events.player.PlayerQuitEvent");

            // Player chat events - try multiple possible names
            registerSafe(registry, HytaleEventHandler::onPlayerChat,
                    "com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent",
                    "com.hypixel.hytale.server.core.event.events.player.ChatMessageEvent",
                    "com.hypixel.hytale.server.core.event.events.chat.ChatMessageEvent",
                    "com.hypixel.hytale.server.core.event.events.ChatEvent");

            // Player death events
            registerSafe(registry, HytaleEventHandler::onPlayerDeath,
                    "com.hypixel.hytale.server.core.event.events.player.PlayerDeathEvent",
                    "com.hypixel.hytale.server.core.event.events.entity.EntityDeathEvent");

            LOGGER.info("Hytale event registration completed");
        } catch (Exception e) {
            LOGGER.error("Failed to register events via reflection", e);
        }
    }

    private void registerSafe(Object registry, Consumer<Object> handler, String... classNames) {
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                reflectiveRegister(registry, clazz, handler);
                LOGGER.info("✓ Registered event: {}", className.substring(className.lastIndexOf('.') + 1));
                return; // Success, don't try other names
            } catch (ClassNotFoundException e) {
                // Try next class name
                if (Config.ENABLE_DEBUG_LOGGING.get()) {
                    LOGGER.debug("Event class not found: {}", className);
                }
            } catch (Exception e) {
                LOGGER.warn("Error registering event {}: {}", className, e.getMessage());
            }
        }
        // None of the class names worked
        String eventType = classNames[0].substring(classNames[0].lastIndexOf('.') + 1);
        LOGGER.warn("✗ Could not register event type: {} (tried {} variants)", eventType, classNames.length);
    }

    @SuppressWarnings("unchecked")
    private void reflectiveRegister(Object registry, Class<?> eventClass, Consumer<Object> handler) throws Exception {
        Method registerMethod = null;

        // Try registerGlobal first (preferred in Hytale examples)
        try {
            registerMethod = registry.getClass().getMethod("registerGlobal", Class.class, Consumer.class);
        } catch (NoSuchMethodException e) {
            // Try register second
            try {
                registerMethod = registry.getClass().getMethod("register", Class.class, Consumer.class);
            } catch (NoSuchMethodException e2) {
                // Try to find any method that takes Class and Consumer
                for (Method m : registry.getClass().getMethods()) {
                    if (m.getParameterCount() == 2 &&
                            m.getParameterTypes()[0].equals(Class.class) &&
                            m.getParameterTypes()[1].equals(Consumer.class)) {
                        registerMethod = m;
                        break;
                    }
                }
            }
        }

        if (registerMethod != null) {
            registerMethod.setAccessible(true);
            registerMethod.invoke(registry, eventClass, handler);
            if (Config.ENABLE_DEBUG_LOGGING.get()) {
                LOGGER.debug("Registered event {} using method {}", eventClass.getSimpleName(),
                        registerMethod.getName());
            }
        } else {
            LOGGER.error("Could not find registration method on registry for {}", eventClass.getName());
        }
    }

    @Override
    protected void start() {
        LOGGER.info("Starting Viscord...");

        // Check for updates
        if (Config.ENABLE_UPDATE_CHECKER.get()) {
            UpdateChecker.checkForUpdates().thenAccept(result -> {
                if (result.updateAvailable) {
                    LOGGER.warn("========================================");
                    LOGGER.warn("  A new version of Viscord is available!");
                    LOGGER.warn("  Current: {} | Latest: {}", VERSION, result.latestVersion);
                    LOGGER.warn("  Download: {}", result.downloadUrl);
                    LOGGER.warn("========================================");
                }
            });
        }

        // Initialize Discord integration
        DiscordManager.getInstance().initialize(null);

        enabled = true;
        LOGGER.info("Viscord started successfully!");
    }

    @Override
    protected void shutdown() {
        if (!enabled) {
            return;
        }

        LOGGER.info("Shutting down Viscord...");

        String serverName = Config.SERVER_NAME.get();
        DiscordManager.getInstance().sendShutdownEmbed(serverName);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        DiscordManager.getInstance().shutdown();

        enabled = false;
        LOGGER.info("Viscord shut down");
    }

    public static Viscord getInstance() {
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

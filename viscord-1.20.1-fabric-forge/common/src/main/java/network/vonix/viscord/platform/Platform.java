package network.vonix.viscord.platform;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Platform abstraction for Viscord.
 * Provides platform-specific functionality that varies between Fabric and Forge.
 */
public class Platform {

    /**
     * Gets the configuration directory for the mod.
     */
    public static Path getConfigDirectory() {
        return Paths.get("config");
    }

    /**
     * Check if we're running on a dedicated server.
     */
    public static boolean isDedicatedServer() {
        // This will be implemented differently on Fabric vs Forge
        // For now, return false and let platform-specific implementations override
        return false;
    }
}

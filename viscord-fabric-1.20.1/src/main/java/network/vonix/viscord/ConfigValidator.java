package network.vonix.viscord;

/**
 * Utility for validating configuration values.
 * Reduces duplication in config validation code.
 */
public class ConfigValidator {

    /**
     * Check if a config value is properly configured (not null, empty, or default)
     */
    public static boolean isConfigured(String value, String defaultValue) {
        return value != null && !value.isEmpty() && !value.equals(defaultValue);
    }

    /**
     * Require a config value to be configured, logging an error if not
     * 
     * @return true if configured, false otherwise
     */
    public static boolean requireConfigured(String value, String defaultValue, String name) {
        if (!isConfigured(value, defaultValue)) {
            Viscord.LOGGER.error("{} not configured! Please set it in the config file.", name);
            return false;
        }
        return true;
    }

    /**
     * Check if a config value is configured, logging a warning if not
     * 
     * @return true if configured, false otherwise
     */
    public static boolean warnIfNotConfigured(String value, String defaultValue, String name) {
        if (!isConfigured(value, defaultValue)) {
            Viscord.LOGGER.warn("{} not configured!", name);
            return false;
        }
        return true;
    }
}

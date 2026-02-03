package network.vonix.viscord;

import org.javacord.api.entity.server.Server;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPrefixConfig {
    
    private static final String DEFAULT_PREFIX = "MC";
    private static final int MAX_PREFIX_LENGTH = 16;
    
    private final Map<Long, String> serverPrefixMap = new ConcurrentHashMap<>();
    private final Set<String> usedPrefixes = ConcurrentHashMap.newKeySet();
    
    private String fallbackPrefix = DEFAULT_PREFIX;
    private int prefixCounter = 1;
    
    public String getServerPrefix(long serverId) {
        return serverPrefixMap.computeIfAbsent(serverId, this::generateUniquePrefix);
    }
    
    public String getServerPrefix(Server server) {
        if (server == null) {
            return getFallbackPrefix();
        }
        return getServerPrefix(server.getId());
    }
    
    public void setServerPrefix(long serverId, String prefix) {
        if (!isValidPrefix(prefix)) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix + 
                ". Prefix must be 1-" + MAX_PREFIX_LENGTH + " characters, alphanumeric or common symbols only.");
        }
        
        String normalizedPrefix = normalizePrefix(prefix);
        if (usedPrefixes.contains(normalizedPrefix)) {
            String currentPrefix = serverPrefixMap.get(serverId);
            if (currentPrefix == null || !normalizedPrefix.equals(normalizePrefix(currentPrefix))) {
                throw new IllegalArgumentException("Prefix '" + prefix + "' is already in use by another server");
            }
        }
        
        String oldPrefix = serverPrefixMap.get(serverId);
        if (oldPrefix != null) {
            usedPrefixes.remove(normalizePrefix(oldPrefix));
        }
        
        serverPrefixMap.put(serverId, prefix);
        usedPrefixes.add(normalizedPrefix);
        
        Viscord.LOGGER.info("[Discord] Set server prefix for server {} to '{}'", serverId, prefix);
    }
    
    public void removeServerPrefix(long serverId) {
        String removedPrefix = serverPrefixMap.remove(serverId);
        if (removedPrefix != null) {
            usedPrefixes.remove(normalizePrefix(removedPrefix));
            Viscord.LOGGER.info("[Discord] Removed server prefix for server {}", serverId);
        }
    }
    
    public String getFallbackPrefix() {
        return fallbackPrefix;
    }
    
    public void setFallbackPrefix(String fallbackPrefix) {
        if (!isValidPrefix(fallbackPrefix)) {
            throw new IllegalArgumentException("Invalid fallback prefix: " + fallbackPrefix);
        }
        this.fallbackPrefix = fallbackPrefix;
        Viscord.LOGGER.info("[Discord] Set fallback prefix to '{}'", fallbackPrefix);
    }
    
    public Map<Long, String> getAllServerPrefixes() {
        return new HashMap<>(serverPrefixMap);
    }
    
    public void clearAllPrefixes() {
        serverPrefixMap.clear();
        usedPrefixes.clear();
        prefixCounter = 1;
        Viscord.LOGGER.info("[Discord] Cleared all server prefix configurations");
    }
    
    private boolean isValidPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = prefix.trim();
        if (trimmed.length() > MAX_PREFIX_LENGTH) {
            return false;
        }
        
        return trimmed.matches("[a-zA-Z0-9\\-_\\[\\]\\(\\)\\{\\}]+");
    }
    
    private String normalizePrefix(String prefix) {
        return prefix.trim().toLowerCase();
    }
    
    private String generateUniquePrefix(long serverId) {
        String basePrefix = "S" + Math.abs(serverId % 1000);
        
        String candidatePrefix = basePrefix;
        while (usedPrefixes.contains(normalizePrefix(candidatePrefix))) {
            candidatePrefix = basePrefix + "_" + prefixCounter++;
            
            if (prefixCounter > 9999) {
                candidatePrefix = "MC" + System.currentTimeMillis() % 1000;
                break;
            }
        }
        
        if (candidatePrefix.length() > MAX_PREFIX_LENGTH) {
            candidatePrefix = candidatePrefix.substring(0, MAX_PREFIX_LENGTH);
        }
        
        usedPrefixes.add(normalizePrefix(candidatePrefix));
        Viscord.LOGGER.info("[Discord] Generated unique prefix '{}' for server {}", candidatePrefix, serverId);
        
        return candidatePrefix;
    }
    
    public boolean validateConfiguration() {
        boolean isValid = true;
        Set<String> duplicateCheck = new HashSet<>();
        
        for (Map.Entry<Long, String> entry : serverPrefixMap.entrySet()) {
            String prefix = entry.getValue();
            String normalized = normalizePrefix(prefix);
            
            if (!isValidPrefix(prefix)) {
                Viscord.LOGGER.warn("[Discord] Invalid prefix '{}' found for server {}", prefix, entry.getKey());
                isValid = false;
            }
            
            if (duplicateCheck.contains(normalized)) {
                Viscord.LOGGER.warn("[Discord] Duplicate prefix '{}' found for server {}", prefix, entry.getKey());
                isValid = false;
            } else {
                duplicateCheck.add(normalized);
            }
        }
        
        if (!isValidPrefix(fallbackPrefix)) {
            Viscord.LOGGER.warn("[Discord] Invalid fallback prefix '{}'", fallbackPrefix);
            isValid = false;
        }
        
        if (isValid) {
            Viscord.LOGGER.debug("[Discord] Server prefix configuration validation passed");
        } else {
            Viscord.LOGGER.warn("[Discord] Server prefix configuration validation failed");
        }
        
        return isValid;
    }
}

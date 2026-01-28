package network.vonix.viscord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages linked accounts between Hytale and Discord.
 * Stores UUID -> Discord ID mappings and handles link code
 * generation/validation.
 */
public class LinkedAccountsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;
    private final Map<UUID, LinkedAccount> linkedAccounts;
    private final Map<String, PendingLink> pendingLinks;

    public LinkedAccountsManager(Path configDir) throws IOException {
        this.dataFile = configDir.resolve("viscord-linked-accounts.json");
        this.linkedAccounts = new ConcurrentHashMap<>();
        this.pendingLinks = new ConcurrentHashMap<>();
        load();
    }

    /**
     * Generate a 6-digit link code for a player
     */
    public String generateLinkCode(UUID minecraftUUID, String minecraftUsername) {
        // Clean up expired codes first
        cleanupExpiredCodes();

        // Generate a random 6-digit code
        String code;
        do {
            code = String.format("%06d", new Random().nextInt(1000000));
        } while (pendingLinks.containsKey(code));

        // Store the pending link
        long expiryTime = System.currentTimeMillis() + (Config.LINK_CODE_EXPIRY_SECONDS.get() * 1000L);
        pendingLinks.put(code, new PendingLink(minecraftUUID, minecraftUsername, expiryTime));

        Viscord.LOGGER.info("Generated link code {} for player {} ({})",
                code, minecraftUsername, minecraftUUID);

        return code;
    }

    /**
     * Verify and complete a link using a code
     */
    public LinkResult verifyAndLink(String code, String discordId, String discordUsername) {
        cleanupExpiredCodes();

        PendingLink pending = pendingLinks.get(code);
        if (pending == null) {
            return new LinkResult(false, "Invalid or expired code. Please generate a new code in-game.");
        }

        if (System.currentTimeMillis() > pending.expiryTime) {
            pendingLinks.remove(code);
            return new LinkResult(false, "This code has expired. Please generate a new code in-game.");
        }

        // Check if Discord account is already linked
        for (LinkedAccount account : linkedAccounts.values()) {
            if (account.discordId.equals(discordId)) {
                return new LinkResult(false,
                        "Your Discord account is already linked to " + account.minecraftUsername + ".");
            }
        }

        // Check if Minecraft account is already linked
        if (linkedAccounts.containsKey(pending.minecraftUUID)) {
            LinkedAccount existing = linkedAccounts.get(pending.minecraftUUID);
            return new LinkResult(false,
                    "This Hytale account is already linked to Discord user " + existing.discordUsername + ".");
        }

        // Create the link
        LinkedAccount newLink = new LinkedAccount(
                pending.minecraftUUID,
                pending.minecraftUsername,
                discordId,
                discordUsername,
                System.currentTimeMillis());

        linkedAccounts.put(pending.minecraftUUID, newLink);
        pendingLinks.remove(code);
        save();

        Viscord.LOGGER.info("Linked {} ({}) to Discord user {} ({})",
                pending.minecraftUsername, pending.minecraftUUID, discordUsername, discordId);

        return new LinkResult(true,
                "Successfully linked **" + pending.minecraftUsername + "** to your Discord account!");
    }

    /**
     * Unlink a Hytale account
     */
    public boolean unlinkMinecraft(UUID minecraftUUID) {
        LinkedAccount removed = linkedAccounts.remove(minecraftUUID);
        if (removed != null) {
            save();
            Viscord.LOGGER.info("Unlinked Hytale account {} ({})",
                    removed.minecraftUsername, minecraftUUID);
            return true;
        }
        return false;
    }

    /**
     * Unlink a Discord account
     */
    public boolean unlinkDiscord(String discordId) {
        UUID toRemove = null;
        for (Map.Entry<UUID, LinkedAccount> entry : linkedAccounts.entrySet()) {
            if (entry.getValue().discordId.equals(discordId)) {
                toRemove = entry.getKey();
                break;
            }
        }

        if (toRemove != null) {
            LinkedAccount removed = linkedAccounts.remove(toRemove);
            save();
            Viscord.LOGGER.info("Unlinked Discord account {} from Hytale {}",
                    discordId, removed.minecraftUsername);
            return true;
        }
        return false;
    }

    /**
     * Get linked account by Hytale UUID
     */
    public LinkedAccount getByMinecraft(UUID minecraftUUID) {
        return linkedAccounts.get(minecraftUUID);
    }

    /**
     * Get linked account by Discord ID
     */
    public LinkedAccount getByDiscord(String discordId) {
        for (LinkedAccount account : linkedAccounts.values()) {
            if (account.discordId.equals(discordId)) {
                return account;
            }
        }
        return null;
    }

    /**
     * Check if a Hytale account is linked
     */
    public boolean isLinked(UUID minecraftUUID) {
        return linkedAccounts.containsKey(minecraftUUID);
    }

    /**
     * Get total number of linked accounts
     */
    public int getLinkedCount() {
        return linkedAccounts.size();
    }

    /**
     * Clean up expired link codes
     */
    private void cleanupExpiredCodes() {
        long now = System.currentTimeMillis();
        pendingLinks.entrySet().removeIf(entry -> now > entry.getValue().expiryTime);
    }

    /**
     * Save linked accounts to file
     */
    private void save() {
        try {
            Files.createDirectories(dataFile.getParent());

            // Convert to serializable format
            Map<String, LinkedAccount> serializableMap = new HashMap<>();
            for (Map.Entry<UUID, LinkedAccount> entry : linkedAccounts.entrySet()) {
                serializableMap.put(entry.getKey().toString(), entry.getValue());
            }

            try (Writer writer = new FileWriter(dataFile.toFile())) {
                GSON.toJson(serializableMap, writer);
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Failed to save linked accounts", e);
        }
    }

    /**
     * Load linked accounts from file
     */
    private void load() throws IOException {
        if (!Files.exists(dataFile)) {
            return;
        }

        try (Reader reader = new FileReader(dataFile.toFile())) {
            Type type = new TypeToken<Map<String, LinkedAccount>>() {
            }.getType();
            Map<String, LinkedAccount> loaded = GSON.fromJson(reader, type);

            if (loaded != null) {
                for (Map.Entry<String, LinkedAccount> entry : loaded.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        linkedAccounts.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        Viscord.LOGGER.warn("Invalid UUID in linked accounts file: {}", entry.getKey());
                    }
                }
            }
        }
    }

    // ========= Data Classes =========

    public static class LinkedAccount {
        public final UUID minecraftUUID;
        public final String minecraftUsername;
        public final String discordId;
        public final String discordUsername;
        public final long linkedTimestamp;

        public LinkedAccount(UUID minecraftUUID, String minecraftUsername, String discordId, String discordUsername,
                long linkedTimestamp) {
            this.minecraftUUID = minecraftUUID;
            this.minecraftUsername = minecraftUsername;
            this.discordId = discordId;
            this.discordUsername = discordUsername;
            this.linkedTimestamp = linkedTimestamp;
        }
    }

    public static class PendingLink {
        public final UUID minecraftUUID;
        public final String minecraftUsername;
        public final long expiryTime;

        public PendingLink(UUID minecraftUUID, String minecraftUsername, long expiryTime) {
            this.minecraftUUID = minecraftUUID;
            this.minecraftUsername = minecraftUsername;
            this.expiryTime = expiryTime;
        }
    }

    public static class LinkResult {
        public final boolean success;
        public final String message;

        public LinkResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}

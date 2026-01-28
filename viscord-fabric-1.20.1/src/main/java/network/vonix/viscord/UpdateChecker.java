package network.vonix.viscord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Checks for mod updates from GitHub releases
 */
public class UpdateChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Vonix-Network/Viscord/releases/latest";
    private static final String CURRENT_VERSION = "1.0.3";
    private static final Gson GSON = new Gson();

    private static String latestVersion = null;
    private static String downloadUrl = null;
    private static boolean updateAvailable = false;

    /**
     * Check for updates asynchronously
     */
    public static CompletableFuture<UpdateResult> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            if (!Config.ENABLE_UPDATE_CHECKER.get()) {
                return new UpdateResult(false, CURRENT_VERSION, null, "Update checker disabled");
            }

            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();

                Request request = new Request.Builder()
                        .url(GITHUB_API_URL)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Viscord.LOGGER.warn("Failed to check for updates: HTTP {}", response.code());
                        return new UpdateResult(false, CURRENT_VERSION, null, "HTTP error: " + response.code());
                    }

                    String body = response.body().string();
                    JsonObject json = GSON.fromJson(body, JsonObject.class);

                    String tagName = json.get("tag_name").getAsString();
                    String releaseUrl = json.get("html_url").getAsString();

                    // Remove 'v' prefix if present
                    latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    downloadUrl = releaseUrl;

                    updateAvailable = isNewerVersion(CURRENT_VERSION, latestVersion);

                    if (updateAvailable) {
                        Viscord.LOGGER.info("Update available! Current: {}, Latest: {}", CURRENT_VERSION,
                                latestVersion);
                        Viscord.LOGGER.info("Download: {}", downloadUrl);
                    } else {
                        Viscord.LOGGER.info("Viscord is up to date ({})", CURRENT_VERSION);
                    }

                    return new UpdateResult(updateAvailable, latestVersion, downloadUrl, null);
                }
            } catch (Exception e) {
                Viscord.LOGGER.warn("Failed to check for updates: {}", e.getMessage());
                if (Config.ENABLE_DEBUG_LOGGING.get()) {
                    e.printStackTrace();
                }
                return new UpdateResult(false, CURRENT_VERSION, null, e.getMessage());
            }
        });
    }

    /**
     * Check if an update is available (cached result)
     */
    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /**
     * Get the latest version (cached result)
     */
    public static String getLatestVersion() {
        return latestVersion != null ? latestVersion : CURRENT_VERSION;
    }

    /**
     * Get the download URL (cached result)
     */
    public static String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * Get current mod version
     */
    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    /**
     * Compare two semantic version strings
     * Returns true if newVer is newer than currentVer
     */
    private static boolean isNewerVersion(String currentVer, String newVer) {
        try {
            String[] currentParts = currentVer.split("\\.");
            String[] newParts = newVer.split("\\.");

            int maxLength = Math.max(currentParts.length, newParts.length);

            for (int i = 0; i < maxLength; i++) {
                int current = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                int newVersion = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;

                if (newVersion > current) {
                    return true;
                } else if (newVersion < current) {
                    return false;
                }
            }

            return false; // Versions are equal
        } catch (NumberFormatException e) {
            Viscord.LOGGER.warn("Failed to parse version numbers: {} vs {}", currentVer, newVer);
            return false;
        }
    }

    public static class UpdateResult {
        public final boolean updateAvailable;
        public final String latestVersion;
        public final String downloadUrl;
        public final String error;

        public UpdateResult(boolean updateAvailable, String latestVersion, String downloadUrl, String error) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.error = error;
        }
    }
}

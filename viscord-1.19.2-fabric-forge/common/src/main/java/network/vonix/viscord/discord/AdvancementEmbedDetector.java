package network.vonix.viscord.discord;

import org.javacord.api.entity.message.embed.Embed;
import java.util.Set;

/**
 * Detector class for identifying advancement embeds from Discord messages.
 * Uses footer-based detection with advancement keywords to distinguish
 * advancement embeds from regular chat embeds.
 */
public class AdvancementEmbedDetector {

    /**
     * Keywords used to identify advancement embeds in footer text.
     * These keywords are checked case-insensitively in embed footers.
     */
    private static final Set<String> ADVANCEMENT_FOOTER_KEYWORDS = Set.of(
        "advancement", "goal", "challenge", "task"
    );

    /**
     * Determines if a Discord embed is an advancement embed by checking
     * the footer text for advancement-related keywords.
     */
    public boolean isAdvancementEmbed(Embed embed) {
        if (embed == null) {
            return false;
        }

        try {
            return hasAdvancementFooter(embed);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determines the advancement type from embed content by analyzing
     * the title and footer text for type-specific keywords.
     */
    public AdvancementType getAdvancementType(Embed embed) {
        if (embed == null) {
            return AdvancementType.NORMAL;
        }

        try {
            // First check the title for advancement type indicators
            try {
                if (embed.getTitle().isPresent()) {
                    String title = embed.getTitle().get().toLowerCase();

                    if (title.contains("challenge")) {
                        return AdvancementType.CHALLENGE;
                    } else if (title.contains("goal")) {
                        return AdvancementType.GOAL;
                    }
                }
            } catch (Exception titleError) {
                // Continue to check footer
            }

            // Check footer text for additional type indicators
            try {
                if (embed.getFooter().isPresent()) {
                    String footerText = embed.getFooter().get().getText()
                            .map(String::toLowerCase)
                            .orElse("");

                    if (footerText.contains("challenge")) {
                        return AdvancementType.CHALLENGE;
                    } else if (footerText.contains("goal")) {
                        return AdvancementType.GOAL;
                    }
                }
            } catch (Exception footerError) {
                // Fall through to default
            }

            // Default to NORMAL if no specific type indicators found
            return AdvancementType.NORMAL;

        } catch (Exception e) {
            return AdvancementType.NORMAL;
        }
    }

    /**
     * Checks if the embed footer contains advancement-related keywords.
     * This is the primary method for identifying advancement embeds.
     */
    private boolean hasAdvancementFooter(Embed embed) {
        try {
            if (!embed.getFooter().isPresent()) {
                return false;
            }

            try {
                String footerText = embed.getFooter().get().getText()
                        .map(String::toLowerCase)
                        .orElse("");

                // Check if any of the advancement keywords are present in the footer
                return ADVANCEMENT_FOOTER_KEYWORDS.stream()
                        .anyMatch(keyword -> footerText.contains(keyword));

            } catch (Exception footerTextError) {
                return false;
            }

        } catch (Exception e) {
            return false;
        }
    }
}

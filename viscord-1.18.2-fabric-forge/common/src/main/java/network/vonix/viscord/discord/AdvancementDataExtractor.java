package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedField;

/**
 * Extractor class for parsing advancement data from Discord embeds.
 * Extracts player name, advancement title, and description from embed fields
 * with validation for required fields and non-empty values.
 */
public class AdvancementDataExtractor {

    private final AdvancementEmbedDetector detector;

    /**
     * Creates a new AdvancementDataExtractor with the provided detector.
     */
    public AdvancementDataExtractor() {
        this.detector = new AdvancementEmbedDetector();
    }

    /**
     * Extracts advancement data from a Discord embed.
     */
    public AdvancementData extractFromEmbed(Embed embed) throws ExtractionException {
        if (embed == null) {
            throw new ExtractionException("Embed cannot be null");
        }

        try {
            // Validate that this is actually an advancement embed
            if (!detector.isAdvancementEmbed(embed)) {
                throw new ExtractionException("Embed is not an advancement embed");
            }

            // Extract advancement type with error handling
            AdvancementType type;
            try {
                type = detector.getAdvancementType(embed);
            } catch (Exception typeError) {
                type = AdvancementType.NORMAL;
            }

            // Extract data from embed fields with enhanced error handling
            String playerName = null;
            String advancementTitle = null;
            String advancementDescription = null;

            // Parse embed fields to extract advancement data
            try {
                for (EmbedField field : embed.getFields()) {
                    try {
                        String fieldName = field.getName();
                        String fieldValue = field.getValue();

                        if (fieldName == null || fieldValue == null) {
                            continue;
                        }

                        // Normalize field names for comparison (case-insensitive)
                        String normalizedName = fieldName.toLowerCase().trim();
                        String normalizedValue = fieldValue.trim();

                        // Skip empty values
                        if (normalizedValue.isEmpty()) {
                            continue;
                        }

                        // Extract player name from various possible field names
                        if (normalizedName.contains("player") || normalizedName.contains("user") || normalizedName.contains("name")) {
                            if (playerName == null) { // Take the first match
                                playerName = normalizedValue;
                            }
                        }
                        // Extract advancement title
                        else if (normalizedName.contains("advancement") || normalizedName.contains("title") || normalizedName.contains("achievement")) {
                            if (advancementTitle == null) { // Take the first match
                                advancementTitle = normalizedValue;
                            }
                        }
                        // Extract advancement description
                        else if (normalizedName.contains("description") || normalizedName.contains("desc") || normalizedName.contains("details")) {
                            if (advancementDescription == null) { // Take the first match
                                advancementDescription = normalizedValue;
                            }
                        }
                    } catch (Exception fieldError) {
                        continue;
                    }
                }
            } catch (Exception fieldsError) {
                throw new ExtractionException("Error processing embed fields: " + fieldsError.getMessage(), fieldsError);
            }

            // Also check embed title and description as fallbacks with error handling
            if (advancementTitle == null) {
                try {
                    if (embed.getTitle().isPresent()) {
                        String embedTitle = embed.getTitle().get().trim();
                        if (!embedTitle.isEmpty() && !isGenericTitle(embedTitle)) {
                            advancementTitle = embedTitle;
                        }
                    }
                } catch (Exception titleError) {
                    // Continue
                }
            }

            if (advancementDescription == null) {
                try {
                    if (embed.getDescription().isPresent()) {
                        String embedDesc = embed.getDescription().get().trim();
                        if (!embedDesc.isEmpty()) {
                            advancementDescription = embedDesc;
                        }
                    }
                } catch (Exception descError) {
                    // Continue
                }
            }

            // Validate that all required fields were found with detailed error messages
            validateExtractedData(playerName, advancementTitle, advancementDescription, embed);

            // Create and return the AdvancementData object with error handling
            try {
                return new AdvancementData(playerName, advancementTitle, advancementDescription, type);
            } catch (Exception dataCreationError) {
                throw new ExtractionException("Failed to create AdvancementData object: " + dataCreationError.getMessage(),
                        dataCreationError);
            }

        } catch (ExtractionException e) {
            // Re-throw ExtractionExceptions as-is
            throw e;
        } catch (Exception e) {
            // Wrap any other exceptions in ExtractionException with context
            throw new ExtractionException("Unexpected error during advancement data extraction: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that all required data was successfully extracted.
     */
    private void validateExtractedData(String playerName, String advancementTitle, String advancementDescription, Embed embed)
            throws ExtractionException {

        // Check each field individually to provide specific error messages that match test expectations
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new ExtractionException("Player name not found in embed fields");
        }

        if (advancementTitle == null || advancementTitle.trim().isEmpty()) {
            throw new ExtractionException("Advancement title not found in embed fields");
        }

        if (advancementDescription == null || advancementDescription.trim().isEmpty()) {
            throw new ExtractionException("Advancement description not found in embed fields");
        }
    }

    /**
     * Checks if a title is a generic advancement type title that shouldn't be used
     * as the actual advancement title.
     */
    private boolean isGenericTitle(String title) {
        String lowerTitle = title.toLowerCase();
        return lowerTitle.equals("advancement made") ||
               lowerTitle.equals("goal reached") ||
               lowerTitle.equals("challenge complete") ||
               lowerTitle.equals("advancement") ||
               lowerTitle.equals("goal") ||
               lowerTitle.equals("challenge");
    }
}

package network.vonix.viscord;

import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedField;

public class AdvancementDataExtractor {
    
    private final AdvancementEmbedDetector detector;
    
    public AdvancementDataExtractor() {
        this.detector = new AdvancementEmbedDetector();
    }
    
    public AdvancementData extractFromEmbed(Embed embed) throws ExtractionException {
        if (embed == null) {
            throw new ExtractionException("Embed cannot be null");
        }
        
        try {
            if (!detector.isAdvancementEmbed(embed)) {
                throw new ExtractionException("Embed is not an advancement embed");
            }
            
            AdvancementType type;
            try {
                type = detector.getAdvancementType(embed);
            } catch (Exception typeError) {
                Viscord.LOGGER.warn("[Discord] Error determining advancement type, using NORMAL as fallback. " +
                        "Error: {} | Embed title: {}", 
                        typeError.getMessage(), embed.getTitle().orElse("none"));
                type = AdvancementType.NORMAL;
            }
            
            String playerName = null;
            String advancementTitle = null;
            String advancementDescription = null;
            
            try {
                for (EmbedField field : embed.getFields()) {
                    try {
                        String fieldName = field.getName();
                        String fieldValue = field.getValue();
                        
                        if (fieldName == null || fieldValue == null) {
                            continue;
                        }
                        
                        String normalizedName = fieldName.toLowerCase().trim();
                        String normalizedValue = fieldValue.trim();
                        
                        if (normalizedValue.isEmpty()) {
                            continue;
                        }
                        
                        if (normalizedName.contains("player") || normalizedName.contains("user") || normalizedName.contains("name")) {
                            if (playerName == null) {
                                playerName = normalizedValue;
                            }
                        }
                        else if (normalizedName.contains("advancement") || normalizedName.contains("title") || normalizedName.contains("achievement")) {
                            if (advancementTitle == null) {
                                advancementTitle = normalizedValue;
                            }
                        }
                        else if (normalizedName.contains("description") || normalizedName.contains("desc") || normalizedName.contains("details")) {
                            if (advancementDescription == null) {
                                advancementDescription = normalizedValue;
                            }
                        }
                    } catch (Exception fieldError) {
                        Viscord.LOGGER.warn("[Discord] Error processing embed field, skipping. " +
                                "Field name: {} | Error: {}", 
                                field.getName(), fieldError.getMessage());
                        continue;
                    }
                }
            } catch (Exception fieldsError) {
                throw new ExtractionException("Error processing embed fields: " + fieldsError.getMessage(), fieldsError);
            }
            
            if (advancementTitle == null) {
                try {
                    if (embed.getTitle().isPresent()) {
                        String embedTitle = embed.getTitle().get().trim();
                        if (!embedTitle.isEmpty() && !isGenericTitle(embedTitle)) {
                            advancementTitle = embedTitle;
                        }
                    }
                } catch (Exception titleError) {
                    Viscord.LOGGER.warn("[Discord] Error accessing embed title for fallback extraction: {}", 
                            titleError.getMessage());
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
                    Viscord.LOGGER.warn("[Discord] Error accessing embed description for fallback extraction: {}", 
                            descError.getMessage());
                }
            }
            
            validateExtractedData(playerName, advancementTitle, advancementDescription, embed);
            
            try {
                return new AdvancementData(playerName, advancementTitle, advancementDescription, type);
            } catch (Exception dataCreationError) {
                throw new ExtractionException("Failed to create AdvancementData object: " + dataCreationError.getMessage(), 
                        dataCreationError);
            }
            
        } catch (ExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtractionException("Unexpected error during advancement data extraction: " + e.getMessage(), e);
        }
    }
    
    private void validateExtractedData(String playerName, String advancementTitle, String advancementDescription, Embed embed) 
            throws ExtractionException {
        
        if (playerName == null || playerName.trim().isEmpty()) {
            Viscord.LOGGER.debug("[Discord] Player name validation failed.");
            throw new ExtractionException("Player name not found in embed fields");
        }
        
        if (advancementTitle == null || advancementTitle.trim().isEmpty()) {
            Viscord.LOGGER.debug("[Discord] Advancement title validation failed.");
            throw new ExtractionException("Advancement title not found in embed fields");
        }
        
        if (advancementDescription == null || advancementDescription.trim().isEmpty()) {
            Viscord.LOGGER.debug("[Discord] Advancement description validation failed.");
            throw new ExtractionException("Advancement description not found in embed fields");
        }
    }
    
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

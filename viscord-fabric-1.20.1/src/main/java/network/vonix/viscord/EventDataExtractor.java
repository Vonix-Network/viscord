package network.vonix.viscord;

import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedField;

public class EventDataExtractor {
    
    private final EventEmbedDetector detector;
    
    public EventDataExtractor() {
        this.detector = new EventEmbedDetector();
    }
    
    public EventData extractFromEmbed(Embed embed) throws ExtractionException {
        if (embed == null) {
            throw new ExtractionException("Embed cannot be null");
        }
        
        EventEmbedDetector.EventType eventType = detector.getEventType(embed);
        if (eventType == EventEmbedDetector.EventType.UNKNOWN) {
            throw new ExtractionException("Could not determine event type from embed");
        }
        
        String playerName = extractPlayerName(embed);
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new ExtractionException("Could not extract player name from embed");
        }
        
        String deathMessage = null;
        if (eventType == EventEmbedDetector.EventType.DEATH) {
            deathMessage = extractDeathMessage(embed);
        }
        
        return new EventData(playerName, eventType, deathMessage);
    }
    
    private String extractPlayerName(Embed embed) {
        for (EmbedField field : embed.getFields()) {
            String fieldName = field.getName().toLowerCase();
            if (fieldName.contains("player") || fieldName.contains("user") || fieldName.equals("name")) {
                String value = field.getValue();
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        
        if (embed.getAuthor().isPresent()) {
            String authorName = embed.getAuthor().get().getName();
            if (authorName != null && !authorName.trim().isEmpty()) {
                return authorName.trim();
            }
        }
        
        if (embed.getDescription().isPresent()) {
            String description = embed.getDescription().get();
            String extracted = extractPlayerFromDescription(description);
            if (extracted != null) {
                return extracted;
            }
        }
        
        return null;
    }
    
    private String extractPlayerFromDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }
        
        String[] words = description.split("\\s+");
        if (words.length > 0) {
            String firstWord = words[0].trim();
            if (!firstWord.equalsIgnoreCase("a") && 
                !firstWord.equalsIgnoreCase("the") &&
                !firstWord.equalsIgnoreCase("someone") &&
                !firstWord.isEmpty()) {
                return firstWord;
            }
        }
        
        return null;
    }
    
    private String extractDeathMessage(Embed embed) {
        for (EmbedField field : embed.getFields()) {
            String fieldName = field.getName().toLowerCase();
            if (fieldName.contains("message") || fieldName.contains("cause") || fieldName.contains("death")) {
                String value = field.getValue();
                if (value != null && !value.trim().isEmpty() && !value.equals("—")) {
                    return value.trim();
                }
            }
        }
        
        if (embed.getDescription().isPresent()) {
            String description = embed.getDescription().get();
            if (description != null && !description.trim().isEmpty() && 
                !description.equalsIgnoreCase("a player died")) {
                return description.trim();
            }
        }
        
        return null;
    }
}

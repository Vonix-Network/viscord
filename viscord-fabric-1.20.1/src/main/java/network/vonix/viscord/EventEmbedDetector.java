package network.vonix.viscord;

import org.javacord.api.entity.message.embed.Embed;
import java.util.Set;

public class EventEmbedDetector {
    
    public enum EventType {
        JOIN("joined", "join"),
        LEAVE("left", "leave"),
        DEATH("death", "died"),
        UNKNOWN(null, null);
        
        private final String actionVerb;
        private final String footerKeyword;
        
        EventType(String actionVerb, String footerKeyword) {
            this.actionVerb = actionVerb;
            this.footerKeyword = footerKeyword;
        }
        
        public String getActionVerb() {
            return actionVerb;
        }
        
        public String getFooterKeyword() {
            return footerKeyword;
        }
    }
    
    private static final Set<String> EVENT_FOOTER_KEYWORDS = Set.of(
        "join", "leave", "death"
    );
    
    public boolean isEventEmbed(Embed embed) {
        if (embed == null) {
            return false;
        }
        
        try {
            return hasEventFooter(embed) || hasEventTitle(embed);
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error during event embed detection: {}", e.getMessage());
            return false;
        }
    }
    
    public EventType getEventType(Embed embed) {
        if (embed == null) {
            return EventType.UNKNOWN;
        }
        
        try {
            if (embed.getFooter().isPresent()) {
                String footerText = embed.getFooter().get().getText()
                        .map(String::toLowerCase)
                        .orElse("");
                
                if (footerText.contains("join")) {
                    return EventType.JOIN;
                } else if (footerText.contains("leave")) {
                    return EventType.LEAVE;
                } else if (footerText.contains("death")) {
                    return EventType.DEATH;
                }
            }
            
            if (embed.getTitle().isPresent()) {
                String title = embed.getTitle().get().toLowerCase();
                
                if (title.contains("joined") || title.contains("join")) {
                    return EventType.JOIN;
                } else if (title.contains("left") || title.contains("leave")) {
                    return EventType.LEAVE;
                } else if (title.contains("died") || title.contains("death")) {
                    return EventType.DEATH;
                }
            }
            
            return EventType.UNKNOWN;
            
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error determining event type: {}", e.getMessage());
            return EventType.UNKNOWN;
        }
    }
    
    private boolean hasEventFooter(Embed embed) {
        try {
            if (!embed.getFooter().isPresent()) {
                return false;
            }
            
            String footerText = embed.getFooter().get().getText()
                    .map(String::toLowerCase)
                    .orElse("");
            
            return EVENT_FOOTER_KEYWORDS.stream()
                    .anyMatch(keyword -> footerText.contains(keyword));
                    
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean hasEventTitle(Embed embed) {
        try {
            if (!embed.getTitle().isPresent()) {
                return false;
            }
            
            String title = embed.getTitle().get().toLowerCase();
            String strippedTitle = title.replaceAll("[^a-zA-Z ]", "").trim();
            
            return strippedTitle.contains("player joined") ||
                   strippedTitle.contains("player left") ||
                   strippedTitle.contains("player died");
                   
        } catch (Exception e) {
            return false;
        }
    }
}

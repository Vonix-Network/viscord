package network.vonix.viscord;

import org.javacord.api.entity.message.embed.Embed;
import java.util.Set;

public class AdvancementEmbedDetector {
    
    private static final Set<String> ADVANCEMENT_FOOTER_KEYWORDS = Set.of(
        "advancement", "goal", "challenge", "task"
    );
    
    public boolean isAdvancementEmbed(Embed embed) {
        if (embed == null) {
            return false;
        }
        
        try {
            return hasAdvancementFooter(embed);
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error during advancement embed detection, treating as non-advancement embed. " +
                    "Error: {} | Embed title: {} | Fields: {}", 
                    e.getMessage(), embed.getTitle().orElse("none"), embed.getFields().size());
            return false;
        }
    }
    
    public AdvancementType getAdvancementType(Embed embed) {
        if (embed == null) {
            return AdvancementType.NORMAL;
        }
        
        try {
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
                Viscord.LOGGER.debug("[Discord] Error checking embed title for advancement type: {}", 
                        titleError.getMessage());
            }
            
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
                Viscord.LOGGER.debug("[Discord] Error checking embed footer for advancement type: {}", 
                        footerError.getMessage());
            }
            
            return AdvancementType.NORMAL;
            
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error determining advancement type, using NORMAL as fallback. " +
                    "Error: {} | Embed title: {}", 
                    e.getMessage(), embed.getTitle().orElse("none"));
            return AdvancementType.NORMAL;
        }
    }
    
    private boolean hasAdvancementFooter(Embed embed) {
        try {
            if (!embed.getFooter().isPresent()) {
                return false;
            }
            
            try {
                String footerText = embed.getFooter().get().getText()
                        .map(String::toLowerCase)
                        .orElse("");
                
                boolean hasKeyword = ADVANCEMENT_FOOTER_KEYWORDS.stream()
                        .anyMatch(keyword -> footerText.contains(keyword));
                
                if (Config.ENABLE_DEBUG_LOGGING.get() && hasKeyword) {
                    Viscord.LOGGER.debug("[Discord] Advancement embed detected by footer keywords. " +
                            "Footer: '{}'", footerText);
                }
                
                return hasKeyword;
                
            } catch (Exception footerTextError) {
                Viscord.LOGGER.warn("[Discord] Error accessing footer text for advancement detection: {}", 
                        footerTextError.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error checking embed footer for advancement keywords: {}", 
                    e.getMessage());
            return false;
        }
    }
}

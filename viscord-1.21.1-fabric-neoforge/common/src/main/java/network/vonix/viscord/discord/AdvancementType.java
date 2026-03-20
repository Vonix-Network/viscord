package network.vonix.viscord.discord;

import net.minecraft.ChatFormatting;

/**
 * Enumeration of advancement types with their display text and color mappings.
 * Used to categorize and format different types of advancement messages from Discord.
 */
public enum AdvancementType {
    /**
     * Regular advancement completion
     */
    NORMAL("Advancement Made", ChatFormatting.YELLOW),

    /**
     * Goal advancement completion
     */
    GOAL("Goal Reached", ChatFormatting.YELLOW),

    /**
     * Challenge advancement completion
     */
    CHALLENGE("Challenge Complete", ChatFormatting.LIGHT_PURPLE);

    private final String displayText;
    private final ChatFormatting color;

    /**
     * Creates an advancement type with display text and color.
     */
    AdvancementType(String displayText, ChatFormatting color) {
        this.displayText = displayText;
        this.color = color;
    }

    /**
     * Gets the display text for this advancement type.
     */
    public String getDisplayText() {
        return displayText;
    }

    /**
     * Gets the color formatting for this advancement type.
     */
    public ChatFormatting getColor() {
        return color;
    }

    /**
     * Determines the advancement type from embed title text.
     */
    public static AdvancementType fromTitle(String title) {
        if (title == null) {
            return NORMAL;
        }

        String lowerTitle = title.toLowerCase();

        if (lowerTitle.contains("challenge")) {
            return CHALLENGE;
        } else if (lowerTitle.contains("goal")) {
            return GOAL;
        } else {
            return NORMAL;
        }
    }
}

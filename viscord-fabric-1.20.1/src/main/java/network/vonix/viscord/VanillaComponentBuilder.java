package network.vonix.viscord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public class VanillaComponentBuilder {
    
    private static final ChatFormatting SERVER_PREFIX_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting BRACKET_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting PLAYER_NAME_COLOR = ChatFormatting.WHITE;
    private static final ChatFormatting CONNECTOR_COLOR = ChatFormatting.WHITE;
    
    public MutableComponent buildAdvancementMessage(AdvancementData data, String serverPrefix) {
        if (data == null) {
            throw new IllegalArgumentException("AdvancementData cannot be null");
        }
        if (serverPrefix == null) {
            throw new IllegalArgumentException("Server prefix cannot be null");
        }
        
        try {
            MutableComponent message = Component.empty();
            
            if (!serverPrefix.trim().isEmpty()) {
                message.append(createServerPrefixComponent(serverPrefix.trim()));
                message.append(Component.literal(" "));
            }
            
            message.append(Component.literal(data.getPlayerName())
                    .withStyle(PLAYER_NAME_COLOR));
            
            message.append(Component.literal(" has made the advancement ")
                    .withStyle(CONNECTOR_COLOR));
            
            MutableComponent advancementComponent = Component.literal("[" + data.getAdvancementTitle() + "]")
                    .withStyle(Style.EMPTY
                            .withColor(data.getType().getColor())
                            .withHoverEvent(createHoverText(data.getAdvancementDescription())));
            
            message.append(advancementComponent);
            
            return message;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to build advancement message component: " + e.getMessage(), e);
        }
    }
    
    private MutableComponent createServerPrefixComponent(String serverPrefix) {
        try {
            MutableComponent prefixComponent = Component.empty();
            
            prefixComponent.append(Component.literal("[")
                    .withStyle(BRACKET_COLOR));
            
            prefixComponent.append(Component.literal(serverPrefix)
                    .withStyle(SERVER_PREFIX_COLOR));
            
            prefixComponent.append(Component.literal("]")
                    .withStyle(BRACKET_COLOR));
            
            return prefixComponent;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create server prefix component: " + e.getMessage(), e);
        }
    }
    
    private HoverEvent createHoverText(String description) {
        try {
            MutableComponent hoverComponent = Component.literal(description)
                    .withStyle(ChatFormatting.WHITE);
            
            return new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create hover text: " + e.getMessage(), e);
        }
    }
    
    public MutableComponent buildAdvancementMessage(AdvancementData data) {
        return buildAdvancementMessage(data, "");
    }
    
    public MutableComponent buildEventMessage(EventData data, String serverPrefix) {
        if (data == null) {
            throw new IllegalArgumentException("EventData cannot be null");
        }
        if (serverPrefix == null) {
            throw new IllegalArgumentException("Server prefix cannot be null");
        }
        
        try {
            MutableComponent message = Component.empty();
            
            if (!serverPrefix.trim().isEmpty()) {
                message.append(createServerPrefixComponent(serverPrefix.trim()));
                message.append(Component.literal(" "));
            }
            
            ChatFormatting playerColor = getEventPlayerColor(data.getEventType());
            message.append(Component.literal(data.getPlayerName())
                    .withStyle(playerColor));
            
            message.append(Component.literal(" " + data.getActionString())
                    .withStyle(ChatFormatting.YELLOW));
            
            return message;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to build event message component: " + e.getMessage(), e);
        }
    }
    
    private ChatFormatting getEventPlayerColor(EventEmbedDetector.EventType eventType) {
        switch (eventType) {
            case JOIN:
                return ChatFormatting.GREEN;
            case LEAVE:
                return ChatFormatting.GRAY;
            case DEATH:
                return ChatFormatting.RED;
            default:
                return ChatFormatting.WHITE;
        }
    }
    
    public MutableComponent createEventFallbackComponent(String playerName, String action, String serverPrefix) {
        try {
            MutableComponent fallback = Component.empty();
            
            if (serverPrefix != null && !serverPrefix.trim().isEmpty()) {
                try {
                    fallback.append(createServerPrefixComponent(serverPrefix.trim()));
                    fallback.append(Component.literal(" "));
                } catch (Exception prefixError) {
                }
            }
            
            String safePlayerName = (playerName != null && !playerName.trim().isEmpty()) 
                ? playerName.trim() 
                : "Someone";
            String safeAction = (action != null && !action.trim().isEmpty())
                ? action.trim()
                : "performed an action";
            
            fallback.append(Component.literal(safePlayerName + " " + safeAction)
                    .withStyle(ChatFormatting.YELLOW));
            
            return fallback;
            
        } catch (Exception e) {
            return Component.literal("An event occurred but could not be displayed")
                    .withStyle(ChatFormatting.YELLOW);
        }
    }
    
    public MutableComponent createFallbackComponent(String playerName, String advancementTitle, String serverPrefix) {
        try {
            MutableComponent fallback = Component.empty();
            
            if (serverPrefix != null && !serverPrefix.trim().isEmpty()) {
                try {
                    fallback.append(createServerPrefixComponent(serverPrefix.trim()));
                    fallback.append(Component.literal(" "));
                } catch (Exception prefixError) {
                }
            }
            
            String safePlayerName = (playerName != null && !playerName.trim().isEmpty()) 
                ? playerName.trim() 
                : "Someone";
            
            fallback.append(Component.literal(safePlayerName + " has made an advancement")
                    .withStyle(ChatFormatting.YELLOW));
            
            if (advancementTitle != null && !advancementTitle.trim().isEmpty()) {
                try {
                    fallback.append(Component.literal(": " + advancementTitle.trim())
                            .withStyle(ChatFormatting.WHITE));
                } catch (Exception titleError) {
                }
            }
            
            return fallback;
            
        } catch (Exception e) {
            try {
                return Component.literal("⚠ An advancement was earned but could not be displayed")
                        .withStyle(ChatFormatting.YELLOW);
            } catch (Exception ultimateError) {
                return Component.empty();
            }
        }
    }
}

package com.pixelservices.flash.models;

/**
 * Enum representing the different types of handlers.
 */
public enum HandlerType {
    STANDARD("blue", "⚙️"),
    SIMPLE("green", "📦"),
    STATIC("gray", "📄"),
    WEBSOCKET("purple", "🔗"),
    SERVER_SENT_EVENTS("pink", "📡"),
    REDIRECT("orange", "➡️"),
    INTERNAL("red", "🔒"),
    DYNAMIC("yellow", "🔍");

    private final String textColor;
    private final String emoji;

    /**
     * Constructs a HandlerType with a given text color and emoji.
     *
     * @param textColor the color of the text representation of the handler type
     * @param emoji     the emoji representation of the handler type
     */
    HandlerType(String textColor, String emoji) {
        this.textColor = textColor;
        this.emoji = emoji;
    }

    /**
     * Gets the text color of the handler type.
     *
     * @return the text color
     */
    public String getTextColor() {
        return textColor;
    }

    /**
     * Gets the emoji representation of the handler type.
     *
     * @return the emoji
     */
    public String getEmoji() {
        return emoji;
    }
}

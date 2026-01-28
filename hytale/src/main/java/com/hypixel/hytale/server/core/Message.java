package com.hypixel.hytale.server.core;

import java.awt.Color;

/**
 * Stub for Hytale's Message class.
 * Used for formatted messages.
 */
public class Message {

    private String text;
    private Color color;

    private Message(String text) {
        this.text = text;
    }

    public static Message raw(String text) {
        return new Message(text);
    }

    public static Message join(Message... messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append(m.text);
        }
        return new Message(sb.toString());
    }

    public Message color(Color color) {
        this.color = color;
        return this;
    }

    @Override
    public String toString() {
        return text;
    }
}

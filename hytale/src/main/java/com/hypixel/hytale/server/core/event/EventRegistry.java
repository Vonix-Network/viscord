package com.hypixel.hytale.server.core.event;

import java.util.function.Consumer;

/**
 * Stub for Hytale's EventRegistry.
 * Correct method: registerGlobal(EventClass.class, handler)
 */
public class EventRegistry {

    /**
     * Register a global event handler.
     */
    public <T> void registerGlobal(Class<T> eventClass, Consumer<T> handler) {
        // Stub - real implementation provided by HytaleServer.jar
    }

    public void unregisterAll() {
    }
}

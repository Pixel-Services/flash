package com.pixelservices.flash.models;

import java.util.EnumMap;
import java.util.Map;

public class FlashConfiguration {
    private final Map<HandlerType, Boolean> loggingPreferences;

    public FlashConfiguration() {
        loggingPreferences = new EnumMap<>(HandlerType.class);
        for (HandlerType type : HandlerType.values()) {
            loggingPreferences.put(type, true);
        }
    }

    public FlashConfiguration setLoggingPreference(HandlerType type, boolean shouldLog) {
        loggingPreferences.put(type, shouldLog);
        return this;
    }

    public boolean shouldLog(HandlerType type) {
        return loggingPreferences.getOrDefault(type, true);
    }
}

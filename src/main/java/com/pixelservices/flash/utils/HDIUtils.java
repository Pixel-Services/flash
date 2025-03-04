package com.pixelservices.flash.utils;

import com.pixelservices.flash.components.http.RequestHandler;

import java.util.ArrayList;
import java.util.List;

public class HDIUtils {
    /**
     * Detects the chain of Handler Default Implementations (HDIs) that a given handler extends.
     * The list includes all superclasses up to (but excluding) RequestHandler.
     *
     * @param clazz The handler class to analyze.
     * @return A list of HDI classes, ordered from the closest HDI to the furthest one.
     */
    public static List<Class<? extends RequestHandler>> detectHDIChain(Class<? extends RequestHandler> clazz) {
        List<Class<? extends RequestHandler>> hdiChain = new ArrayList<>();
        Class<?> current = clazz.getSuperclass(); // Start with the immediate superclass

        while (current != null && RequestHandler.class.isAssignableFrom(current) && current != RequestHandler.class) {
            hdiChain.add((Class<? extends RequestHandler>) current);
            current = current.getSuperclass();
        }

        return hdiChain;
    }
}

package com.pixelservices.flash.components;

import com.pixelservices.flash.models.HttpMethod;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

public class RouteEntry {
    private final HttpMethod method;
    private final String path;
    private final RequestHandler handler;
    private final boolean isParameterized;
    private final boolean isDynamic;
    private final RoutePattern routePattern;

    /**
     * If the route ends with "/*", it is treated as a dynamic route.
     * Otherwise, if it contains a colon, it's considered parameterized.
     * Literal routes match exactly.
     */
    public RouteEntry(HttpMethod method, String path, RequestHandler handler) {
        this.method = method;
        this.path = path;
        this.handler = handler;
        if (path.endsWith("/*")) {
            this.isDynamic = true;
            this.isParameterized = false;
            this.routePattern = new RoutePattern(convertDynamicPathToRegex(path));
        } else {
            this.isDynamic = false;
            this.isParameterized = path.contains(":");
            this.routePattern = isParameterized ? new RoutePattern(path) : null;
        }
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public RequestHandler getHandler() {
        return handler;
    }

    public boolean isParameterized() {
        return isParameterized;
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    /**
     * For literal routes, the key is simply "METHOD:/literal/path".
     */
    public String getLiteralKey() {
        return method.name() + ":" + path;
    }

    /**
     * Attempts to match the given requestPath.
     * - For literal routes, only an exact match returns an empty map.
     * - For parameterized or dynamic routes, the precompiled pattern is used.
     * Returns an unmodifiable map of parameters if it matches, or null if it doesn’t.
     */
    public Map<String, String> match(String requestPath) {
        if (!isParameterized && !isDynamic) {
            return path.equals(requestPath) ? Collections.emptyMap() : null;
        }
        return routePattern.extractParameters(requestPath);
    }

    /**
     * Converts a dynamic route (ending in "/*") to a regex.
     * For example, "/myendpoint/*" becomes "^/myendpoint(/.*)?$".
     */
    private static String convertDynamicPathToRegex(String dynamicPath) {
        String prefix = dynamicPath.substring(0, dynamicPath.length() - 2);
        return "^" + Pattern.quote(prefix) + "(/.*)?$";
    }
}




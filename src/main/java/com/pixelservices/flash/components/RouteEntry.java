package com.pixelservices.flash.components;

import com.pixelservices.flash.models.HttpMethod;

import java.util.Collections;
import java.util.Map;

public class RouteEntry {
    private final HttpMethod method;
    private final String path;
    private final RequestHandler handler;
    private final boolean isParameterized;
    private final RoutePattern routePattern;

    public RouteEntry(HttpMethod method, String path, RequestHandler handler) {
        this.method = method;
        this.path = path;
        this.handler = handler;
        this.isParameterized = path.contains(":");
        this.routePattern = isParameterized ? new RoutePattern(path) : null;
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

    /**
     * For literal routes, the key is simply "METHOD:/literal/path".
     */
    public String getLiteralKey() {
        return method.name() + ":" + path;
    }

    /**
     * If parameterized, attempts to match a request path.
     * Returns an unmodifiable map of parameters if it matches, otherwise null.
     */
    public Map<String, String> match(String requestPath) {
        if (!isParameterized) {
            return path.equals(requestPath) ? Collections.emptyMap() : null;
        }
        return routePattern.extractParameters(requestPath);
    }
}



package com.pixelservices.flash.components.http.routing.models;

import com.pixelservices.flash.components.http.HandlerType;
import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.components.http.pool.HandlerPool;
import com.pixelservices.flash.components.http.pool.HandlerPoolManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

public class RouteEntry {
    private final HttpMethod method;
    private final String path;
    private final HandlerPool<? extends RequestHandler> handlerPool; // Changed from RequestHandler to HandlerPool
    private final boolean isParameterized;
    private final boolean isDynamic;
    private final RoutePattern routePattern;
    private final String[] pathSegments;
    private final HandlerType handlerType;
    private final Class<? extends RequestHandler> handlerClass;

    /**
     * Constructor for class-based handlers that will use pooling
     */
    public RouteEntry(HttpMethod method, String path, Class<? extends RequestHandler> handlerClass, 
                      HandlerType handlerType, HandlerPoolManager poolManager) {
        this.method = method;
        this.path = path;
        this.handlerType = handlerType;
        this.handlerClass = handlerClass;
        
        // Get or create a handler pool for this handler class
        this.handlerPool = poolManager.getOrCreatePool(handlerClass);
        
        if (path.endsWith("/*")) {
            this.isDynamic = true;
            this.isParameterized = false;
            this.routePattern = new RoutePattern(convertDynamicPathToRegex(path));
        } else {
            this.isDynamic = false;
            this.isParameterized = path.contains(":");
            this.routePattern = isParameterized ? new RoutePattern(path) : null;
        }
        // Populate pathSegments in the constructor
        this.pathSegments = parsePathSegments(path);
    }
    
    /**
     * Constructor for lambda-based handlers (using a special pool)
     */
    public RouteEntry(HttpMethod method, String path, RequestHandler handler, 
                      HandlerType handlerType, HandlerPoolManager poolManager) {
        this.method = method;
        this.path = path;
        this.handlerType = handlerType;
        this.handlerClass = handler.getClass();
        
        // Create a special single-instance pool for lambda handlers
        this.handlerPool = new SingleInstanceHandlerPool<>(handler);
        
        if (path.endsWith("/*")) {
            this.isDynamic = true;
            this.isParameterized = false;
            this.routePattern = new RoutePattern(convertDynamicPathToRegex(path));
        } else {
            this.isDynamic = false;
            this.isParameterized = path.contains(":");
            this.routePattern = isParameterized ? new RoutePattern(path) : null;
        }
        // Populate pathSegments in the constructor
        this.pathSegments = parsePathSegments(path);
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public HandlerPool<? extends RequestHandler> getHandlerPool() {
        return handlerPool;
    }
    
    public HandlerType getHandlerType() {
        return handlerType;
    }
    
    public Class<? extends RequestHandler> getHandlerClass() {
        return handlerClass;
    }

    public boolean isParameterized() {
        return isParameterized;
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    /**
     * Returns the path segments for Trie traversal as String array.
     */
    public String[] getPathSegments() {
        return pathSegments;
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
     * Returns an unmodifiable map of parameters if it matches, or null if it doesn't.
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

    /**
     * Parses the path into segments, splitting by '/'.
     * Removes leading/trailing slashes and empty segments.
     * Returns String array.
     */
    private String[] parsePathSegments(String routePath) {
        String path = routePath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/*")) {
            path = path.substring(0, path.length() - 2); // Remove /* for dynamic routes
        } else if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.isEmpty()) {
            return new String[0]; // Return empty String array
        }

        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isEmpty()) // Filter out empty segments
                .toArray(String[]::new); // Convert Stream<String> to String[]
    }
    
    /**
     * Special HandlerPool implementation for single-instance handlers (like lambdas)
     */
    private static class SingleInstanceHandlerPool<T extends RequestHandler> extends HandlerPool<T> {
        private final T singleHandler;
        
        public SingleInstanceHandlerPool(T handler) {
            super((Class<T>)handler.getClass(), 1, 1, 1);
            this.singleHandler = handler;
        }
        
        @Override
        public T acquire(Request request, Response response) {
            singleHandler.setRequestResponse(request, response);
            return singleHandler;
        }
        
        @Override
        public void release(T handler) {
            // No need to return to pool for single instance
            handler.setRequestResponse(null, null);
        }
    }
}
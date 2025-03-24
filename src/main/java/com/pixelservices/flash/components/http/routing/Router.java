package com.pixelservices.flash.components.http.routing;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.components.http.HandlerType;
import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.components.http.pool.HandlerPool;
import com.pixelservices.flash.components.http.pool.HandlerPoolManager;
import com.pixelservices.flash.components.http.routing.models.RouteInfo;

import java.lang.reflect.Constructor;

public class Router {
    private final String basePath;
    private final FlashServer server;
    private final HandlerPoolManager poolManager;

    /**
     * Constructs a Router with a given base path and FlashServer instance.
     *
     * @param basePath the base path for all routes registered with this router
     * @param server   the FlashServer instance to register routes with
     */
    public Router(String basePath, FlashServer server) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.server = server;
        this.poolManager = server.getHandlerPoolManager();
    }
    
    /**
     * Constructs a Router with a given base path, FlashServer instance, and HandlerPoolManager.
     */
    public Router(String basePath, FlashServer server, HandlerPoolManager poolManager) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.server = server;
        this.poolManager = poolManager;
    }

    /**
     * Registers a request handler class with the server.
     */
    public <T extends RequestHandler> Router register(Class<T> handlerClass) {
        String endpoint = getEndpoint(handlerClass);
        HttpMethod method = getMethod(handlerClass);
        String fullPath = basePath + endpoint;
    
        // Get a properly typed handler pool
        HandlerPool<T> handlerPool = poolManager.getOrCreatePool(handlerClass);
        
        // Acquire an instance with the correct type
        T handlerInstance = handlerPool.acquire(null, null);
        
        server.registerRoute(method, fullPath, handlerInstance);
        
        // Now we can safely release it with the correct type
        handlerPool.release(handlerInstance);
        
        return this;
    }

    /**
     * Retrieves the endpoint for the given handler class by inspecting its @RouteInfo annotation.
     */
    private String getEndpoint(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).endpoint();
        }
        throw new IllegalArgumentException("No @RouteInfo annotation found on " + handlerClass.getName());
    }

    /**
     * Retrieves the HTTP method for the given handler class by inspecting its @RouteInfo annotation.
     */
    private HttpMethod getMethod(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).method();
        }
        throw new IllegalArgumentException("No @RouteInfo annotation found on " + handlerClass.getName());
    }
}

package com.pixelservices.components;

import com.pixelservices.lifecycle.Request;
import com.pixelservices.lifecycle.Response;
import com.pixelservices.models.HttpMethod;
import com.pixelservices.models.RouteInfo;

import java.lang.reflect.Constructor;

/**
 * Router provides a mechanism to register request handlers to specific routes in a FlashServer instance.
 */
public class Router {
    private final String basePath;
    private final FlashServer server;

    /**
     * Constructs a Router with a given base path and FlashServer instance.
     *
     * @param basePath the base path for all routes registered with this router
     * @param server   the FlashServer instance to register routes with
     */
    public Router(String basePath, FlashServer server) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.server = server;
    }

    /**
     * Registers a request handler class with the server.
     *
     * @param handlerClass the class of the request handler to register
     * @return the current Router instance for method chaining
     * @throws RuntimeException if handler instantiation or registration fails
     */
    public Router register(Class<? extends RequestHandler> handlerClass) {
        String endpoint = getEndpoint(handlerClass);
        HttpMethod method = getMethod(handlerClass);
        String fullPath = basePath + endpoint;

        RequestHandler handlerInstance = createHandlerInstance(handlerClass);
        server.registerRoute(method, fullPath, handlerInstance); // Register in FlashServer
        return this;
    }

    /**
     * Creates an instance of the given request handler class.
     *
     * @param handlerClass the class of the request handler
     * @return an instance of the request handler
     * @throws RuntimeException if instantiation fails
     */
    private RequestHandler createHandlerInstance(Class<? extends RequestHandler> handlerClass) {
        try {
            Constructor<? extends RequestHandler> constructor = handlerClass.getConstructor(Request.class, Response.class);
            return constructor.newInstance(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create handler instance for " + handlerClass.getName(), e);
        }
    }

    /**
     * Retrieves the endpoint for the given handler class by inspecting its @RouteInfo annotation.
     *
     * @param handlerClass the class of the request handler
     * @return the endpoint specified in the @RouteInfo annotation
     * @throws IllegalArgumentException if the @RouteInfo annotation is missing
     */
    private String getEndpoint(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).endpoint();
        }
        throw new IllegalArgumentException("No @RouteInfo annotation found on " + handlerClass.getName());
    }

    /**
     * Retrieves the HTTP method for the given handler class by inspecting its @RouteInfo annotation.
     *
     * @param handlerClass the class of the request handler
     * @return the HTTP method specified in the @RouteInfo annotation
     * @throws IllegalArgumentException if the @RouteInfo annotation is missing
     */
    private HttpMethod getMethod(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).method();
        }
        throw new IllegalArgumentException("No @RouteInfo annotation found on " + handlerClass.getName());
    }
}



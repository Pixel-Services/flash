package flash.route;

import flash.*;
import flash.models.HandlerSpecification;
import flash.models.RequestHandler;
import flash.models.RequestHandlerInterceptor;
import flash.models.RouteInfo;
import flash.swagger.FlashSwaggerGenerator;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class RouteController {
    private final FlashServer server;
    private final String base;
    private final Map<HttpMethod, BiConsumer<String, Route>> methodMap;
    private final List<RequestHandler> handlers = new ArrayList<>();
    private final Map<Class<? extends RequestHandler>, RequestHandler> handlerInstances = new HashMap<>();
    private final List<RequestHandlerInterceptor> routeInterceptors = new ArrayList<>();
    private final Map<Class<? extends RequestHandler>, List<RequestHandlerInterceptor>> handlerInterceptors = new HashMap<>();

    public RouteController(String base, FlashServer server) {
        this.server = server;
        this.base = base;

        server.exception(IllegalArgumentException.class, (exception, req, res) -> {
            res.status(400);
            res.body(exception.getMessage());
        });

        methodMap = Map.of(
            HttpMethod.GET, server::get,
            HttpMethod.POST, server::post,
            HttpMethod.PUT, server::put,
            HttpMethod.PATCH, server::patch,
            HttpMethod.DELETE, server::delete,
            HttpMethod.HEAD, server::head,
            HttpMethod.OPTIONS, server::options
        );
    }

    public RouteController register(Class<? extends RequestHandler> handlerClass) {
        return register(handlerClass, new ArrayList<>());
    }

    public RouteController register(Class<? extends RequestHandler> handlerClass, List<RequestHandlerInterceptor> interceptors) {
        if (handlerInstances.containsKey(handlerClass)) {
            throw new IllegalArgumentException("Handler for class " + handlerClass.getName() + " is already registered.");
        }

        RequestHandler handlerInstance = createHandlerInstance(handlerClass);
        handlerInstances.put(handlerClass, handlerInstance);
        handlers.add(handlerInstance);
        handlerInterceptors.put(handlerClass, interceptors);

        String endpoint = base + getEndpoint(handlerClass);
        HttpMethod method = getMethod(handlerClass);

        BiConsumer<String, Route> routeRegistrar = methodMap.get(method);
        if (routeRegistrar == null) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        routeRegistrar.accept(endpoint, (req, res) -> {
            // Execute route-level interceptors
            for (RequestHandlerInterceptor interceptor : routeInterceptors) {
                if (!interceptor.preHandle(req, res)) {
                    return res.body();
                }
            }

            // Execute handler-level interceptors
            for (RequestHandlerInterceptor interceptor : handlerInterceptors.get(handlerClass)) {
                if (!interceptor.preHandle(req, res)) {
                    return res.body();
                }
            }

            // Update req, res for the handler
            handlerInstance.setRequestResponse(req, res);

            return handlerInstance.handle();
        });

        HandlerSpecification specification = new HandlerSpecification(
            handlerInstance,
            endpoint,
            method,
            getEnforceNonNullBody(handlerClass)
        );

        handlerInstance.setSpecification(specification);

        registerUnsupportedMethods(endpoint, method);

        return this;
    }

    public RouteController addInterceptor(RequestHandlerInterceptor interceptor) {
        routeInterceptors.add(interceptor);
        return this;
    }

    private RequestHandler createHandlerInstance(Class<? extends RequestHandler> handlerClass) {
        try {
            Constructor<? extends RequestHandler> constructor = handlerClass.getConstructor(Request.class, Response.class);
            return constructor.newInstance(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create handler instance", e);
        }
    }

    private String getEndpoint(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).endpoint();
        }
        throw new RuntimeException("No @RouteInfo annotation found on " + handlerClass.getName());
    }

    private boolean getEnforceNonNullBody(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).enforceNonNullBody();
        }
        throw new RuntimeException("No @RouteInfo annotation found on " + handlerClass.getName());
    }

    private HttpMethod getMethod(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).method();
        }
        throw new RuntimeException("No @RouteInfo annotation found on " + handlerClass.getName());
    }

    private void registerUnsupportedMethods(String endpoint, HttpMethod correctMethod) {
        methodMap.forEach((method, registerMethod) -> {
            if (method != correctMethod) {
                registerMethod.accept(endpoint, (req, res) -> {
                    res.status(400);
                    return "Error: wrong HTTP method '" + method + "' for " + endpoint + " : Expecting '" + correctMethod + "'";
                });
            }
        });
    }

    public List<RequestHandler> getHandlers() {
        return handlers;
    }
}

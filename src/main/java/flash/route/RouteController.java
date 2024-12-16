package flash.route;

import flash.*;
import flash.models.RequestHandler;
import flash.models.RequestMethod;
import flash.models.RouteInfo;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static flash.FlashServerHelper.exception;

public class RouteController {
    private final String base;
    private final List<RequestHandler> handlers = new ArrayList<>();

    private final Map<RequestMethod, BiConsumer<String, Route>> methodMap = Map.of(
        RequestMethod.GET, FlashServerHelper::get,
        RequestMethod.POST, FlashServerHelper::post,
        RequestMethod.PUT, FlashServerHelper::put,
        RequestMethod.PATCH, FlashServerHelper::patch,
        RequestMethod.DELETE, FlashServerHelper::delete,
        RequestMethod.HEAD, FlashServerHelper::head,
        RequestMethod.OPTIONS, FlashServerHelper::options
    );

    public RouteController(String base) {
        this.base = base;
        exception(IllegalArgumentException.class, (exception, req, res) -> {
            res.status(400);
            res.body(exception.getMessage());
        });
    }

    public RouteController register(Class<? extends RequestHandler> handlerClass) {
        if (handlers.stream().anyMatch(handler -> handler.getClass().equals(handlerClass))) {
            throw new IllegalArgumentException("Handler for class " + handlerClass.getName() + " is already registered.");
        }

        String endpoint = base + "/" + getEndpoint(handlerClass);
        RequestMethod method = getMethod(handlerClass);

        BiConsumer<String, Route> routeRegistrar = methodMap.get(method);
        if (routeRegistrar == null) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        routeRegistrar.accept(endpoint, (req, res) -> createHandlerInstance(handlerClass, req, res).handle());

        registerUnsupportedMethods(endpoint, method);

        handlers.add(createHandlerInstance(handlerClass, null, null));
        return this;
    }


    private RequestHandler createHandlerInstance(Class<? extends RequestHandler> handlerClass, Request req, Response res) {
        try {
            Constructor<? extends RequestHandler> constructor = handlerClass.getConstructor(Request.class, Response.class);
            return constructor.newInstance(req, res);
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

    private RequestMethod getMethod(Class<? extends RequestHandler> handlerClass) {
        if (handlerClass.isAnnotationPresent(RouteInfo.class)) {
            return handlerClass.getAnnotation(RouteInfo.class).method();
        }
        throw new RuntimeException("No @RouteInfo annotation found on " + handlerClass.getName());
    }

    private void registerUnsupportedMethods(String endpoint, RequestMethod correctMethod) {
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

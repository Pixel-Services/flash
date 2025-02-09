package com.pixelservices.components;

import com.pixelservices.components.staticfileserver.StaticFileServer;
import com.pixelservices.components.staticfileserver.StaticFileServerConfiguration;
import com.pixelservices.exceptions.RequestExceptionHandler;
import com.pixelservices.exceptions.UnmatchedHandlerException;
import com.pixelservices.lifecycle.Request;
import com.pixelservices.lifecycle.Response;
import com.pixelservices.models.HandlerSpecification;
import com.pixelservices.models.HandlerType;
import com.pixelservices.models.HttpMethod;
import com.pixelservices.models.SimpleHandler;
import com.pixelservices.swagger.OpenAPIConfiguration;
import com.pixelservices.swagger.OpenAPISchemaGenerator;
import com.pixelservices.swagger.OpenAPIUITemplate;
import com.pixelservices.utils.PrettyLogger;
import com.pixelservices.utils.RouteParameterParser;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * FlashServer is a lightweight and asynchronous HTTP server designed for handling requests
 * using a thread pool and a route-based approach.
 */
public class FlashServer {
    private final ConcurrentMap<String, RequestHandler> routeHandlers;
    private final ConcurrentMap<String, HandlerType> handlerTypes;
    private final Map<String, BiConsumer<Request, Response>> routes = new HashMap<>();
    private final Map<String, RouteParameterParser> routeParsers = new HashMap<>();
    private final int port;
    private AsynchronousServerSocketChannel serverSocketChannel;
    private final FlashConfiguration config;
    private final StaticFileServer staticFileServer;

    /**
     * Creates a new FlashServer instance with the specified port and configuration.
     *
     * @param port   the port to bind the server to
     * @param config the server configuration
     */
    public FlashServer(int port, FlashConfiguration config) {
        this.routeHandlers = new ConcurrentHashMap<>();
        this.handlerTypes = new ConcurrentHashMap<>();
        this.port = port;
        int threadCount = Runtime.getRuntime().availableProcessors();
        //this.threadPool = Executors.newFixedThreadPool(threadCount);
        this.staticFileServer = new StaticFileServer(this);
        this.config = config;
    }

    /**
     * Creates a new FlashServer instance with the specified port.
     *
     * @param port the port to bind the server to
     */
    public FlashServer(int port) {
        this(port, new FlashConfiguration());
    }

    /**
     * Starts the FlashServer, binding it to the specified port and handling incoming requests.
     */
    public void start() {
        long startTime = System.currentTimeMillis();
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open()
                    .bind(new InetSocketAddress(port));

            acceptNextConnection();

            long elapsedTime = System.currentTimeMillis() - startTime;

            String[] flashPixelArt = {
                    "&#reset",
                    "      *      ",
                    "     **      ",
                    "    ***       &#reset Started &#80EF80successfully&#reset on port &#FF746C" + port + "&#reset",
                    "   *******    &#reset Startup time: &#FF746C" + elapsedTime + "&#reset ms",
                    "      ***     &#reset Serving &#FF746C" + routeHandlers.size() + "&#reset handlers",
                    "      **     ",
                    "      *      ",
                    "&#reset"
            };

            for (String line : flashPixelArt) {
                PrettyLogger.log("&#FFEE8C" + line);
            }

            // Keep the server alive
            Thread.sleep(Long.MAX_VALUE);
        } catch (IOException | InterruptedException e) {
            PrettyLogger.logWithEmoji("Error starting server: " + e.getMessage(), "❌");
        }
    }

    /**
     * Accepts the next incoming connection asynchronously.
     */
    private void acceptNextConnection() {
        serverSocketChannel.accept(null, new CompletionHandler<>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
                acceptNextConnection();
                Thread.startVirtualThread(() -> handleClient(clientChannel));
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                PrettyLogger.logWithEmoji("Failed to accept connection: " + exc.getMessage(), "⚠️");
                acceptNextConnection();
            }
        });
    }

    /**
     * Registers a route with the server.
     *
     * @param method      the HTTP method for the route (e.g., GET, POST)
     * @param fullPath    the full path of the route
     * @param handler     the handler for the route
     * @param handlerType the type of handler (e.g., STANDARD, SIMPLE)
     * @throws IllegalStateException if a route with the same method and path is already registered
     */
    private void registerRoute(HttpMethod method, String fullPath, RequestHandler handler, HandlerType handlerType) {
        String routeKey = createRouteKey(method, fullPath);
        if (routeHandlers.putIfAbsent(routeKey, handler) != null) {
            throw new IllegalStateException("Duplicate route registered: " + routeKey);
        }

        handlerTypes.put(routeKey, handlerType);
        handler.setHandlerType(handlerType);

        if (config.shouldLog(handlerType)) {
            PrettyLogger.logWithEmoji(handlerType.name() + " Route registered: [" + method + "] " + fullPath, handlerType.getEmoji());
        }

        handler.setSpecification(new HandlerSpecification(handler, fullPath, method, handler.isEnforcedNonNullBody()));
    }

    /**
     * Registers a standard route with the server.
     *
     * @param method   the HTTP method for the route (e.g., GET, POST)
     * @param fullPath the full path of the route
     * @param handler  the handler for the route
     * @throws IllegalStateException if a route with the same method and path is already registered
     */
    public void registerRoute(HttpMethod method, String fullPath, RequestHandler handler) {
        registerRoute(method, fullPath, handler, HandlerType.STANDARD);
    }

    /**
     * Registers a simple route with the server.
     *
     * @param method   the HTTP method for the route (e.g., GET, POST)
     * @param fullPath the full path of the route
     * @param handler  the simple handler for the route
     * @throws IllegalStateException if a route with the same method and path is already registered
     */
    public void registerRoute(HttpMethod method, String fullPath, SimpleHandler handler) {
        registerRoute(method, fullPath, wrapSimpleHandler(handler), HandlerType.SIMPLE);
    }

    /**
     * Registers a simple route with the server and specifies the handler type.
     *
     * @param method      the HTTP method for the route (e.g., GET, POST)
     * @param fullPath    the full path of the route
     * @param handler     the simple handler for the route
     * @param handlerType the type of handler
     * @throws IllegalStateException if a route with the same method and path is already registered
     */
    public void registerRoute(HttpMethod method, String fullPath, SimpleHandler handler, HandlerType handlerType) {
        registerRoute(method, fullPath, wrapSimpleHandler(handler), handlerType);
    }

    /**
     * Unregisters a route from the server.
     *
     * @param method   the HTTP method for the route (e.g., GET, POST)
     * @param fullPath the full path of the route
     */
    public void unregisterRoute(HttpMethod method, String fullPath) {
        String routeKey = createRouteKey(method, fullPath);
        if (routeHandlers.remove(routeKey) != null) {
            handlerTypes.remove(routeKey);
            PrettyLogger.logWithEmoji("Route unregistered: [" + method + "] " + fullPath, "❌");
        } else {
            PrettyLogger.logWithEmoji("Route not found: [" + method + "] " + fullPath, "⚠️");
        }
    }

    /**
     * Registers a route that redirects from one path to another.
     *
     * @param fromPath the path to redirect from
     * @param toPath   the path to redirect to
     * @param method   the HTTP method for the route (e.g., GET, POST)
     */
    public void redirect(String fromPath, String toPath, HttpMethod method) {
        registerRoute(method, fromPath, (req, res) -> {
            res.status(302);
            res.header("Location", toPath);
            return "Redirecting to " + toPath;
        }, HandlerType.REDIRECT);
    }

    /**
     * Registers a route that redirects from one path to another.
     *
     * @param fromPath the path to redirect from
     * @param toPath   the path to redirect to
     */
    public void redirect(String fromPath, String toPath) {
        redirect(fromPath, toPath, HttpMethod.GET);
    }

    /**
     * Wraps a SimpleHandler in a RequestHandler.
     *
     * @param handler the simple handler to wrap
     * @return the wrapped request handler
     */
    private RequestHandler wrapSimpleHandler(SimpleHandler handler) {
        return new RequestHandler(null, null) {
            @Override
            public Object handle() {
                return handler.handle(this.req, this.res);
            }
        };
    }

    /**
     * Handles an incoming client request.
     *
     * @param clientChannel the client socket channel
     */
    private void handleClient(AsynchronousSocketChannel clientChannel) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
        clientChannel.read(buffer, buffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer buf) {
                try {
                    Request request = new Request(buf, (InetSocketAddress) clientChannel.getRemoteAddress());
                    Response response = new Response();
                    String routeKey = createRouteKey(request.method(), request.path());
                    RequestHandler handler = routeHandlers.get(routeKey.split("\\?")[0]);
                    if (handler == null) {
                        throw new UnmatchedHandlerException("No handler found for " + request.method() + " " + request.path());
                    }
                    handler.setRequestResponse(request, response);
                    validateHandlerResources(handler);
                    Object responseBody = handler.handle();
                    response.body(convertToResponseBody(responseBody));
                    sendResponse(response, clientChannel);
                } catch (Exception e) {
                    new RequestExceptionHandler(clientChannel, e).handle();
                } finally {
                    closeSocket(clientChannel);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                new RequestExceptionHandler(clientChannel, new Exception(exc)).handle();
                closeSocket(clientChannel);
            }
        });

    }


    /**
     * Sends a response to the client.
     *
     * @param response      the response to send
     * @param clientChannel the client socket channel
     */
    private void sendResponse(Response response, AsynchronousSocketChannel clientChannel) {
        synchronized (response) {
            response.finalizeResponse();
            ByteBuffer responseBuffer = response.getSerialized();
            clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer bytesWritten, ByteBuffer buf) {
                    if (buf.hasRemaining()) {
                        clientChannel.write(buf, buf, this);
                    } else {
                        closeSocket(clientChannel);
                    }
                }
                @Override
                public void failed(Throwable exc, ByteBuffer buf) {
                    PrettyLogger.logWithEmoji("Error sending response: " + exc.getMessage(), "⚠️");
                    closeSocket(clientChannel);
                }
            });
        }
    }


    /**
     * Sends an error response to the client.
     *
     * @param clientChannel the client socket channel
     * @param statusCode    the HTTP status code
     * @param message       the error message
     */
    private void sendErrorResponse(AsynchronousSocketChannel clientChannel, int statusCode, String message) {
        Response errorResponse = new Response();
        errorResponse.status(statusCode).body(message).type("text/plain");
        sendResponse(errorResponse, clientChannel);
    }

    /**
     * Validates the expected request fields of the handler.
     *
     * @param handler the request handler
     */
    private void validateHandlerResources(RequestHandler handler) {
        handler.getExpectedRequestParameters().values()
                .parallelStream()
                .forEach(ExpectedRequestParameter::getFieldValue);

        handler.getExpectedBodyFields().values()
                .parallelStream()
                .forEach(ExpectedBodyField::getFieldValue);

        handler.getExpectedBodyFiles().values()
                .parallelStream()
                .forEach(ExpectedBodyFile::getInputStream);
    }

    /**
     * Converts the response body to a string suitable for the HTTP response.
     *
     * @param responseBody the response body object
     * @return the string representation of the response body
     */
    private String convertToResponseBody(Object responseBody) {
        return switch (responseBody) {
            case null -> "";
            case byte[] bytes -> new String(bytes);
            case String s -> s;
            default -> responseBody.toString();
        };
    }

    /**
     * Instantiates a new Router instance with the specified base path for this server.
     *
     * @return the Router instance
     */
    public Router route(String basePath) {
        return new Router(basePath, this);
    }

    /**
     * Creates a route key based on the HTTP method and path.
     *
     * @param method the HTTP method
     * @param path   the route path
     * @return the route key
     */
    private String createRouteKey(HttpMethod method, String path) {
        return method.name() + ":" + path;
    }

    /**
     * Closes a client socket channel.
     *
     * @param clientChannel the client socket channel
     */
    private void closeSocket(AsynchronousSocketChannel clientChannel) {
        try {
            clientChannel.close();
        } catch (IOException e) {
            PrettyLogger.logWithEmoji("Error closing socket: " + e.getMessage(), "❌");
        }
    }

    /**
     * Serves the OpenAPI schema and UI for the specified endpoint.
     *
     * @param endpoint      The base endpoint for the UI (e.g., "/swagger" or "/redoc").
     * @param configuration The configuration object for generating the OpenAPI schema.
     * @param templateHtml  The HTML template for the UI.
     * @return The Swagger generator configuration.
     */
    public OpenAPISchemaGenerator openapi(String endpoint, OpenAPIConfiguration configuration, OpenAPIUITemplate templateHtml) {
        OpenAPISchemaGenerator config = new OpenAPISchemaGenerator(this, configuration);
        JSONObject schema = config.generate();

        // Serve the Swagger schema JSON
        registerRoute(HttpMethod.GET, endpoint + "/schema.json", (req, res) -> {
            res.status(200);
            res.type("text/plain");
            return schema;
        }, HandlerType.INTERNAL);

        // Serve the UI HTML
        registerRoute(HttpMethod.GET, endpoint, (req, res) -> {
            res.status(200).type("text/html");
            return templateHtml.getTemplate().formatted(endpoint);
        }, HandlerType.INTERNAL);

        return config;
    }

    public void serveStaticFiles(String endpoint, StaticFileServerConfiguration config) {
        staticFileServer.serveStaticFiles(endpoint, config);
    }

    public Map<String, RequestHandler> getRouteHandlers() {
        return routeHandlers;
    }

    /**
     * Simple GET request handler registration.
     */
    public void get(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.GET, endpoint, handler);
    }

    /**
     * Simple POST request handler registration.
     */
    public void post(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.POST, endpoint, handler);
    }

    /**
     * Simple PUT request handler registration.
     */
    public void put(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.PUT, endpoint, handler);
    }

    /**
     * Simple DELETE request handler registration.
     */
    public void delete(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.DELETE, endpoint, handler);
    }

    /**
     * Simple PATCH request handler registration.
     */
    public void patch(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.PATCH, endpoint, handler);
    }

    /**
     * Simple HEAD request handler registration.
     */
    public void head(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.HEAD, endpoint, handler);
    }

    /**
     * Simple TRACE request handler registration.
     */
    public void trace(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.TRACE, endpoint, handler);
    }

    /**
     * Simple CONNECT request handler registration.
     */
    public void connect(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.CONNECT, endpoint, handler);
    }

    /**
     * Simple OPTIONS request handler registration.
     */
    public void options(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.OPTIONS, endpoint, handler);
    }

    /**
     * Simple BEFORE request handler registration.
     */
    public void before(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.BEFORE, endpoint, handler);
    }

    /**
     * Simple AFTER request handler registration.
     */
    public void after(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.AFTER, endpoint, handler);
    }

    /**
     * Simple AFTERAFTER request handler registration.
     */
    public void afterAfter(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.AFTERAFTER, endpoint, handler);
    }
}


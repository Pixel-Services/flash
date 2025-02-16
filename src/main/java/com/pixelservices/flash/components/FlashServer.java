package com.pixelservices.flash.components;

import com.pixelservices.flash.components.fileserver.DynamicFileServer;
import com.pixelservices.flash.components.fileserver.DynamicFileServerConfiguration;
import com.pixelservices.flash.components.fileserver.StaticFileServer;
import com.pixelservices.flash.components.fileserver.StaticFileServerConfiguration;
import com.pixelservices.flash.exceptions.RequestExceptionHandler;
import com.pixelservices.flash.exceptions.ServerStartupException;
import com.pixelservices.flash.exceptions.UnmatchedHandlerException;
import com.pixelservices.flash.lifecycle.Request;
import com.pixelservices.flash.lifecycle.Response;
import com.pixelservices.flash.models.HandlerSpecification;
import com.pixelservices.flash.models.HandlerType;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.models.SimpleHandler;
import com.pixelservices.flash.swagger.OpenAPIConfiguration;
import com.pixelservices.flash.swagger.OpenAPISchemaGenerator;
import com.pixelservices.flash.swagger.OpenAPIUITemplate;
import com.pixelservices.flash.utils.PrettyLogger;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FlashServer is a lightweight and asynchronous HTTP server optimized for concurrency.
 */
public class FlashServer {
    // Compatibility maps (if needed elsewhere)
    private final ConcurrentHashMap<String, RequestHandler> routeHandlers;
    private final ConcurrentHashMap<String, HandlerType> handlerTypes;

    // Precompiled route collections for fast lookup.
    private final Map<String, RouteEntry> literalRoutes = new ConcurrentHashMap<>();
    private final List<RouteEntry> parameterizedRoutes = new CopyOnWriteArrayList<>();
    private final List<RouteEntry> dynamicRoutes = new CopyOnWriteArrayList<>();

    private final int port;
    private AsynchronousServerSocketChannel serverSocketChannel;
    private final FlashConfiguration config;
    private final StaticFileServer staticFileServer;
    private final DynamicFileServer dynamicFileServer;

    private static final Pattern PATH_ONLY_PATTERN = Pattern.compile("^[^?]+");

    public FlashServer(int port, FlashConfiguration config) {
        this.routeHandlers = new ConcurrentHashMap<>();
        this.handlerTypes = new ConcurrentHashMap<>();
        this.port = port;
        this.staticFileServer = new StaticFileServer(this);
        this.dynamicFileServer = new DynamicFileServer(this);
        this.config = config;
    }

    public FlashServer(int port) {
        this(port, new FlashConfiguration());
    }

    /**
     * Starts the server and binds to the specified port.
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
            // Keep the server alive indefinitely.
            Thread.sleep(Long.MAX_VALUE);
        } catch (IOException | InterruptedException e) {
            PrettyLogger.withEmoji("Error starting server: " + e.getMessage(), "❌");
            throw new ServerStartupException("Error starting server", e);
        }
    }

    /**
     * Accepts incoming connections asynchronously.
     */
    private void acceptNextConnection() {
        serverSocketChannel.accept(null, new CompletionHandler<>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
                // Immediately accept the next connection.
                acceptNextConnection();
                // Use virtual threads to handle the client concurrently.
                Thread.startVirtualThread(() -> handleClient(clientChannel));
            }
            @Override
            public void failed(Throwable exc, Object attachment) {
                PrettyLogger.withEmoji("Failed to accept connection: " + exc.getMessage(), "⚠️");
                acceptNextConnection();
            }
        });
    }

    // ------------------ Route Registration ------------------ //

    public Router route(String basePath) {
        return new Router(basePath, this);
    }

    /**
     * Registers a route and precompiles its matching logic.
     */
    private void registerRoute(HttpMethod method, String fullPath, RequestHandler handler, HandlerType handlerType) {
        RouteEntry entry = new RouteEntry(method, fullPath, handler);
        if (fullPath.endsWith("/*")) {
            dynamicRoutes.add(entry);
        } else if (entry.isParameterized()) {
            parameterizedRoutes.add(entry);
        } else {
            literalRoutes.put(entry.getLiteralKey(), entry);
        }
        handlerTypes.put(method.name() + ":" + fullPath, handlerType);
        handler.setHandlerType(handlerType);
        String routingType = fullPath.endsWith("/*") ? "Dynamic"
                : (entry.isParameterized() ? "Parameterized" : "Literal");
        if (config.shouldLog(handlerType)) {
            PrettyLogger.withEmoji(handlerType.name() + " " + routingType + " Route registered: [" + method + "] " + fullPath, handlerType.getEmoji());
        }
        handler.setSpecification(new HandlerSpecification(handler, fullPath, method, handler.isEnforcedNonNullBody()));
    }

    public void registerRoute(HttpMethod method, String fullPath, RequestHandler handler) {
        registerRoute(method, fullPath, handler, HandlerType.STANDARD);
    }

    public void registerRoute(HttpMethod method, String fullPath, SimpleHandler handler) {
        registerRoute(method, fullPath, wrapSimpleHandler(handler), HandlerType.SIMPLE);
    }

    public void registerRoute(HttpMethod method, String fullPath, SimpleHandler handler, HandlerType handlerType) {
        registerRoute(method, fullPath, wrapSimpleHandler(handler), handlerType);
    }

    public void unregisterRoute(HttpMethod method, String fullPath) {
        String routeKey = createRouteKey(method, fullPath);
        if (routeHandlers.remove(routeKey) != null) {
            handlerTypes.remove(routeKey);
            PrettyLogger.withEmoji("Route unregistered: [" + method + "] " + fullPath, "❌");
        } else {
            PrettyLogger.withEmoji("Route not found: [" + method + "] " + fullPath, "⚠️");
        }
    }

    public void redirect(String fromPath, String toPath, HttpMethod method) {
        registerRoute(method, fromPath, (req, res) -> {
            res.status(302);
            res.header("Location", toPath);
            return "Redirecting to " + toPath;
        }, HandlerType.REDIRECT);
    }

    public void redirect(String fromPath, String toPath) {
        redirect(fromPath, toPath, HttpMethod.GET);
    }

    private RequestHandler wrapSimpleHandler(SimpleHandler handler) {
        return new RequestHandler(null, null) {
            @Override
            public Object handle() {
                return handler.handle(this.req, this.res);
            }
        };
    }

    // ------------------ Request Handling ------------------ //

    private void handleClient(AsynchronousSocketChannel clientChannel) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
        clientChannel.read(buffer, buffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer buf) {
                try {
                    String rawRequest = decodeBuffer(buf);
                    RequestInfo reqInfo = parseRequest(rawRequest);
                    RouteMatch match = findRoute(reqInfo);
                    if (match == null) {
                        throw new UnmatchedHandlerException("No handler found for "
                                + reqInfo.getMethod() + " " + reqInfo.getPath());
                    }
                    Request request = new Request(rawRequest, (InetSocketAddress) clientChannel.getRemoteAddress(), match.getParams());
                    Response response = new Response();
                    RequestHandler handler = match.getEntry().getHandler();
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
     * Decodes the given ByteBuffer into a UTF-8 String.
     */
    private String decodeBuffer(ByteBuffer buffer) {
        buffer.flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    /**
     * Parses the raw HTTP request to extract the method and path (query parameters removed).
     */
    private RequestInfo parseRequest(String rawRequest) {
        String[] lines = rawRequest.split("\r\n");
        if (lines.length == 0 || lines[0].isEmpty()) {
            throw new IllegalArgumentException("Empty request");
        }
        String[] parts = lines[0].split(" ");
        HttpMethod method = HttpMethod.valueOf(parts[0]);
        Matcher matcher = PATH_ONLY_PATTERN.matcher(parts[1]);
        String path = matcher.find() ? matcher.group() : parts[1];
        return new RequestInfo(method, path);
    }

    /**
     * Searches for a matching route for the given request info.
     * First checks literal routes, then uses parallel streaming to find a match in parameterized routes.
     */
    private RouteMatch findRoute(RequestInfo reqInfo) {
        String literalKey = reqInfo.getMethod().name() + ":" + reqInfo.getPath();
        RouteEntry literalEntry = literalRoutes.get(literalKey);
        if (literalEntry != null) {
            return new RouteMatch(literalEntry, Collections.emptyMap());
        }

        Optional<RouteMatch> paramMatch = parameterizedRoutes.parallelStream()
                .filter(e -> e.getMethod() == reqInfo.getMethod())
                .map(e -> {
                    Map<String, String> params = e.match(reqInfo.getPath());
                    return params != null ? new RouteMatch(e, params) : null;
                })
                .filter(Objects::nonNull)
                .findFirst();
        if (paramMatch.isPresent()) {
            return paramMatch.get();
        }

        Optional<RouteMatch> dynamicMatch = dynamicRoutes.parallelStream()
                .filter(e -> e.getMethod() == reqInfo.getMethod())
                .map(e -> {
                    String prefix = e.getPath().substring(0, e.getPath().length() - 2);
                    if (reqInfo.getPath().startsWith(prefix)) {
                        Map<String, String> params = new HashMap<>();
                        params.put("path", reqInfo.getPath().substring(prefix.length()));
                        return new RouteMatch(e, params);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst();
        return dynamicMatch.orElse(null);
    }


    // ------------------ Response and Socket Utilities ------------------ //

    private void sendResponse(Response response, AsynchronousSocketChannel clientChannel) {
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
                PrettyLogger.withEmoji("Error sending response: " + exc.getMessage(), "⚠️");
                closeSocket(clientChannel);
            }
        });
    }

    private void closeSocket(AsynchronousSocketChannel clientChannel) {
        try {
            clientChannel.close();
        } catch (IOException e) {
            PrettyLogger.withEmoji("Error closing socket: " + e.getMessage(), "❌");
        }
    }

    private void validateHandlerResources(RequestHandler handler) {
        handler.getExpectedRequestParameters().values().parallelStream().forEach(ExpectedRequestParameter::getFieldValue);
        handler.getExpectedBodyFields().values().parallelStream().forEach(ExpectedBodyField::getFieldValue);
        handler.getExpectedBodyFiles().values().parallelStream().forEach(ExpectedBodyFile::getInputStream);
    }

    private Object convertToResponseBody(Object responseBody) {
        return switch (responseBody) {
            case null -> "";
            case byte[] ignored -> responseBody;
            case String ignored -> responseBody;
            default -> responseBody.toString();
        };
    }


    /**
     * Creates a route key based on the HTTP method and path.
     */
    private String createRouteKey(HttpMethod method, String path) {
        return method.name() + ":" + path;
    }

    // ------------------ OpenAPI and Static Files ------------------ //

    public OpenAPISchemaGenerator openapi(String endpoint, OpenAPIConfiguration configuration, OpenAPIUITemplate templateHtml) {
        OpenAPISchemaGenerator config = new OpenAPISchemaGenerator(this, configuration);
        JSONObject schema = config.generate();
        registerRoute(HttpMethod.GET, endpoint + "/schema.json", (req, res) -> {
            res.status(200);
            res.type("text/plain");
            return schema;
        }, HandlerType.INTERNAL);
        registerRoute(HttpMethod.GET, endpoint, (req, res) -> {
            res.status(200).type("text/html");
            return templateHtml.getTemplate().formatted(endpoint);
        }, HandlerType.INTERNAL);
        return config;
    }

    public void serveStatic(String endpoint, StaticFileServerConfiguration config) {
        staticFileServer.serve(endpoint, config);
    }

    public void serveDynamic(String endpoint, DynamicFileServerConfiguration config) {
        dynamicFileServer.serve(endpoint, config);
    }

    public Map<String, RequestHandler> getRouteHandlers() {
        return routeHandlers;
    }

    // ------------------ Simple Route Registrations ------------------ //

    public void get(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.GET, endpoint, handler);
    }
    public void post(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.POST, endpoint, handler);
    }
    public void put(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.PUT, endpoint, handler);
    }
    public void delete(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.DELETE, endpoint, handler);
    }
    public void patch(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.PATCH, endpoint, handler);
    }
    public void head(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.HEAD, endpoint, handler);
    }
    public void trace(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.TRACE, endpoint, handler);
    }
    public void connect(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.CONNECT, endpoint, handler);
    }
    public void options(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.OPTIONS, endpoint, handler);
    }
    public void before(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.BEFORE, endpoint, handler);
    }
    public void after(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.AFTER, endpoint, handler);
    }
    public void afterAfter(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.AFTERAFTER, endpoint, handler);
    }

    // ------------------ Helper Classes ------------------ //

    private static class RequestInfo {
        private final HttpMethod method;
        private final String path;
        public RequestInfo(HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }
        public HttpMethod getMethod() { return method; }
        public String getPath() { return path; }
    }

    private static class RouteMatch {
        private final RouteEntry entry;
        private final Map<String, String> params;
        public RouteMatch(RouteEntry entry, Map<String, String> params) {
            this.entry = entry;
            this.params = params;
        }
        public RouteEntry getEntry() { return entry; }
        public Map<String, String> getParams() { return params; }
    }
}


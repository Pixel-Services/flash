package com.pixelservices.flash.components;

import com.pixelservices.flash.components.fileserver.DynamicFileServer;
import com.pixelservices.flash.components.fileserver.DynamicFileServerConfiguration;
import com.pixelservices.flash.components.fileserver.StaticFileServer;
import com.pixelservices.flash.components.fileserver.StaticFileServerConfiguration;
import com.pixelservices.flash.components.http.*;
import com.pixelservices.flash.components.http.routing.models.RequestInfo;
import com.pixelservices.flash.components.http.routing.models.RouteEntry;
import com.pixelservices.flash.components.http.routing.RouteRegistry;
import com.pixelservices.flash.components.http.routing.Router;
import com.pixelservices.flash.components.http.routing.models.SimpleHandler;
import com.pixelservices.flash.components.http.routing.models.SimpleHandlerWrapper;
import com.pixelservices.flash.components.http.pool.HandlerPoolManager;
import com.pixelservices.flash.components.websocket.WebSocketHandler;
import com.pixelservices.flash.components.websocket.WebSocketRequestHandler;
import com.pixelservices.flash.components.websocket.WebSocketSession;
import com.pixelservices.flash.exceptions.RequestExceptionHandler;
import com.pixelservices.flash.exceptions.ServerStartupException;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.models.*;
import com.pixelservices.flash.swagger.OpenAPIConfiguration;
import com.pixelservices.flash.swagger.OpenAPISchemaGenerator;
import com.pixelservices.flash.swagger.OpenAPIUITemplate;
import com.pixelservices.flash.utils.PrettyLogger;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Flash is a highly optimized, asynchronous and multithreaded web server.
 */
public class FlashServer {
    private final ConcurrentHashMap<String, RequestHandler> routeHandlers = new ConcurrentHashMap<>();

    // Add HandlerPoolManager as a field
    private final HandlerPoolManager handlerPoolManager;

    // ------------------ Middleware ------------------ //
    private final List<MiddlewareEntry> globalMiddlewares = new CopyOnWriteArrayList<>();
    private final Map<String, List<MiddlewareEntry>> pathMiddlewares = new ConcurrentHashMap<>();

    // ------------------ Networking & Thread Pools ------------------ //
    private final int port;
    private AsynchronousServerSocketChannel serverSocketChannel;
    private final FlashConfiguration config;
    private final StaticFileServer staticFileServer;
    private final DynamicFileServer dynamicFileServer;

    private final Map<String, WebSocketHandler> webSocketHandlers = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // Virtual thread executor for client connections.
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // ------------------ Off-Heap Buffer Management ------------------ //
    public static final int BUFFER_POOL_SIZE = 4096;
    public static final int BUFFER_SIZE = 262144; // 256KB
    public static final OffHeapBufferPool REQUEST_BUFFER_POOL = new OffHeapBufferPool(BUFFER_POOL_SIZE, BUFFER_SIZE);

    // For WebSocket frames (64KB per buffer)
    public static final int WEBSOCKET_BUFFER_SIZE = 65536;
    public static final OffHeapBufferPool WEBSOCKET_BUFFER_POOL = new OffHeapBufferPool(1024, WEBSOCKET_BUFFER_SIZE);

    // ------------------ Thread-Local Decoder ------------------ //
    private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(StandardCharsets.UTF_8::newDecoder);

    private final RouteRegistry routeRegistry = new RouteRegistry();
    private final HttpRequestHandler httpRequestHandler;
    private final WebSocketRequestHandler webSocketRequestHandler;

    // ------------------ Constructors ------------------ //

    public FlashServer(int port, FlashConfiguration config) {
        this.port = port;
        this.config = config;
        
        // Initialize the HandlerPoolManager with default values
        this.handlerPoolManager = new HandlerPoolManager(this, 5, 2, 20);
        
        this.staticFileServer = new StaticFileServer(this);
        this.dynamicFileServer = new DynamicFileServer(this);
        this.httpRequestHandler = new HttpRequestHandler(this, routeRegistry);
        this.webSocketRequestHandler = new WebSocketRequestHandler(this, webSocketHandlers, activeSessions, WEBSOCKET_BUFFER_POOL);
    }

    public FlashServer(int port) {
        this(port, new FlashConfiguration());
    }

    // ------------------ Server Startup ------------------ //
    
    private volatile boolean isRunning = false;
    private Thread serverThread;
    private final Object serverLock = new Object();

    /**
     * Starts the server and binds to the specified port.
     * This method is non-blocking and returns a thread that can be used to wait for the server to stop.
     * 
     * @return The thread running the server
     */
    public Thread start() {
        synchronized (serverLock) {
            if (isRunning) {
                throw new IllegalStateException("Server is already running");
            }
            
            serverThread = new Thread(() -> {
                try {
                    startServerInternal();
                } catch (Exception e) {
                    PrettyLogger.withEmoji("Server error: " + e.getMessage(), "❌");
                }
            }, "FlashServer-" + port);
            
            serverThread.setDaemon(false);
            isRunning = true;
            serverThread.start();
            
            return serverThread;
        }
    }
    
    /**
     * Stops the server safely.
     * This method blocks until the server has fully stopped.
     */
    public void stop() {
        synchronized (serverLock) {
            if (!isRunning || serverThread == null) {
                return;
            }
            
            isRunning = false;
            
            try {
                // Close the server socket channel
                if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                    serverSocketChannel.close();
                }
                
                // Shutdown the handler pool manager
                if (handlerPoolManager != null) {
                    handlerPoolManager.shutdown();
                }
                
                // Wait for the server thread to finish
                if (serverThread != null && serverThread.isAlive()) {
                    serverThread.join(5000); // Wait up to 5 seconds
                    if (serverThread.isAlive()) {
                        PrettyLogger.withEmoji("Server thread did not stop gracefully, forcing shutdown", "⚠️");
                        serverThread.interrupt();
                    }
                }
                
                PrettyLogger.withEmoji("Server stopped successfully on port " + port, "🛑");
                
            } catch (Exception e) {
                PrettyLogger.withEmoji("Error stopping server: " + e.getMessage(), "❌");
            } finally {
                serverThread = null;
            }
        }
    }
    
    /**
     * Checks if the server is currently running.
     * 
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        synchronized (serverLock) {
            return isRunning && serverThread != null && serverThread.isAlive();
        }
    }

    /**
     * Internal method that actually starts the server.
     * This runs on the server thread.
     */
    private void startServerInternal() {
        final long startTime = System.currentTimeMillis();
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
            acceptNextConnection();
            final long elapsedTime = System.currentTimeMillis() - startTime;
            final String[] flashPixelArt = {
                    "&#reset",
                    "      *      ",
                    "     **      ",
                    "    ***       &#reset Started &#00CC66successfully&#reset on port &#FF746C" + port + "&#reset",
                    "   *******    &#reset Startup time: &#FF746C" + elapsedTime + "&#reset ms",
                    "      ***     &#reset Serving " + routeRegistry.getLiteralRouteCount() + " literal routes, " + routeRegistry.getParameterizedRouteCount() + " parameterized routes, " + routeRegistry.getDynamicRouteCount() + " dynamic routes",
                    "      **     ",
                    "      *      ",
                    "&#reset"
            };
            for (String line : flashPixelArt) {
                //PrettyLogger.log("&#FFEE8C" + line);
            }
            
            // Keep the server alive until stopped
            while (isRunning) {
                try {
                    Thread.sleep(100); // Check every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
        } catch (IOException e) {
            PrettyLogger.withEmoji("Error starting server: " + e.getMessage(), "❌");
            throw new ServerStartupException("Error starting server", e);
        }
    }

    private void acceptNextConnection() {
        serverSocketChannel.accept(null, new CompletionHandler<>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
                acceptNextConnection();
                VIRTUAL_THREAD_EXECUTOR.submit(() -> handleClient(clientChannel));
            }
            @Override
            public void failed(Throwable exc, Object attachment) {
                PrettyLogger.withEmoji("Failed to accept connection: " + exc.getMessage(), "⚠️");
                acceptNextConnection();
            }
        });
    }

    // ------------------ Middleware Registration ------------------ //

    public void use(Middleware middleware) {
        globalMiddlewares.add(new MiddlewareEntry(middleware, null));
        PrettyLogger.withEmoji("Global middleware registered", "🔄");
    }

    public void use(String pathPrefix, Middleware middleware) {
        pathMiddlewares.computeIfAbsent(pathPrefix, k -> new CopyOnWriteArrayList<>())
                .add(new MiddlewareEntry(middleware, pathPrefix));
        PrettyLogger.withEmoji("Path middleware registered for: " + pathPrefix, "🔄");
    }

    public void enableCORS(String allowedOrigins, String allowedMethods, String allowedHeaders) {
        Middleware corsMiddleware = (request, response) -> {
            response.header("Access-Control-Allow-Origin", allowedOrigins);
            if (request.method() == HttpMethod.OPTIONS) {
                response.header("Access-Control-Allow-Methods", allowedMethods);
                response.header("Access-Control-Allow-Headers", allowedHeaders);
                response.header("Access-Control-Max-Age", "86400");
                response.status(204);
                response.body("");
                return false;
            }
            return true;
        };
        use(corsMiddleware);
        registerRoute(HttpMethod.OPTIONS, "/*", (req, res) -> "", HandlerType.INTERNAL);
        PrettyLogger.withEmoji("CORS enabled for origin: " + allowedOrigins, "🌐");
    }

    // ------------------ Route Registration ------------------ //

    public Router route(String basePath) {
        return new Router(basePath, this);
    }

    private void registerRoute(HttpMethod method, String fullPath, RequestHandler handler, HandlerType handlerType) {
        final RouteEntry entry = new RouteEntry(method, fullPath, handler, handlerType, handlerPoolManager);
        if (fullPath.endsWith("/*")) {
            routeRegistry.registerDynamicRoute(entry);
        } else if (entry.isParameterized()) {
            routeRegistry.registerParameterizedRoute(entry);
        } else {
            routeRegistry.registerLiteralRoute(entry);
        }
        handler.setHandlerType(handlerType);
        final String routingType = fullPath.endsWith("/*") ? "Dynamic" : (entry.isParameterized() ? "Parameterized" : "Literal");
        if (config.shouldLog(handlerType)) {
            String poolInfo = "";
            if (entry.getHandlerPool() != null) {
                poolInfo = " [Pool: " + entry.getHandlerPool().getTotalSize() + "/" + 
                          entry.getHandlerPool().getHandlerClass() + "]";
            }
            PrettyLogger.withEmoji(handlerType.name() + " " + routingType + " Route registered: [" + method + "] " + 
                                fullPath + poolInfo, handlerType.getEmoji());
        }
        handler.setSpecification(new HandlerSpecification(handler, fullPath, method, handler.isEnforcedNonNullBody()));
        routeHandlers.put(method.name() + ":" + fullPath, handler);
    }

    public void registerRoute(HttpMethod method, String fullPath, RequestHandler handler) {
        registerRoute(method, fullPath, handler, HandlerType.STANDARD);
    }

    public void registerRoute(HttpMethod method, String fullPath, SimpleHandler handler) {
        SimpleHandlerWrapper wrappedHandler = new SimpleHandlerWrapper(null, null);
        wrappedHandler.setSimpleHandler(handler);
        registerRoute(method, fullPath, wrappedHandler, HandlerType.SIMPLE);
    }

    public void registerRoute(HttpMethod method, String fullPath, SimpleHandler handler, HandlerType handlerType) {
        SimpleHandlerWrapper wrappedHandler = new SimpleHandlerWrapper(null, null);
        wrappedHandler.setSimpleHandler(handler);
        registerRoute(method, fullPath, wrappedHandler, handlerType);
    }

    public void unregisterRoute(HttpMethod method, String fullPath) {
        final String routeKey = method.name() + ":" + fullPath;
        if (routeHandlers.remove(routeKey) != null) {
            PrettyLogger.withEmoji("Route unregistered: [" + method + "] " + fullPath, "❌");
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

    // ------------------ Request Handling ------------------ //

    private void handleClient(AsynchronousSocketChannel clientChannel) {
        final ByteBuffer buffer = REQUEST_BUFFER_POOL.acquire();
        final ClientAttachment attachment = new ClientAttachment(buffer, clientChannel);
        startRead(attachment);
    }

    private void startRead(ClientAttachment attachment) {
        attachment.buffer.clear();
        attachment.channel.read(attachment.buffer, attachment, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, ClientAttachment att) {
                if (bytesRead > 0) {
                    processReadData(att);
                } else if (bytesRead == -1) {
                    cleanupResources(att);
                } else {
                    startRead(att); // Continue reading
                }
            }

            @Override
            public void failed(Throwable exc, ClientAttachment att) {
                handleFailure(exc, att);
            }
        });
    }

    private void processReadData(ClientAttachment att) {
        ByteBuffer buf = att.buffer;

        buf.flip();
        try {
            // Use a more efficient approach for checking if we have a complete request
            if (isCompleteRequest(buf, att.requestData)) {
                handleRequest(att, att.requestData.toString());
            } else {
                // Continue reading if we don't have a complete request yet
                buf.clear();
                startRead(att);
            }
        } catch (Exception ex) {
            handleDecodingError(ex, att);
        }
    }

    private boolean isCompleteRequest(ByteBuffer buffer, StringBuilder requestData) {
        try {
            CharBuffer charBuffer = decodeBuffer(buffer);
            requestData.append(charBuffer);
            
            // Fast check for HTTP request completion
            String data = requestData.toString();
            
            // Check for end of headers
            int headersEnd = data.indexOf("\r\n\r\n");
            if (headersEnd == -1) {
                return false;
            }
            
            // For requests with a body, check Content-Length
            String contentLengthHeader = "Content-Length: ";
            int contentLengthPos = data.indexOf(contentLengthHeader);
            if (contentLengthPos != -1) {
                int valueStart = contentLengthPos + contentLengthHeader.length();
                int valueEnd = data.indexOf("\r\n", valueStart);
                if (valueEnd != -1) {
                    try {
                        int contentLength = Integer.parseInt(data.substring(valueStart, valueEnd).trim());
                        int bodyStart = headersEnd + 4;
                        int bodyLength = data.length() - bodyStart;
                        return bodyLength >= contentLength;
                    } catch (NumberFormatException e) {
                        // If we can't parse content length, assume request is complete
                        return true;
                    }
                }
            }
            
            // If no Content-Length header, assume the request is complete after headers
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private CharBuffer decodeBuffer(ByteBuffer buf) throws CharacterCodingException {
        CharsetDecoder decoder = UTF8_DECODER.get();
        decoder.reset();

        ByteBuffer duplicateBuffer = buf.duplicate();
        return decoder.decode(duplicateBuffer);
    }

    private void handleRequest(ClientAttachment att, String fullRequestData) {
        try {
            RequestInfo reqInfo = parseRequest(fullRequestData);
            if (isWebSocketRequest(reqInfo)) {
                att.isWebSocket = true;
                webSocketRequestHandler.handle(att.channel, reqInfo);
            } else {
                httpRequestHandler.handle(att, reqInfo, fullRequestData);
            }
        } catch (Exception e) {
            new RequestExceptionHandler(att.channel, e).handle();
        } finally {
            if (!att.isWebSocket) {
                cleanupResources(att);
            }
        }
    }

    private void handleDecodingError(Exception ex, ClientAttachment att) {
        PrettyLogger.withEmoji("Decoding error: " + ex.getMessage(), "❌");
        cleanupResources(att);
    }

    private void handleFailure(Throwable exc, ClientAttachment att) {
        PrettyLogger.withEmoji("Read failed: " + exc.getMessage(), "❌");
        PrettyLogger.log("Buffer state on read fail - Position: " + att.buffer.position() + ", Limit: " + att.buffer.limit() + ", Capacity: " + att.buffer.capacity()); // Logging buffer state on failure
        new RequestExceptionHandler(att.channel, new Exception(exc)).handle();
        cleanupResources(att);
    }

    private void cleanupResources(ClientAttachment att) {
        REQUEST_BUFFER_POOL.release(att.buffer);
        closeSocket(att.channel);
    }

    public static RequestInfo parseRequest(String rawRequest) {
        final int requestLineEnd = rawRequest.indexOf("\r\n");
        if (requestLineEnd == -1) throw new IllegalArgumentException("Malformed request");
        final String requestLine = rawRequest.substring(0, requestLineEnd);
        final int firstSpace = requestLine.indexOf(' ');
        final int secondSpace = requestLine.indexOf(' ', firstSpace + 1);
        if (firstSpace == -1 || secondSpace == -1) throw new IllegalArgumentException("Malformed request line: " + requestLine);
        final String methodStr = requestLine.substring(0, firstSpace);
        final String rawPath = requestLine.substring(firstSpace + 1, secondSpace);
        final int qIndex = rawPath.indexOf('?');
        final String path = (qIndex != -1) ? rawPath.substring(0, qIndex) : rawPath;
        final HttpMethod method = HttpMethod.valueOf(methodStr);
        final int headersEnd = rawRequest.indexOf("\r\n\r\n");
        final List<String> headers = (headersEnd != -1 && headersEnd > requestLineEnd)
                ? Arrays.asList(rawRequest.substring(requestLineEnd + 2, headersEnd).split("\r\n"))
                : Collections.emptyList();
        return new RequestInfo(method, path, headers);
    }

    private boolean isWebSocketRequest(RequestInfo reqInfo) {
        return "Upgrade".equalsIgnoreCase(reqInfo.getHeader("Connection")) &&
                "websocket".equalsIgnoreCase(reqInfo.getHeader("Upgrade"));
    }

    public boolean processMiddleware(String path, Request request, Response response) {
        for (final MiddlewareEntry entry : globalMiddlewares) {
            if (!entry.middleware.process(request, response)) return false;
        }
        for (final Map.Entry<String, List<MiddlewareEntry>> e : pathMiddlewares.entrySet()) {
            if (path.startsWith(e.getKey())) {
                for (final MiddlewareEntry entry : e.getValue()) {
                    if (!entry.middleware.process(request, response)) return false;
                }
            }
        }
        return true;
    }

    public static void closeSocket(AsynchronousSocketChannel clientChannel) {
        try { clientChannel.close(); }
        catch (IOException e) { PrettyLogger.withEmoji("Error closing socket: " + e.getMessage(), "❌"); }
    }

    // ------------------ Simple Route Registrations ------------------ //

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
        dynamicFileServer.serve(endpoint, config, getClass());
    }

    public void serveDynamic(String endpoint, DynamicFileServerConfiguration config, Class<?> contextClass) {
        dynamicFileServer.serve(endpoint, config, contextClass);
    }

    public void ws(String endpoint, WebSocketHandler handler) {
        registerWebSocketRoute(endpoint, handler);
    }

    private void registerWebSocketRoute(String endpoint, WebSocketHandler handler) {
        webSocketHandlers.put(endpoint, handler);
        PrettyLogger.withEmoji("WebSocket Route registered: " + endpoint, "🔗");
    }

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

    // ------------------ Helper Methods ------------------ //

    public Map<String, RequestHandler> getRouteHandlers() {
        return routeHandlers;
    }

    // ------------------ Helper Classes ------------------ //

    private record MiddlewareEntry(Middleware middleware, String pathPrefix){}

    // Add a method to access the HandlerPoolManager
    public HandlerPoolManager getHandlerPoolManager() {
        return handlerPoolManager;
    }
}
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
import com.pixelservices.flash.models.*;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;

/**
 * FlashServer is a lightweight and asynchronous HTTP server optimized for concurrency.
 */
public class FlashServer {
    // Compatibility maps (if needed elsewhere)
    private final ConcurrentHashMap<String, RequestHandler> routeHandlers;
    private final ConcurrentHashMap<String, HandlerType> handlerTypes;

    // Enhanced middleware system with path-specific middleware chains
    private final List<MiddlewareEntry> globalMiddlewares = new CopyOnWriteArrayList<>();
    private final Map<String, List<MiddlewareEntry>> pathMiddlewares = new ConcurrentHashMap<>();

    // Precompiled route collections for fast lookup.
    private final Map<String, RouteEntry> literalRoutes = new ConcurrentHashMap<>();
    private final List<RouteEntry> parameterizedRoutes = new CopyOnWriteArrayList<>();
    private final List<RouteEntry> dynamicRoutes = new CopyOnWriteArrayList<>();

    private final int port;
    private AsynchronousServerSocketChannel serverSocketChannel;
    private final FlashConfiguration config;
    private final StaticFileServer staticFileServer;
    private final DynamicFileServer dynamicFileServer;

    private final Map<String, WebSocketHandler> webSocketHandlers = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    private static final Pattern PATH_ONLY_PATTERN = Pattern.compile("^[^?]+");
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    // A virtual thread executor for handling client connections concurrently.
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // Thread pool for I/O operations that might block
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService IO_THREAD_POOL =
            new ThreadPoolExecutor(
                    CPU_CORES * 2,
                    CPU_CORES * 4,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(10000),
                    new ThreadFactory() {
                        private final AtomicInteger counter = new AtomicInteger(0);
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "flash-io-thread-" + counter.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        }
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure handling
            );

    // Buffer Pool Configuration
    private static final int BUFFER_POOL_SIZE = 4096; // Increased pool size for high concurrency
    private static final int BUFFER_SIZE = 16384; // Increased buffer size to 16KB for larger requests
    private static final BufferPool REQUEST_BUFFER_POOL = new BufferPool(BUFFER_POOL_SIZE, BUFFER_SIZE);

    // Buffer for WebSocket frames
    private static final int WEBSOCKET_BUFFER_SIZE = 65536; // 64KB for WebSocket frames
    private static final BufferPool WEBSOCKET_BUFFER_POOL = new BufferPool(1024, WEBSOCKET_BUFFER_SIZE);

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
                    "    ***       &#reset Started PEF80successfully&#reset on port &#FF746C" + port + "&#reset",
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
     * Add global middleware that applies to all routes
     */
    public void use(Middleware middleware) {
        globalMiddlewares.add(new MiddlewareEntry(middleware, null));
        PrettyLogger.withEmoji("Global middleware registered", "🔄");
    }

    /**
     * Add middleware with a path filter
     */
    public void use(String pathPrefix, Middleware middleware) {
        pathMiddlewares.computeIfAbsent(pathPrefix, k -> new CopyOnWriteArrayList<>())
                .add(new MiddlewareEntry(middleware, pathPrefix));
        PrettyLogger.withEmoji("Path middleware registered for: " + pathPrefix, "🔄");
    }

    /**
     * Creates a CORS middleware and adds it to the global middleware stack
     */
    public void enableCORS(String allowedOrigins, String allowedMethods, String allowedHeaders) {
        Middleware corsMiddleware = new Middleware() {
            @Override
            public boolean process(Request request, Response response) {
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
            }
        };

        use(corsMiddleware);

        registerRoute(HttpMethod.OPTIONS, "/*", (req, res) -> "", HandlerType.INTERNAL);

        PrettyLogger.withEmoji("CORS enabled for origin: " + allowedOrigins, "🌐");
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
                // Submit the client handling task to our virtual thread executor.
                VIRTUAL_THREAD_EXECUTOR.submit(() -> handleClient(clientChannel));
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

    private static class ClientAttachment { // Class to hold state per client connection
        ByteBuffer buffer;
        StringBuilder requestData;
        AsynchronousSocketChannel channel;
        boolean isWebSocket = false;

        public ClientAttachment(ByteBuffer buffer, AsynchronousSocketChannel channel) {
            this.buffer = buffer;
            this.requestData = new StringBuilder();
            this.channel = channel;
        }
    }

    private void handleClient(AsynchronousSocketChannel clientChannel) {
        ByteBuffer buffer = REQUEST_BUFFER_POOL.acquire();
        ClientAttachment attachment = new ClientAttachment(buffer, clientChannel);
        startRead(attachment);
    }

    private void startRead(ClientAttachment attachment) {
        attachment.buffer.clear();
        attachment.channel.read(attachment.buffer, attachment, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, ClientAttachment clientAttachment) {
                if (bytesRead > 0) {
                    ByteBuffer buf = clientAttachment.buffer;
                    buf.flip();
                    String chunk = StandardCharsets.UTF_8.decode(buf).toString();
                    clientAttachment.requestData.append(chunk);
                    buf.clear();

                    String fullRequestData = clientAttachment.requestData.toString();
                    if (fullRequestData.contains("\r\n\r\n")) {
                        try {
                            InetSocketAddress remoteAddress = (InetSocketAddress) clientAttachment.channel.getRemoteAddress();
                            RequestInfo reqInfo = parseRequest(fullRequestData);

                            if (isWebSocketRequest(reqInfo)) {
                                clientAttachment.isWebSocket = true;
                                handleWebSocketHandshake(clientAttachment.channel, reqInfo, fullRequestData);
                            } else {
                                handleHttpRequest(clientAttachment, reqInfo, fullRequestData, remoteAddress);
                            }
                        } catch (Exception e) {
                            PrettyLogger.withEmoji("Error handling request: " + e.getMessage(), "❌");
                            new RequestExceptionHandler(clientAttachment.channel, e).handle();
                        } finally {
                            if (!clientAttachment.isWebSocket) {
                                REQUEST_BUFFER_POOL.release(clientAttachment.buffer);
                                closeSocket(clientAttachment.channel);
                            }
                        }
                        return;
                    }

                    startRead(clientAttachment);
                } else if (bytesRead == -1) {
                    REQUEST_BUFFER_POOL.release(clientAttachment.buffer);
                    closeSocket(clientAttachment.channel);
                } else {
                    startRead(clientAttachment);
                }
            }

            @Override
            public void failed(Throwable exc, ClientAttachment clientAttachment) {
                PrettyLogger.withEmoji("Read failed: " + exc.getMessage(), "❌");
                new RequestExceptionHandler(clientAttachment.channel, new Exception(exc)).handle();
                REQUEST_BUFFER_POOL.release(clientAttachment.buffer);
                closeSocket(clientAttachment.channel);
            }
        });
    }

    private boolean isWebSocketRequest(RequestInfo reqInfo) {
        return "Upgrade".equalsIgnoreCase(reqInfo.getHeader("Connection")) &&
                "websocket".equalsIgnoreCase(reqInfo.getHeader("Upgrade"));
    }

    private void handleHttpRequest(ClientAttachment clientAttachment, RequestInfo reqInfo, String fullRequestData, InetSocketAddress remoteAddress) throws UnmatchedHandlerException {
        RouteMatch match = findRoute(reqInfo);
        Map<String, String> params = match != null ? match.getParams() : Collections.emptyMap();
        Request request = new Request(fullRequestData, remoteAddress, params);
        Response response = new Response();

        boolean shouldContinueToHandler = processMiddleware(reqInfo.getPath(), request, response);

        if (!shouldContinueToHandler) {
            sendResponse(response, clientAttachment.channel);
            return;
        }

        if (match == null) {
            throw new UnmatchedHandlerException("No handler found for " + reqInfo.getMethod() + " " + reqInfo.getPath());
        }

        RequestHandler handler = match.getEntry().getHandler();
        handler.setRequestResponse(request, response);
        validateHandlerResources(handler);

        Object responseBody = handler.handle();
        response.body(convertToResponseBody(responseBody));
        sendResponse(response, clientAttachment.channel);
    }

    private void handleWebSocketHandshake(AsynchronousSocketChannel clientChannel, RequestInfo reqInfo, String fullRequestData) {
        String path = reqInfo.getPath();
        WebSocketHandler handler = webSocketHandlers.get(path);

        if (handler == null) {
            PrettyLogger.withEmoji("No WebSocket handler found for path: " + path, "⚠️");
            try {
                closeSocket(clientChannel);
            } catch (Exception e) {
                PrettyLogger.withEmoji("Error closing WebSocket with no handler: " + e.getMessage(), "❌");
            }
            return;
        }

        try {
            // Get the WebSocket key from the request headers
            String webSocketKey = reqInfo.getHeader("Sec-WebSocket-Key");
            if (webSocketKey == null) {
                PrettyLogger.withEmoji("WebSocket handshake failed: missing Sec-WebSocket-Key header", "❌");
                closeSocket(clientChannel);
                return;
            }

            // Create the WebSocket accept key
            String acceptKey = generateWebSocketAcceptKey(webSocketKey);

            // Build handshake response
            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 101 Switching Protocols\r\n");
            response.append("Upgrade: websocket\r\n");
            response.append("Connection: Upgrade\r\n");
            response.append("Sec-WebSocket-Accept: ").append(acceptKey).append("\r\n");
            response.append("\r\n");

            // Send handshake response
            ByteBuffer responseBuffer = ByteBuffer.wrap(response.toString().getBytes(StandardCharsets.UTF_8));

            clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<>() {
                @Override
                public void completed(Integer bytesWritten, ByteBuffer buffer) {
                    if (buffer.hasRemaining()) {
                        clientChannel.write(buffer, buffer, this);
                    } else {
                        // Handshake completed, create session and start handling WebSocket frames
                        WebSocketSession session = new WebSocketSession(clientChannel, reqInfo, path);
                        String sessionId = UUID.randomUUID().toString();
                        activeSessions.put(sessionId, session);
                        session.setId(sessionId);

                        try {
                            // Notify the handler that a session has been opened
                            handler.onOpen(session);

                            // Start reading WebSocket frames
                            startWebSocketFrameReader(session, handler);
                        } catch (Exception e) {
                            PrettyLogger.withEmoji("Error in WebSocket open handler: " + e.getMessage(), "❌");
                            handler.onError(session, e);
                            removeSession(session);
                        }
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer buffer) {
                    PrettyLogger.withEmoji("WebSocket handshake response failed: " + exc.getMessage(), "❌");
                    closeSocket(clientChannel);
                }
            });
        } catch (Exception e) {
            PrettyLogger.withEmoji("Error during WebSocket handshake: " + e.getMessage(), "❌");
            closeSocket(clientChannel);
        }
    }

    private String generateWebSocketAcceptKey(String webSocketKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            String concatenated = webSocketKey + WEBSOCKET_GUID;
            byte[] sha1Hash = md.digest(concatenated.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sha1Hash);
        } catch (NoSuchAlgorithmException e) {
            PrettyLogger.withEmoji("Error generating WebSocket accept key: " + e.getMessage(), "❌");
            throw new RuntimeException("Failed to generate WebSocket accept key", e);
        }
    }

    private void startWebSocketFrameReader(WebSocketSession session, WebSocketHandler handler) {
        ByteBuffer buffer = WEBSOCKET_BUFFER_POOL.acquire();
        session.setBuffer(buffer);

        readWebSocketFrame(session, handler);
    }

    private void readWebSocketFrame(WebSocketSession session, WebSocketHandler handler) {
        ByteBuffer buffer = session.getBuffer();
        buffer.clear();

        session.getChannel().read(buffer, session, new CompletionHandler<Integer, WebSocketSession>() {
            @Override
            public void completed(Integer bytesRead, WebSocketSession session) {
                if (bytesRead > 0) {
                    buffer.flip();

                    try {
                        processWebSocketFrame(buffer, session, handler);
                        readWebSocketFrame(session, handler);
                    } catch (Exception e) {
                        PrettyLogger.withEmoji("Error processing WebSocket frame: " + e.getMessage(), "❌");
                        handler.onError(session, e);
                        removeSession(session);
                    }
                } else if (bytesRead == -1) {
                    // Connection closed
                    handler.onClose(session, 1000, "Connection closed by client");
                    removeSession(session);
                } else {
                    // Continue reading
                    readWebSocketFrame(session, handler);
                }
            }

            @Override
            public void failed(Throwable exc, WebSocketSession session) {
                PrettyLogger.withEmoji("WebSocket read failed: " + exc.getMessage(), "❌");
                handler.onError(session, exc);
                removeSession(session);
            }
        });
    }

    private void processWebSocketFrame(ByteBuffer buffer, WebSocketSession session, WebSocketHandler handler) {
        if (buffer.remaining() < 2) {
            return;
        }

        byte byte1 = buffer.get();
        byte byte2 = buffer.get();

        boolean fin = (byte1 & 0x80) != 0;
        int rsv1 = (byte1 & 0x40) != 0 ? 1 : 0;
        int rsv2 = (byte1 & 0x20) != 0 ? 1 : 0;
        int rsv3 = (byte1 & 0x10) != 0 ? 1 : 0;
        int opcode = byte1 & 0x0F;
        boolean masked = (byte2 & 0x80) != 0;
        int payloadLength = byte2 & 0x7F;

        if (rsv1 != 0 || rsv2 != 0 || rsv3 != 0) {
            PrettyLogger.withEmoji("Invalid WebSocket frame: RSV1, RSV2, and RSV3 must be clear", "❌");
            session.close(1002, "Protocol error");
            return;
        }

        long actualPayloadLength;
        if (payloadLength < 126) {
            actualPayloadLength = payloadLength;
        } else if (payloadLength == 126) {
            if (buffer.remaining() < 2) {
                return;
            }
            actualPayloadLength = ((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF);
        } else {
            if (buffer.remaining() < 8) {
                return;
            }
            actualPayloadLength = buffer.getLong();
        }

        if (actualPayloadLength > WEBSOCKET_BUFFER_SIZE - 14) {
            PrettyLogger.withEmoji("WebSocket frame too large: " + actualPayloadLength + " bytes", "⚠️");
            session.close(1009, "Message too big");
            return;
        }

        byte[] maskingKey = new byte[4];
        if (masked) {
            if (buffer.remaining() < 4) {
                return;
            }
            buffer.get(maskingKey);
        }

        if (buffer.remaining() < actualPayloadLength) {
            return;
        }

        byte[] payload = new byte[(int) actualPayloadLength];
        buffer.get(payload);

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
            }
        }

        switch (opcode) {
            case 0x0:
                break;
            case 0x1:
                String message = new String(payload, StandardCharsets.UTF_8);
                try {
                    handler.onMessage(session, message);
                } catch (Exception e) {
                    PrettyLogger.withEmoji("Error in WebSocket message handler: " + e.getMessage(), "❌");
                    handler.onError(session, e);
                }
                break;
            case 0x2:
                break;
            case 0x8:
                int statusCode = 1000;
                String reason = "";
                if (payload.length >= 2) {
                    statusCode = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    if (payload.length > 2) {
                        reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
                    }
                }
                handler.onClose(session, statusCode, reason);
                session.close(statusCode, "Acknowledged");
                removeSession(session);
                break;
            case 0x9:
                sendPong(session, payload);
                break;
            case 0xA:
                break;
            default:
                session.close(1002, "Protocol error");
                removeSession(session);
                break;
        }
    }

    private void sendPong(WebSocketSession session, byte[] payload) {
        ByteBuffer pongBuffer = ByteBuffer.allocate(2 + payload.length);
        // Create pong frame with opcode 0xA
        pongBuffer.put((byte) 0x8A); // FIN + opcode
        pongBuffer.put((byte) payload.length); // payload length (assuming small payload)
        pongBuffer.put(payload);
        pongBuffer.flip();

        session.getChannel().write(pongBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                // Pong sent successfully
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                PrettyLogger.withEmoji("Error sending WebSocket pong: " + exc.getMessage(), "❌");
            }
        });
    }

    private void removeSession(WebSocketSession session) {
        try {
            activeSessions.remove(session.getId());
            WEBSOCKET_BUFFER_POOL.release(session.getBuffer());
            closeSocket(session.getChannel());
        } catch (Exception e) {
            PrettyLogger.withEmoji("Error removing WebSocket session: " + e.getMessage(), "⚠️");
        }
    }

    /**
     * Process the middleware chain for a given request path
     * @return true if the request should continue to the handler, false if it should stop
     */
    private boolean processMiddleware(String path, Request request, Response response) {
        // First process global middleware
        for (MiddlewareEntry entry : globalMiddlewares) {
            if (!entry.middleware.process(request, response)) {
                return false;
            }
        }

        // Then process path-specific middleware
        for (Map.Entry<String, List<MiddlewareEntry>> pathEntry : pathMiddlewares.entrySet()) {
            String pathPrefix = pathEntry.getKey();

            // Skip middleware that doesn't match this path
            if (!path.startsWith(pathPrefix)) {
                continue;
            }

            for (MiddlewareEntry entry : pathEntry.getValue()) {
                if (!entry.middleware.process(request, response)) {
                    return false;
                }
            }
        }

        return true;
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

        List<String> headers = new ArrayList<>(Arrays.asList(lines).subList(1, lines.length));

        return new RequestInfo(method, path, headers);
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

        // Try sequential stream first for potential minor performance gain if parallel overhead is significant
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

        Optional<RouteMatch> dynamicMatch = dynamicRoutes.parallelStream() // Use sequential stream as well initially
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

        clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer buf) {
                if (buf.hasRemaining()) {
                    clientChannel.write(buf, buf, this);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                PrettyLogger.withEmoji("Error sending response: " + exc.getMessage(), "⚠️");
                closeSocket(clientChannel); // Close socket on response send failure as well.
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
        handler.getExpectedRequestParameters().values().parallelStream().forEach(ExpectedRequestParameter::getFieldValue); // Use sequential stream
        handler.getExpectedBodyFields().values().parallelStream().forEach(ExpectedBodyField::getFieldValue); // Use sequential stream
        handler.getExpectedBodyFiles().values().parallelStream().forEach(ExpectedBodyFile::getInputStream); // Use sequential stream
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
    public void connect(String endpoint, SimpleHandler handler) {registerRoute(HttpMethod.CONNECT, endpoint, handler);}
    public void options(String endpoint, SimpleHandler handler) { registerRoute(HttpMethod.OPTIONS, endpoint, handler);}
    public void before(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.BEFORE, endpoint, handler);
    }
    public void after(String endpoint, SimpleHandler handler) {
        registerRoute(HttpMethod.AFTER, endpoint, handler);
    }
    public void afterAfter(String endpoint, SimpleHandler handler) {registerRoute(HttpMethod.AFTERAFTER, endpoint, handler);}

    // ------------------ Helper Classes ------------------ //

    /**
     * Wrapper class that holds a middleware and its target path
     */
    private static class MiddlewareEntry {
        private final Middleware middleware;

        public MiddlewareEntry(Middleware middleware, String pathPrefix) {
            this.middleware = middleware;
        }
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

    /**
     * A simple pool for reusing direct ByteBuffers.
     */
    private static class BufferPool {
        private final int bufferSize; // Instance variable for buffer size
        private final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

        public BufferPool(int initialSize, int bufferSize) {
            this.bufferSize = bufferSize; // Initialize bufferSize
            for (int i = 0; i < initialSize; i++) {
                pool.offer(ByteBuffer.allocateDirect(this.bufferSize)); // Use instance bufferSize
            }
        }

        public ByteBuffer acquire() {
            ByteBuffer buffer = pool.poll();
            if (buffer == null) {
                return ByteBuffer.allocateDirect(this.bufferSize);
            }
            buffer.clear();
            return buffer;
        }

        public void release(ByteBuffer buffer) {
            pool.offer(buffer);
        }
    }
}
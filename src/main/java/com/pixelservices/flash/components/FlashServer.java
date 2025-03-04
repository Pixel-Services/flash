package com.pixelservices.flash.components;

import com.pixelservices.flash.components.fileserver.DynamicFileServer;
import com.pixelservices.flash.components.fileserver.DynamicFileServerConfiguration;
import com.pixelservices.flash.components.fileserver.StaticFileServer;
import com.pixelservices.flash.components.fileserver.StaticFileServerConfiguration;
import com.pixelservices.flash.components.routing.models.RouteEntry;
import com.pixelservices.flash.components.routing.models.RouteMatch;
import com.pixelservices.flash.components.routing.RouteRegistry;
import com.pixelservices.flash.components.routing.Router;
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
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Flash is a highly optimized, asynchronous and multithreaded web server.
 */
public class FlashServer {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    // For routes registered via other mechanisms (like simple handlers)
    private final ConcurrentHashMap<String, RequestHandler> routeHandlers = new ConcurrentHashMap<>();

    // ------------------ Middleware ------------------ //
    private final List<MiddlewareEntry> globalMiddlewares = new CopyOnWriteArrayList<>();
    private final Map<String, List<MiddlewareEntry>> pathMiddlewares = new ConcurrentHashMap<>();

    // ------------------ Networking & Thread Pools ------------------ //
    private final int port;
    private AsynchronousServerSocketChannel serverSocketChannel;
    private final FlashConfiguration config;
    // (Static and dynamic file servers omitted for brevity; assume they use similar off-heap optimizations)
    private final StaticFileServer staticFileServer;
    private final DynamicFileServer dynamicFileServer;

    // WebSocket handlers and active sessions (WebSocket frame code not shown)
    private final Map<String, WebSocketHandler> webSocketHandlers = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // Virtual thread executor for client connections.
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // ------------------ Off-Heap Buffer Management ------------------ //
    // OffHeapBufferPool uses direct ByteBuffers (off-heap memory)
    private static final int BUFFER_POOL_SIZE = 4096;   // Pool size for request buffers
    private static final int BUFFER_SIZE = 16384;         // 16KB per buffer for requests
    private static final OffHeapBufferPool REQUEST_BUFFER_POOL = new OffHeapBufferPool(BUFFER_POOL_SIZE, BUFFER_SIZE);

    // For WebSocket frames (64KB per buffer)
    private static final int WEBSOCKET_BUFFER_SIZE = 65536;
    private static final OffHeapBufferPool WEBSOCKET_BUFFER_POOL = new OffHeapBufferPool(1024, WEBSOCKET_BUFFER_SIZE);

    // ------------------ Thread-Local Decoder ------------------ //
    // Reuse a thread-local CharsetDecoder to minimize allocation overhead.
    private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(StandardCharsets.UTF_8::newDecoder);

    private final RouteRegistry routeRegistry = new RouteRegistry();

    // ------------------ Constructors ------------------ //
    public FlashServer(int port, FlashConfiguration config) {
        this.port = port;
        this.config = config;
        this.staticFileServer = new StaticFileServer(this);
        this.dynamicFileServer = new DynamicFileServer(this);
    }

    public FlashServer(int port) {
        this(port, new FlashConfiguration());
    }

    // ------------------ Server Startup ------------------ //

    /**
     * Starts the server and binds to the specified port.
     */
    public void start() {
        final long startTime = System.currentTimeMillis();
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
            acceptNextConnection();
            final long elapsedTime = System.currentTimeMillis() - startTime;
            final String[] flashPixelArt = {
                    "&#reset",
                    "      *      ",
                    "     **      ",
                    "    ***       &#reset Started &#77dd77successfully&#reset on port &#FF746C" + port + "&#reset",
                    "   *******    &#reset Startup time: &#FF746C" + elapsedTime + "&#reset ms",
                    "      ***     &#reset Serving " + routeRegistry.getLiteralRouteCount() + " literal routes, " + routeRegistry.getParameterizedRouteCount() + " parameterized routes, " + routeRegistry.getDynamicRouteCount() + " dynamic routes",
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

    /**
     * Registers a route and precompiles its matching logic.
     * Literal routes are inserted into the trie.
     */
    private void registerRoute(HttpMethod method, String fullPath, RequestHandler handler, HandlerType handlerType) {
        final RouteEntry entry = new RouteEntry(method, fullPath, handler);
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
                    final ByteBuffer buf = att.buffer;
                    buf.flip();
                    final CharsetDecoder decoder = UTF8_DECODER.get();
                    decoder.reset();
                    CharBuffer charBuffer;
                    try {
                        charBuffer = decoder.decode(buf);
                    } catch (Exception ex) {
                        PrettyLogger.withEmoji("Decoding error: " + ex.getMessage(), "❌");
                        closeSocket(att.channel);
                        REQUEST_BUFFER_POOL.release(att.buffer);
                        return;
                    }
                    att.requestData.append(charBuffer);
                    buf.clear();

                    final String fullRequestData = att.requestData.toString();
                    if (fullRequestData.contains("\r\n\r\n")) {
                        try {
                            final InetSocketAddress remoteAddress = (InetSocketAddress) att.channel.getRemoteAddress();
                            final RequestInfo reqInfo = parseRequest(fullRequestData);
                            if (isWebSocketRequest(reqInfo)) {
                                att.isWebSocket = true;
                                handleWebSocketHandshake(att.channel, reqInfo, fullRequestData);
                            } else {
                                handleHttpRequest(att, reqInfo, fullRequestData, remoteAddress);
                            }
                        } catch (Exception e) {
                            new RequestExceptionHandler(att.channel, e).handle();
                        } finally {
                            if (!att.isWebSocket) {
                                REQUEST_BUFFER_POOL.release(att.buffer);
                                closeSocket(att.channel);
                            }
                        }
                        return;
                    }
                    startRead(att);
                } else if (bytesRead == -1) {
                    REQUEST_BUFFER_POOL.release(att.buffer);
                    closeSocket(att.channel);
                } else {
                    startRead(att);
                }
            }
            @Override
            public void failed(Throwable exc, ClientAttachment att) {
                PrettyLogger.withEmoji("Read failed: " + exc.getMessage(), "❌");
                new RequestExceptionHandler(att.channel, new Exception(exc)).handle();
                REQUEST_BUFFER_POOL.release(att.buffer);
                closeSocket(att.channel);
            }
        });
    }

    private boolean isWebSocketRequest(RequestInfo reqInfo) {
        return "Upgrade".equalsIgnoreCase(reqInfo.getHeader("Connection")) &&
                "websocket".equalsIgnoreCase(reqInfo.getHeader("Upgrade"));
    }

    private void handleHttpRequest(
            ClientAttachment att,
            RequestInfo reqInfo,
            String fullRequestData,
            InetSocketAddress remoteAddress
    ) throws UnmatchedHandlerException {
        // attempt to match against routeRegistry
        RouteMatch match = routeRegistry.resolveRoute(reqInfo.getMethod(), reqInfo.getPath());

        final Map<String, String> params = match != null ? match.params() : Collections.emptyMap();
        final Request request = new Request(fullRequestData, remoteAddress, params);
        final Response response = new Response();

        if (!processMiddleware(reqInfo.getPath(), request, response)) {
            sendResponse(response, att.channel);
            return;
        }
        if (match == null) {
            throw new UnmatchedHandlerException("No handler found for " + reqInfo.getMethod() + " " + reqInfo.getPath());
        }
        final RequestHandler handler = match.entry().getHandler();
        handler.setRequestResponse(request, response);
        validateHandlerResources(handler);
        final Object responseBody = handler.handle();
        response.body(convertToResponseBody(responseBody));
        sendResponse(response, att.channel);
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
            String webSocketKey = reqInfo.getHeader("Sec-WebSocket-Key");
            if (webSocketKey == null) {
                PrettyLogger.withEmoji("WebSocket handshake failed: missing Sec-WebSocket-Key header", "❌");
                closeSocket(clientChannel);
                return;
            }

            String acceptKey = generateWebSocketAcceptKey(webSocketKey);

            // hanshake res
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                    "\r\n";

            ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));

            clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<>() {
                @Override
                public void completed(Integer bytesWritten, ByteBuffer buffer) {
                    if (buffer.hasRemaining()) {
                        clientChannel.write(buffer, buffer, this);
                    } else {
                        // handshake completed can now handle the frames
                        WebSocketSession session = new WebSocketSession(clientChannel, reqInfo, path);
                        String sessionId = UUID.randomUUID().toString();
                        activeSessions.put(sessionId, session);
                        session.setId(sessionId);

                        try {
                            handler.onOpen(session);
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

        session.getChannel().read(buffer, session, new CompletionHandler<>() {
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
                    // closed
                    handler.onClose(session, 1000, "Connection closed by client");
                    removeSession(session);
                } else {
                    // keep reading
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
            case 0x0, 0x2, 0xA:
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
            default:
                session.close(1002, "Protocol error");
                removeSession(session);
                break;
        }
    }

    private void sendPong(WebSocketSession session, byte[] payload) {
        ByteBuffer pongBuffer = ByteBuffer.allocate(2 + payload.length);
        pongBuffer.put((byte) 0x8A);
        pongBuffer.put((byte) payload.length); // payload length (as long as its not humongous lol)
        pongBuffer.put(payload);
        pongBuffer.flip();

        session.getChannel().write(pongBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                // sent pong
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
     * Process the middleware chain.
     */
    private boolean processMiddleware(String path, Request request, Response response) {
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

    /**
     * Minimal manual parsing of the request to extract method and path.
     */
    private RequestInfo parseRequest(String rawRequest) {
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

    private void sendResponse(Response response, AsynchronousSocketChannel clientChannel) {
        response.finalizeResponse();
        final ByteBuffer responseBuffer = response.getSerialized();
        clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer buf) {
                if (buf.hasRemaining()) clientChannel.write(buf, buf, this);
            }
            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                PrettyLogger.withEmoji("Error sending response: " + exc.getMessage(), "⚠️");
                closeSocket(clientChannel);
            }
        });
    }

    private void closeSocket(AsynchronousSocketChannel clientChannel) {
        try { clientChannel.close(); }
        catch (IOException e) { PrettyLogger.withEmoji("Error closing socket: " + e.getMessage(), "❌"); }
    }

    private void validateHandlerResources(RequestHandler handler) {
        for (ExpectedRequestParameter param : handler.getExpectedRequestParameters().values()) {
            param.getFieldValue();
        }
        for (ExpectedBodyField field : handler.getExpectedBodyFields().values()) {
            field.getFieldValue();
        }
        for (ExpectedBodyFile file : handler.getExpectedBodyFiles().values()) {
            file.getInputStream();
        }
    }

    private Object convertToResponseBody(Object responseBody) {
        if (responseBody == null) return "";
        if (responseBody instanceof byte[] || responseBody instanceof String) return responseBody;
        return responseBody.toString();
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
        dynamicFileServer.serve(endpoint, config);
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

    /**
     * ClientAttachment holds per-connection state.
     */
    private static class ClientAttachment {
        final ByteBuffer buffer;
        final StringBuilder requestData = new StringBuilder();
        final AsynchronousSocketChannel channel;
        boolean isWebSocket = false;
        ClientAttachment(ByteBuffer buffer, AsynchronousSocketChannel channel) {
            this.buffer = buffer;
            this.channel = channel;
        }
    }

    /**
     * MiddlewareEntry associates a middleware with an optional path prefix.
     */
    private record MiddlewareEntry(Middleware middleware, String pathPrefix){}

    /**
     * OffHeapBufferPool provides a pool of direct (off-heap) ByteBuffers.
     */
    private static class OffHeapBufferPool {
        private final int bufferSize;
        private final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
        OffHeapBufferPool(int initialSize, int bufferSize) {
            this.bufferSize = bufferSize;
            for (int i = 0; i < initialSize; i++) {
                pool.offer(ByteBuffer.allocateDirect(this.bufferSize));
            }
        }
        ByteBuffer acquire() {
            ByteBuffer buffer = pool.poll();
            if (buffer == null) {
                return ByteBuffer.allocateDirect(this.bufferSize);
            }
            buffer.clear();
            return buffer;
        }
        void release(ByteBuffer buffer) {
            pool.offer(buffer);
        }
    }
}
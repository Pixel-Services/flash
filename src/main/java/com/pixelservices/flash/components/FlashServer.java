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
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FlashServer is a lightweight and asynchronous HTTP server optimized for concurrency.
 */
public class FlashServer {
    // Compatibility maps (if needed elsewhere)
    private final ConcurrentHashMap<String, RequestHandler> routeHandlers;
    private final ConcurrentHashMap<String, HandlerType> handlerTypes;
    private final List<Middleware> middlewares = new CopyOnWriteArrayList<>();

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

    // A virtual thread executor for handling client connections concurrently.
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // Buffer Pool Configuration
    private static final int BUFFER_POOL_SIZE = 1024; // Example pool size, adjust as needed
    private static final int BUFFER_SIZE = 8192; // Increased buffer size to 8KB, adjust as needed
    private static final BufferPool REQUEST_BUFFER_POOL = new BufferPool(BUFFER_POOL_SIZE, BUFFER_SIZE);


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

    public void use(Middleware middleware) {
        middlewares.add(middleware);
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

        public ClientAttachment(ByteBuffer buffer, AsynchronousSocketChannel channel) {
            this.buffer = buffer;
            this.requestData = new StringBuilder();
            this.channel = channel;
        }
    }

    private void handleClient(AsynchronousSocketChannel clientChannel) {
        ByteBuffer buffer = REQUEST_BUFFER_POOL.acquire();
        ClientAttachment attachment = new ClientAttachment(buffer, clientChannel); // Create attachment
        startRead(attachment); // Start the initial read
    }

    private void startRead(ClientAttachment attachment) {
        attachment.buffer.clear(); // Clear buffer for new read
        attachment.channel.read(attachment.buffer, attachment, new CompletionHandler<Integer, ClientAttachment>() {
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

                            Request request = new Request(fullRequestData, (InetSocketAddress) clientAttachment.channel.getRemoteAddress(), Collections.emptyMap());
                            Response response = new Response();

                            for (Middleware m : middlewares) {
                                boolean proceed = m.process(request, response);
                                if (!proceed) {
                                    sendResponse(response, clientAttachment.channel);
                                    return;
                                }
                            }

                            RequestInfo reqInfo = parseRequest(fullRequestData);
                            RouteMatch match = findRoute(reqInfo);

                            if (match == null) {
                                throw new UnmatchedHandlerException("No handler found for " + reqInfo.getMethod() + " " + reqInfo.getPath());
                            }

                            request = new Request(fullRequestData, (InetSocketAddress) clientAttachment.channel.getRemoteAddress(), match.getParams());
                            response = new Response();
                            RequestHandler handler = match.getEntry().getHandler();
                            handler.setRequestResponse(request, response);
                            validateHandlerResources(handler);

                            Object responseBody = handler.handle();
                            response.body(convertToResponseBody(responseBody));
                            sendResponse(response, clientAttachment.channel);

                        } catch (Exception e) {
                            new RequestExceptionHandler(clientAttachment.channel, e).handle();
                        } finally {
                            REQUEST_BUFFER_POOL.release(clientAttachment.buffer);
                            closeSocket(clientAttachment.channel);
                        }
                        return; // Request processed, exit handler
                    }

                    // If not a full request yet, read more data
                    startRead(clientAttachment); // Continue reading from the channel

                } else if (bytesRead == -1) {
                    // Client closed connection gracefully
                    REQUEST_BUFFER_POOL.release(clientAttachment.buffer);
                    closeSocket(clientAttachment.channel);
                } else {
                    // bytesRead == 0 might indicate no data immediately available, but channel is still open.
                    // You might need to handle this case based on your requirements,
                    // perhaps by retrying the read or implementing a timeout.
                    startRead(clientAttachment); // Try reading again
                }
            }

            @Override
            public void failed(Throwable exc, ClientAttachment clientAttachment) {
                new RequestExceptionHandler(clientAttachment.channel, new Exception(exc)).handle();
                REQUEST_BUFFER_POOL.release(clientAttachment.buffer);
                closeSocket(clientAttachment.channel);
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
                return ByteBuffer.allocateDirect(this.bufferSize); // Allocate if pool is empty, use instance bufferSize
            }
            buffer.clear();
            return buffer;
        }

        public void release(ByteBuffer buffer) {
            pool.offer(buffer);
        }
    }
}
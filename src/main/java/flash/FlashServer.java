/*
 * Copyright 2015 - Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package flash;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import flash.route.RouteController;
import flash.swagger.FlashSwaggerConfiguration;
import flash.swagger.FlashSwaggerGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import flash.embeddedserver.EmbeddedServer;
import flash.embeddedserver.EmbeddedServers;
import flash.embeddedserver.jetty.websocket.WebSocketHandlerClassWrapper;
import flash.embeddedserver.jetty.websocket.WebSocketHandlerInstanceWrapper;
import flash.embeddedserver.jetty.websocket.WebSocketHandlerWrapper;
import flash.route.HttpMethod;
import flash.route.Routes;
import flash.route.ServletRoutes;
import flash.routematch.RouteMatch;
import flash.ssl.SslStores;
import flash.staticfiles.MimeType;
import flash.staticfiles.StaticFilesConfiguration;

import static java.util.Objects.requireNonNull;
import static flash.globalstate.ServletFlag.isRunningFromServlet;

/**
 * Represents a Flash server "session".
 * The instance should typically be named so when prefixing the 'routing' methods
 * the semantic makes sense. For example 'http' is a good variable name since when adding routes it would be:
 */
public final class FlashServer extends Routable {
    private static final Logger LOG = LoggerFactory.getLogger("flash.Flash");

    public static final int FLASH_DEFAULT_PORT = 4545;
    static final String DEFAULT_ACCEPT_TYPE = "*/*";

    private boolean initialized = false;

    private int port = FLASH_DEFAULT_PORT;
    private String ipAddress = "0.0.0.0";

    private String name;
    private SslStores sslStores;

    private Map<String, WebSocketHandlerWrapper> webSocketHandlers = null;

    private int maxThreads = -1;
    private int minThreads = -1;
    private int threadIdleTimeoutMillis = -1;
    private Optional<Long> webSocketIdleTimeoutMillis = Optional.empty();

    private EmbeddedServer server;
    private Deque<String> pathDeque = new ArrayDeque<>();
    private Routes routes;

    private final Map<String, RouteController> routeControllers = new HashMap<>();

    private CountDownLatch initLatch = new CountDownLatch(1);
    private CountDownLatch stopLatch = new CountDownLatch(0);

    private Object embeddedServerIdentifier = EmbeddedServers.defaultIdentifier();

    public final Redirect redirect;
    public final StaticFiles staticFiles;

    private final StaticFilesConfiguration staticFilesConfiguration;
    private final ExceptionMapper exceptionMapper = new ExceptionMapper();

    // default exception handler during initialization phase
    private Consumer<Exception> initExceptionHandler = (e) -> {
        System.out.println("ignite failed: " + e.getMessage());
        System.exit(100);
    };

    private boolean trustForwardHeaders = true;

    public FlashServer(String name) {
        this.name = name;
        redirect = Redirect.create(this);
        staticFiles = new StaticFiles();

        if (isRunningFromServlet()) {
            staticFilesConfiguration = StaticFilesConfiguration.servletInstance;
        } else {
            staticFilesConfiguration = StaticFilesConfiguration.create();
        }
    }

    public synchronized RouteController route(String base) {
        if (routeControllers.containsKey(base)) {
            return routeControllers.get(base);
        }
        RouteController instance = new RouteController(base, this);
        routeControllers.put(base, instance);
        return instance;
    }

    /**
     * Set the identifier used to select the EmbeddedServer;
     * null for the default.
     *
     * @param obj the identifier passed to {@link EmbeddedServers}.
     */
    public synchronized void embeddedServerIdentifier(Object obj) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        embeddedServerIdentifier = obj;
    }

    /**
     * Get the identifier used to select the EmbeddedServer;
     * null for the default.
     */
    public synchronized Object embeddedServerIdentifier() {
        return embeddedServerIdentifier;
    }

    /**
     * Set the IP address that Flash should listen on. If not called the default
     * address is '0.0.0.0'. This has to be called BEFORE any route mapping is
     * done.
     *
     * @param ipAddress The ipAddress
     * @return the object with IP address set
     */
    public synchronized FlashServer ipAddress(String ipAddress) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        this.ipAddress = ipAddress;

        return this;
    }

    public synchronized void start() {
        if (!initialized) {

            System.out.println("🚀 Starting Flash server '" + name + "'...");
            long startTime = System.currentTimeMillis();
            initializeRouteMatcher();

            if (!isRunningFromServlet()) {
                new Thread(() -> {
                    try {
                        EmbeddedServers.initialize();

                        if (embeddedServerIdentifier == null) {
                            embeddedServerIdentifier = EmbeddedServers.defaultIdentifier();
                        }

                        server = EmbeddedServers.create(embeddedServerIdentifier,
                            routes,
                            exceptionMapper,
                            staticFilesConfiguration,
                            hasMultipleHandlers());

                        server.configureWebSockets(webSocketHandlers, webSocketIdleTimeoutMillis);
                        server.trustForwardHeaders(trustForwardHeaders);

                        port = server.ignite(
                            ipAddress,
                            port,
                            sslStores,
                            maxThreads,
                            minThreads,
                            threadIdleTimeoutMillis);
                    } catch (Exception e) {
                        initExceptionHandler.accept(e);
                    }
                    try {
                        initLatch.countDown();
                        server.join();
                    } catch (InterruptedException e) {
                        LOG.error("server interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
            initialized = true;
            initLatch.countDown();
            long endTime = System.currentTimeMillis();
            System.out.println("🟢 Server started in " + (endTime - startTime) + " ms!");
        }
    }

    /**
     * Stops the Flash server and clears all routes.
     */
    public synchronized void stop() {
        if (!initialized) {
            return;
        }
        System.out.println("🛑 Stopping Flash server '" + name + "'...");
        long startTime = System.currentTimeMillis();
        server.extinguish();
        initialized = false;
        long endTime = System.currentTimeMillis();
        System.out.println("✅ Server stopped in " + (endTime - startTime) + " ms");
    }

    /**
     * Waits for the Flash server to stop.
     * <b>Warning:</b> this method should not be called from a request handler.
     */
    public void awaitStop() {
        try {
            stopLatch.await();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted by another thread");
            Thread.currentThread().interrupt();
        }
    }

    private void initiateStop() {
        stopLatch = new CountDownLatch(1);
        Thread stopThread = new Thread(() -> {
            if (server != null) {
                server.extinguish();
                initLatch = new CountDownLatch(1);
            }

            routes.clear();
            exceptionMapper.clear();
            staticFilesConfiguration.clear();
            initialized = false;
            stopLatch.countDown();
        });
        stopThread.start();
    }

    /**
     * Set the port that Flash should listen on. If not called the default port
     * is 4567. This has to be called BEFORE any route mapping is done.
     * If provided port = 0 then an arbitrary available port will be used.
     *
     * @param port The port number
     * @return the object with port set
     */
    public synchronized FlashServer port(int port) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        this.port = port;
        return this;
    }

    /**
     * Retrieves the port that Flash is listening on.
     *
     * @return The port Flash server is listening on.
     * @throws IllegalStateException when the server is not started
     */
    public synchronized int port() {
        if (initialized) {
            return port;
        } else {
            throw new IllegalStateException("This must be done AFTER route mapping has begun");
        }
    }

    /**
     * Set the connection to be secure, using the specified keystore and
     * truststore. This has to be called BEFORE any route mapping is done. You
     * have to supply a keystore file, truststore file is optional (keystore
     * will be reused). By default, client certificates are not checked.
     * This method is only relevant when using embedded Jetty servers. It should
     * not be used if you are using Servlets, where you will need to secure the
     * connection in the servlet container
     *
     * @param keystoreFile       The keystore file location as string
     * @param keystorePassword   the password for the keystore
     * @param truststoreFile     the truststore file location as string, leave null to reuse
     *                           keystore
     * @param truststorePassword the trust store password
     * @return the object with connection set to be secure
     */
    public synchronized FlashServer secure(String keystoreFile,
                                           String keystorePassword,
                                           String truststoreFile,
                                           String truststorePassword) {
        return secure(keystoreFile, keystorePassword, null, truststoreFile, truststorePassword, false);
    }

    /**
     * Set the connection to be secure, using the specified keystore and
     * truststore. This has to be called BEFORE any route mapping is done. You
     * have to supply a keystore file, truststore file is optional (keystore
     * will be reused). By default, client certificates are not checked.
     * This method is only relevant when using embedded Jetty servers. It should
     * not be used if you are using Servlets, where you will need to secure the
     * connection in the servlet container
     *
     * @param keystoreFile       The keystore file location as string
     * @param keystorePassword   the password for the keystore
     * @param certAlias          the default certificate Alias
     * @param truststoreFile     the truststore file location as string, leave null to reuse
     *                           keystore
     * @param truststorePassword the trust store password
     * @return the object with connection set to be secure
     */
    public synchronized FlashServer secure(String keystoreFile,
                                           String keystorePassword,
                                           String certAlias,
                                           String truststoreFile,
                                           String truststorePassword) {
        return secure(keystoreFile, keystorePassword, certAlias, truststoreFile, truststorePassword, false);
    }

    /**
     * Set the connection to be secure, using the specified keystore and
     * truststore. This has to be called BEFORE any route mapping is done. You
     * have to supply a keystore file, truststore file is optional (keystore
     * will be reused).
     * This method is only relevant when using embedded Jetty servers. It should
     * not be used if you are using Servlets, where you will need to secure the
     * connection in the servlet container
     *
     * @param keystoreFile       The keystore file location as string
     * @param keystorePassword   the password for the keystore
     * @param truststoreFile     the truststore file location as string, leave null to reuse
     *                           keystore
     * @param needsClientCert    Whether to require client certificate to be supplied in
     *                           request
     * @param truststorePassword the trust store password
     * @return the object with connection set to be secure
     */
    public synchronized FlashServer secure(String keystoreFile,
                                           String keystorePassword,
                                           String truststoreFile,
                                           String truststorePassword,
                                           boolean needsClientCert) {
        return secure(keystoreFile, keystorePassword, null, truststoreFile, truststorePassword, needsClientCert);
    }

    /**
     * Set the connection to be secure, using the specified keystore and
     * truststore. This has to be called BEFORE any route mapping is done. You
     * have to supply a keystore file, truststore file is optional (keystore
     * will be reused).
     * This method is only relevant when using embedded Jetty servers. It should
     * not be used if you are using Servlets, where you will need to secure the
     * connection in the servlet container
     *
     * @param keystoreFile       The keystore file location as string
     * @param keystorePassword   the password for the keystore
     * @param certAlias          the default certificate Alias
     * @param truststoreFile     the truststore file location as string, leave null to reuse
     *                           keystore
     * @param needsClientCert    Whether to require client certificate to be supplied in
     *                           request
     * @param truststorePassword the trust store password
     * @return the object with connection set to be secure
     */
    public synchronized FlashServer secure(String keystoreFile,
                                           String keystorePassword,
                                           String certAlias,
                                           String truststoreFile,
                                           String truststorePassword,
                                           boolean needsClientCert) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }

        if (keystoreFile == null) {
            throw new IllegalArgumentException(
                    "Must provide a keystore file to run secured");
        }

        sslStores = SslStores.create(keystoreFile, keystorePassword, certAlias, truststoreFile, truststorePassword, needsClientCert);
        return this;
    }

    /**
     * Configures the embedded web server's thread pool.
     *
     * @param maxThreads max nbr of threads.
     * @return the object with the embedded web server's thread pool configured
     */
    public synchronized FlashServer threadPool(int maxThreads) {
        return threadPool(maxThreads, -1, -1);
    }

    /**
     * Configures the embedded web server's thread pool.
     *
     * @param maxThreads        max nbr of threads.
     * @param minThreads        min nbr of threads.
     * @param idleTimeoutMillis thread idle timeout (ms).
     * @return the object with the embedded web server's thread pool configured
     */
    public synchronized FlashServer threadPool(int maxThreads, int minThreads, int idleTimeoutMillis) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }

        this.maxThreads = maxThreads;
        this.minThreads = minThreads;
        this.threadIdleTimeoutMillis = idleTimeoutMillis;

        return this;
    }

    /**
     * Sets the folder in classpath serving static files. Observe: this method
     * must be called BEFORE all other methods.
     *
     * @param folder the folder in classpath.
     * @return the object with folder set
     */
    public synchronized FlashServer staticFileLocation(String folder) {
        if (initialized && !isRunningFromServlet()) {
            throwBeforeRouteMappingException();
        }

        if (!staticFilesConfiguration.isStaticResourcesSet()) {
            staticFilesConfiguration.configure(folder);
        } else {
            LOG.warn("Static file location has already been set");
        }
        return this;
    }

    /**
     * Sets the external folder serving static files. <b>Observe: this method
     * must be called BEFORE all other methods.</b>
     *
     * @param externalFolder the external folder serving static files.
     * @return the object with external folder set
     */
    public synchronized FlashServer externalStaticFileLocation(String externalFolder) {
        if (initialized && !isRunningFromServlet()) {
            throwBeforeRouteMappingException();
        }

        if (!staticFilesConfiguration.isExternalStaticResourcesSet()) {
            staticFilesConfiguration.configureExternal(externalFolder);
        } else {
            LOG.warn("External static file location has already been set");
        }
        return this;
    }

    /**
     * Unmaps a particular route from the collection of those that have been previously routed.
     * Search for previously established routes using the given path and unmaps any matches that are found.
     *
     * @param path the route path
     * @return true if this is a matching route which has been previously routed
     * @throws IllegalArgumentException if path is null or blank
     */
    public boolean unmap(String path) {
        return routes.remove(path);
    }

    /**
     * Unmaps a particular route from the collection of those that have been previously routed.
     * Search for previously established routes using the given path and HTTP method, unmaps any
     * matches that are found.
     *
     * @param path       the route path
     * @param httpMethod the http method
     * @return true if this is a matching route that has been previously routed
     * @throws IllegalArgumentException if path is null or blank or if httpMethod is null, blank,
     *                                  or an invalid HTTP method
     */
    public boolean unmap(String path, String httpMethod) {
        return routes.remove(path, httpMethod);
    }

    /**
     * Maps the given path to the given WebSocket handler class.
     * <p>
     * This is currently only available in the embedded server mode.
     *
     * @param path         the WebSocket path.
     * @param handlerClass the handler class that will manage the WebSocket connection to the given path.
     */
    public void webSocket(String path, Class<?> handlerClass) {
        addWebSocketHandler(path, new WebSocketHandlerClassWrapper(handlerClass));
    }

    /**
     * Maps the given path to the given WebSocket handler instance.
     * <p>
     * This is currently only available in the embedded server mode.
     *
     * @param path    the WebSocket path.
     * @param handler the handler instance that will manage the WebSocket connection to the given path.
     */
    public void webSocket(String path, Object handler) {
        addWebSocketHandler(path, new WebSocketHandlerInstanceWrapper(handler));
    }

    private synchronized void addWebSocketHandler(String path, WebSocketHandlerWrapper handlerWrapper) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        if (isRunningFromServlet()) {
            System.out.println("WebSockets are only supported in the embedded server");
            throw new IllegalStateException("WebSockets are only supported in the embedded server");
        }
        requireNonNull(path, "WebSocket path cannot be null");
        if (webSocketHandlers == null) {
            webSocketHandlers = new HashMap<>();
        }

        webSocketHandlers.put(path, handlerWrapper);
    }

    /**
     * Sets the max idle timeout in milliseconds for WebSocket connections.
     *
     * @param timeoutMillis The max idle timeout in milliseconds.
     * @return the object with max idle timeout set for WebSocket connections
     */
    public synchronized FlashServer webSocketIdleTimeoutMillis(long timeoutMillis) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        if (isRunningFromServlet()) {
            throw new IllegalStateException("WebSockets are only supported in the embedded server");
        }
        webSocketIdleTimeoutMillis = Optional.of(timeoutMillis);
        return this;
    }

    /**
     * Maps 404 errors to the provided custom page
     *
     * @param page the custom 404 error page.
     */
    public synchronized void notFound(String page) {
        CustomErrorPages.add(404, page);
    }

    /**
     * Maps 500 internal server errors to the provided custom page
     *
     * @param page the custom 500 internal server error page.
     */
    public synchronized void internalServerError(String page) {
        CustomErrorPages.add(500, page);
    }

    /**
     * Maps 404 errors to the provided route.
     */
    public synchronized void notFound(Route route) {
        CustomErrorPages.add(404, route);
    }

    /**
     * Maps 500 internal server errors to the provided route.
     */
    public synchronized void internalServerError(Route route) {
        CustomErrorPages.add(500, route);
    }

    /**
     * Waits for the flash server to be initialized.
     * If it's already initialized will return immediately
     */
    public void awaitInitialization() {
        if (!initialized) {
    	        throw new IllegalStateException("Server has not been properly initialized");
        }

        try {
            initLatch.await();
        } catch (InterruptedException e) {
            LOG.info("Interrupted by another thread");
            Thread.currentThread().interrupt();
        }
    }

    private void throwBeforeRouteMappingException() {
        throw new IllegalStateException(
                "This must be done BEFORE route mapping has begun");
    }

    private boolean hasMultipleHandlers() {
        return webSocketHandlers != null;
    }

    /**
     * Add a path-prefix to the routes declared in the routeGroup
     * The path() method adds a path-fragment to a path-stack, adds
     * routes from the routeGroup, then pops the path-fragment again.
     * It's used for separating routes into groups, for example:
     * path("/api/email", () -> {
     * ....POST("/add",       EmailApi::addEmail);
     * ....PUT("/change",     EmailApi::changeEmail);
     * ....etc
     * });
     * Multiple path() calls can be nested.
     *
     * @param path       the path to prefix routes with
     * @param routeGroup group of routes (can also contain path() calls)
     */
    public void path(String path, RouteGroup routeGroup) {
        pathDeque.addLast(path);
        routeGroup.addRoutes();
        pathDeque.removeLast();
    }

    public String getPaths() {
        return String.join("", pathDeque);
    }
    /**
     * @return all routes information from this service
     */
    public List<RouteMatch> routes() {
        return routes.findAll();
    }

    @Override
    public void addRoute(HttpMethod httpMethod, RouteImpl route) {
        start();
        //System.out.println("Adding route: " + httpMethod + " " + getPaths() + route.getPath());
        routes.add(httpMethod, route.withPrefix(getPaths()));
    }

    @Override
    public void addFilter(HttpMethod httpMethod, FilterImpl filter) {
        start();
        routes.add(httpMethod, filter.withPrefix(getPaths()));
    }

    @Override
    @Deprecated
    public void addRoute(String httpMethod, RouteImpl route) {
        start();
        routes.add(httpMethod + " '" + getPaths() + route.getPath() + "'", route.getAcceptType(), route);
    }

    @Override
    @Deprecated
    public void addFilter(String httpMethod, FilterImpl filter) {
        start();
        routes.add(httpMethod + " '" + getPaths() + filter.getPath() + "'", filter.getAcceptType(), filter);
    }

    private void initializeRouteMatcher() {
        if (isRunningFromServlet()) {
            routes = ServletRoutes.get();
        } else {
            routes = Routes.create();
        }
    }

    /**
     * @return The approximate number of currently active threads in the embedded Jetty server
     */
    public synchronized int activeThreadCount() {
        if (server != null) {
            return server.activeThreadCount();
        }
        return 0;
    }

    //////////////////////////////////////////////////
    // EXCEPTION mapper
    //////////////////////////////////////////////////

    /**
     * Maps an exception handler to be executed when an exception occurs during routing
     *
     * @param exceptionClass the exception class
     * @param handler        The handler
     */
    public synchronized <T extends Exception> void exception(Class<T> exceptionClass, ExceptionHandler<? super T> handler) {
        // wrap
        ExceptionHandlerImpl<T> wrapper = new ExceptionHandlerImpl<>(exceptionClass) {
            @Override
            public void handle(T exception, Request request, Response response) {
                handler.handle(exception, request, response);
            }
        };

        exceptionMapper.map(exceptionClass, wrapper);
    }

    //////////////////////////////////////////////////
    // HALT methods
    //////////////////////////////////////////////////

    /**
     * Immediately stops a request within a filter or route
     * NOTE: When using this don't catch exceptions of type HaltException, or if catched, re-throw otherwise
     * halt will not work
     *
     * @return HaltException object
     */
    public HaltException halt() {
        throw new HaltException();
    }

    /**
     * Immediately stops a request within a filter or route with specified status code
     * NOTE: When using this don't catch exceptions of type HaltException, or if catched, re-throw otherwise
     * halt will not work
     *
     * @param status the status code
     * @return HaltException object with status code set
     */
    public HaltException halt(int status) {
        throw new HaltException(status);
    }

    /**
     * Immediately stops a request within a filter or route with specified body content
     * NOTE: When using this don't catch exceptions of type HaltException, or if catched, re-throw otherwise
     * halt will not work
     *
     * @param body The body content
     * @return HaltException object with body set
     */
    public HaltException halt(String body) {
        throw new HaltException(body);
    }

    /**
     * Immediately stops a request within a filter or route with specified status code and body content
     * NOTE: When using this don't catch exceptions of type HaltException, or if catched, re-throw otherwise
     * halt will not work
     *
     * @param status The status code
     * @param body   The body content
     * @return HaltException object with status and body set
     */
    public HaltException halt(int status, String body) {
        throw new HaltException(status, body);
    }

    /**
     * Sets Flash to trust the HTTP headers that are commonly used in reverse proxies.
     * More info at <a href="https://www.eclipse.org/jetty/javadoc/current/org/eclipse/jetty/server/ForwardedRequestCustomizer.html">...</a>
     */
    public synchronized FlashServer trustForwardHeaders() {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        this.trustForwardHeaders = true;

        return this;
    }

    /**
     * Sets Flash to NOT trust the HTTP headers that are commonly used in reverse proxies.
     * More info at <a href="https://www.eclipse.org/jetty/javadoc/current/org/eclipse/jetty/server/ForwardedRequestCustomizer.html">...</a>
     */
    public synchronized FlashServer untrustForwardHeaders() {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        this.trustForwardHeaders = false;

        return this;
    }

    public FlashSwaggerGenerator swagger(String endpoint, FlashSwaggerConfiguration configuration) {
        FlashSwaggerGenerator config = new FlashSwaggerGenerator(this, configuration);

        // Serve the Swagger schema JSON
        get(endpoint + "/schema.json", (req, res) -> {
            res.status(200);
            res.type("application/json");
            return config.generate();
        });

        // Serve the Swagger UI HTML
        get(endpoint, (req, res) -> {
            res.status(200);
            res.type("text/html");

            String swaggerHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Swagger UI</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.0.0/swagger-ui.css">
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.0.0/swagger-ui-bundle.js"></script>
                <script>
                    window.onload = () => {
                        const ui = SwaggerUIBundle({
                            url: "%s/schema.json",
                            dom_id: '#swagger-ui',
                        });
                    };
                </script>
            </body>
            </html>
            """;

            return swaggerHtml.formatted(endpoint);
        });

        return config;
    }


    /**
     * Overrides default exception handler during initialization phase
     *
     * @param initExceptionHandler The custom init exception handler
     */
    public void initExceptionHandler(Consumer<Exception> initExceptionHandler) {
        if (initialized) {
            throwBeforeRouteMappingException();
        }
        this.initExceptionHandler = initExceptionHandler;
    }

    /**
     * @return The route controllers for this FlashServer
     */
    public Map<String, RouteController> getRouteControllers() {
        return routeControllers;
    }

    /**
     * @return The server's name
     */
    public String getName() {
        return name;
    }

    /**
     * Provides static files utility methods.
     */
    public final class StaticFiles {

        /**
         * Sets the folder in classpath serving static files. Observe: this method
         * must be called BEFORE all other methods.
         *
         * @param folder the folder in classpath.
         */
        public void location(String folder) {
            staticFileLocation(folder);
        }

        /**
         * Sets the external folder serving static files. <b>Observe: this method
         * must be called BEFORE all other methods.</b>
         *
         * @param externalFolder the external folder serving static files.
         */
        public void externalLocation(String externalFolder) {
            externalStaticFileLocation(externalFolder);
        }

        /**
         * Puts custom headers for static resources. If the headers previously contained mapping for
         * a specific key in the provided headers map, the old value is replaced by the specified value.
         *
         * @param headers the headers to set on static resources
         */
        public void headers(Map<String, String> headers) {
            staticFilesConfiguration.putCustomHeaders(headers);
        }

        /**
         * Puts custom header for static resources. If the headers previously contained a mapping for
         * the key, the old value is replaced by the specified value.
         *
         * @param key   the key
         * @param value the value
         */
        public void header(String key, String value) {
            staticFilesConfiguration.putCustomHeader(key, value);
        }

        /**
         * Sets the expire-time for static resources
         *
         * @param seconds the expire time in seconds
         */
        @Experimental("Functionality will not be removed. The API might change")
        public void expireTime(long seconds) {
            staticFilesConfiguration.setExpireTimeSeconds(seconds);
        }

        /**
         * Maps an extension to a mime-type. This will overwrite any previous mappings.
         *
         * @param extension the extension to be mapped
         * @param mimeType  the mime-type for the extension
         */
        public void registerMimeType(String extension, String mimeType) {
            MimeType.register(extension, mimeType);
        }

        /**
         * Disables the automatic setting of Content-Type header made from a guess based on extension.
         */
        public void disableMimeTypeGuessing() {
            MimeType.disableGuessing();
        }

    }
}

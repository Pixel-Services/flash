package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.models.HandlerType;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

/**
 * DynamicFileServer serves dynamic files from a specified directory or resource folder.
 * It supports two source types: FILESYSTEM and RESOURCESTREAM. For resource streams, the
 * destination is resolved via the class loader.
 */
public class DynamicFileServer {

    private final FlashServer server;
    private final ThreadPoolExecutor executor;
    private final Map<String, byte[]> fileCache = new ConcurrentHashMap<>();
    private final Set<String> registeredRoutes = ConcurrentHashMap.newKeySet();
    private Path rootPath;
    private byte[] indexHtmlContent;
    private SourceType sourceType;

    /**
     * Constructs a DynamicFileServer instance with the specified FlashServer.
     *
     * @param server the FlashServer instance used for registering routes
     */
    public DynamicFileServer(FlashServer server) {
        this.server = server;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Serves dynamic files from a given directory or resource folder.
     * Supports both FILESYSTEM and RESOURCESTREAM source types.
     *
     * @param endpoint the base endpoint for serving dynamic files
     * @param config   the configuration for the dynamic file server
     */
    public void serve(String endpoint, DynamicFileServerConfiguration config) {
        // Remove trailing slashes from endpoint.
        endpoint = endpoint.replaceAll("/+$", "");
        this.sourceType = config.getSourceType();
        String destString = config.getDestinationPath();
        boolean isResourceStream = (sourceType == SourceType.RESOURCESTREAM);

        // Resolve the root directory.
        if (isResourceStream) {
            try {
                URL resourceUrl = getClass().getClassLoader().getResource(destString);
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Resource folder not found: " + destString);
                }
                this.rootPath = Paths.get(resourceUrl.toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to resolve resource path: " + destString, e);
            }
        } else {
            this.rootPath = Paths.get(destString);
        }

        // Load the dynamic entrypoint (e.g. index.html).
        String entrypoint = config.getDynamicEntrypoint();
        try {
            if (isResourceStream) {
                // Assumes the index is within the resource directory.
                URL indexUrl = getClass().getClassLoader().getResource(destString + "/" + entrypoint);
                if (indexUrl == null) {
                    throw new IllegalArgumentException("Index resource not found: " + entrypoint);
                }
                indexHtmlContent = Files.readAllBytes(Paths.get(indexUrl.toURI()));
            } else {
                Path indexPath = rootPath.resolve(entrypoint);
                indexHtmlContent = Files.readAllBytes(indexPath);
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to read index file: " + entrypoint, e);
        }

        // Register all static file routes.
        registerStaticRoutes(endpoint, isResourceStream, destString);

        // Register the fallback route for dynamic (e.g. SPA) routing.
        registerDynamicFallback(endpoint);

        // Start file watcher only for FILESYSTEM source.
        if (!isResourceStream && config.isEnableFileWatcher()) {
            startDirectoryWatcher(endpoint);
        }
    }

    /**
     * Scans the root directory (or resource) for regular files and registers a route for each.
     *
     * @param endpoint         the base endpoint for file routes
     * @param isResourceStream true if using resource streams; false if using the file system
     * @param destString       the original destination string from configuration
     */
    private void registerStaticRoutes(String endpoint, boolean isResourceStream, String destString) {
        try (Stream<Path> paths = getFilePaths(rootPath, isResourceStream, destString)) {
            paths.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        String relativePath = rootPath.relativize(filePath)
                                .toString()
                                .replace("\\", "/");
                        String routePath = endpoint + "/" + relativePath;
                        cacheAndRegisterFile(routePath, filePath);
                    });
        }
    }

    /**
     * Returns a stream of file paths from the given directory.
     * For resource streams, resolves the directory via the class loader.
     *
     * @param dirPath          the resolved root directory path
     * @param isResourceStream true if using resource streams; false otherwise
     * @param destString       the original destination string from configuration
     * @return a stream of file paths
     */
    private Stream<Path> getFilePaths(Path dirPath, boolean isResourceStream, String destString) {
        try {
            if (isResourceStream) {
                URL resourceUrl = getClass().getClassLoader().getResource(destString);
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Resource not found: " + destString);
                }
                return Files.walk(Paths.get(resourceUrl.toURI()));
            }
            return Files.walk(dirPath);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Error listing directory: " + dirPath, e);
        }
    }

    /**
     * Registers a fallback route that returns the index file.
     * This is useful for SPA routing or dynamic file serving.
     *
     * @param endpoint the base endpoint for the fallback route
     */
    private void registerDynamicFallback(String endpoint) {
        String fallbackRoute = endpoint.endsWith("/") ? endpoint + "*" : endpoint + "/*";
        server.registerRoute(HttpMethod.GET, fallbackRoute, (req, res) -> {
            res.type("text/html");
            return indexHtmlContent;
        });
    }

    /**
     * Starts a directory watcher to monitor file system changes and update routes dynamically.
     * Only applicable for FILESYSTEM source type.
     *
     * @param endpoint the base endpoint for file routes
     */
    private void startDirectoryWatcher(String endpoint) {
        executor.submit(() -> {
            FileServerUtility.startDirectoryWatcher(rootPath, (event, rootDir) -> processWatchEvent(event, endpoint));
        });
    }

    /**
     * Processes a file system watch event by updating routes accordingly.
     *
     * @param event    the watch event
     * @param endpoint the base endpoint for file routes
     */
    @SuppressWarnings("unchecked")
    private void processWatchEvent(WatchEvent<?> event, String endpoint) {
        Path eventPath = ((WatchEvent<Path>) event).context();
        Path fullPath = rootPath.resolve(eventPath);
        String relativePath = rootPath.relativize(fullPath).toString().replace("\\", "/");
        String routePath = endpoint + "/" + relativePath;
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            if (Files.isRegularFile(fullPath)) {
                cacheAndRegisterFile(routePath, fullPath);
            }
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            fileCache.remove(routePath);
            server.unregisterRoute(HttpMethod.GET, routePath);
            registeredRoutes.remove(routePath);
        }
    }

    /**
     * Caches the content of a file and registers a static file route if not already registered.
     *
     * @param routePath the route path to register
     * @param filePath  the file path for the content
     */
    private void cacheAndRegisterFile(String routePath, Path filePath) {
        if (!registeredRoutes.contains(routePath)) {
            try {
                byte[] fileContent;
                String fileName = filePath.toString().toLowerCase();
                if (fileName.endsWith(".html") || fileName.endsWith(".js")) {
                    String content = Files.readString(filePath);
                    // Simple rewriting logic for resource paths.
                    content = content.replaceAll("(href|src)=([\"'])/", "$1=$2" + routePath + "/");
                    fileContent = content.getBytes(StandardCharsets.UTF_8);
                } else {
                    fileContent = Files.readAllBytes(filePath);
                }
                fileCache.put(routePath, fileContent);
                registerStaticFileRoute(routePath, filePath);
            } catch (IOException e) {
                PrettyLogger.withEmoji("Error caching file: " + e.getMessage(), "⚠️");
            }
        }
    }

    /**
     * Registers a static file route that returns the cached file content.
     *
     * @param routePath the route path to register
     * @param filePath  the file path associated with the route
     */
    private void registerStaticFileRoute(String routePath, Path filePath) {
        server.registerRoute(HttpMethod.GET, routePath, (req, res) -> {
            byte[] fileContent = fileCache.get(routePath);
            if (fileContent == null) {
                res.status(404).body("File not found");
                return res.getBody();
            }
            String contentType;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException e) {
                contentType = "application/octet-stream";
            }
            res.status(200).type(contentType).body(fileContent);
            return res.getBody();
        }, HandlerType.STATIC);
        registeredRoutes.add(routePath);
    }
}

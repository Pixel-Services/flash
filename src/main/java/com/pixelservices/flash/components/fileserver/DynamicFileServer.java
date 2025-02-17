package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.models.HandlerType;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DynamicFileServer serves dynamic files from a specified directory or resource folder.
 * It supports two source types: FILESYSTEM and RESOURCESTREAM. For resource streams, files
 * are loaded via getResourceAsStream.
 */
public class DynamicFileServer {

    private final FlashServer server;
    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<String, byte[]> fileCache = new ConcurrentHashMap<>();
    private final Set<String> registeredRoutes = ConcurrentHashMap.newKeySet();
    private Path rootPath; // Used only for filesystem-based sources.
    private String resourceBasePath; // Used only for RESOURCESTREAM.
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

        // Resolve the root “directory.”
        if (isResourceStream) {
            // For resource streams we do not convert to a file system path.
            // Instead, we keep the destination string for later resource lookups.
            this.resourceBasePath = destString;
        } else {
            this.rootPath = Paths.get(destString);
        }

        // Load the dynamic entrypoint (e.g. index.html).
        String entrypoint = config.getDynamicEntrypoint();
        try {
            if (isResourceStream) {
                String fullResourcePath = destString + "/" + entrypoint;
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(fullResourcePath)) {
                    if (is == null) {
                        throw new IllegalArgumentException("Index resource not found: " + fullResourcePath);
                    }
                    indexHtmlContent = is.readAllBytes();
                }
            } else {
                Path indexPath = rootPath.resolve(entrypoint);
                indexHtmlContent = Files.readAllBytes(indexPath);
            }
        } catch (IOException e) {
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
        if (isResourceStream) {
            List<String> resourceFiles = listResourceFiles(destString);
            for (String resourcePath : resourceFiles) {
                String relativePath = resourcePath.substring(destString.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                String routePath = endpoint + "/" + relativePath;
                cacheAndRegisterResourceFile(routePath, resourcePath);
            }
        } else {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                        .forEach(filePath -> {
                            String relativePath = rootPath.relativize(filePath)
                                    .toString()
                                    .replace("\\", "/");
                            String routePath = endpoint + "/" + relativePath;
                            cacheAndRegisterFile(routePath, filePath);
                        });
            } catch (IOException e) {
                throw new RuntimeException("Error listing directory: " + rootPath, e);
            }
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
     * Caches the content of a file (from the file system) and registers a static file route.
     *
     * @param routePath the route path to register
     * @param filePath  the file system path for the content
     */
    private void cacheAndRegisterFile(String routePath, Path filePath) {
        if (!registeredRoutes.contains(routePath)) {
            try {
                byte[] fileContent;
                String fileName = filePath.toString().toLowerCase();
                if (fileName.endsWith(".html") || fileName.endsWith(".js")) {
                    String content = Files.readString(filePath);
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
     * Registers a static file route (for file system–based files) that returns the cached file content.
     *
     * @param routePath the route path to register
     * @param filePath  the file system path associated with the route
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

    /**
     * Caches the content of a resource file (loaded via getResourceAsStream) and registers a static file route.
     *
     * @param routePath    the route path to register
     * @param resourcePath the full resource path (e.g. "static/index.html")
     */
    private void cacheAndRegisterResourceFile(String routePath, String resourcePath) {
        if (!registeredRoutes.contains(routePath)) {
            try {
                byte[] fileContent;
                String lowerCaseResource = resourcePath.toLowerCase();
                if (lowerCaseResource.endsWith(".html") || lowerCaseResource.endsWith(".js")) {
                    String content;
                    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                        if (is == null) {
                            throw new IllegalArgumentException("Resource not found: " + resourcePath);
                        }
                        content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    // Simple rewriting logic for resource paths.
                    content = content.replaceAll("(href|src)=([\"'])/", "$1=$2" + routePath + "/");
                    fileContent = content.getBytes(StandardCharsets.UTF_8);
                } else {
                    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                        if (is == null) {
                            throw new IllegalArgumentException("Resource not found: " + resourcePath);
                        }
                        fileContent = is.readAllBytes();
                    }
                }
                fileCache.put(routePath, fileContent);
                registerStaticResourceFileRoute(routePath, resourcePath);
            } catch (IOException e) {
                PrettyLogger.withEmoji("Error caching resource file: " + e.getMessage(), "⚠️");
            }
        }
    }

    /**
     * Registers a static file route (for resource-based files) that returns the cached file content.
     *
     * @param routePath    the route path to register
     * @param resourcePath the resource path associated with the route
     */
    private void registerStaticResourceFileRoute(String routePath, String resourcePath) {
        server.registerRoute(HttpMethod.GET, routePath, (req, res) -> {
            byte[] fileContent = fileCache.get(routePath);
            if (fileContent == null) {
                res.status(404).body("File not found");
                return res.getBody();
            }
            String contentType = getContentType(resourcePath);
            res.status(200).type(contentType).body(fileContent);
            return res.getBody();
        }, HandlerType.STATIC);
        registeredRoutes.add(routePath);
    }

    /**
     * Returns a simple content type mapping based on file extension.
     *
     * @param fileName the name of the file or resource
     * @return a MIME type string
     */
    private String getContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".js")) return "application/javascript";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        // Add more mappings as necessary.
        return "application/octet-stream";
    }

    /**
     * Lists resource files under the specified resource folder.
     * Works for both running in an IDE (file protocol) and packaged in a JAR.
     *
     * @param path the resource folder path (e.g. "static")
     * @return a list of resource file names (e.g. "static/index.html", "static/css/style.css")
     */
    private List<String> listResourceFiles(String path) {
        List<String> fileList = new ArrayList<>();
        try {
            URL url = getClass().getClassLoader().getResource(path);
            if (url == null) {
                throw new IllegalArgumentException("Resource path not found: " + path);
            }
            if (url.getProtocol().equals("jar")) {
                String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()))) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();
                        if (entryName.startsWith(path) && !entry.isDirectory()) {
                            fileList.add(entryName);
                        }
                    }
                }
            } else if (url.getProtocol().equals("file")) {
                Path dir = Paths.get(url.toURI());
                try (Stream<Path> walk = Files.walk(dir)) {
                    fileList = walk.filter(Files::isRegularFile)
                            .map(p -> {
                                Path rel = dir.relativize(p);
                                return path + "/" + rel.toString().replace("\\", "/");
                            })
                            .collect(Collectors.toList());
                }
            } else {
                throw new UnsupportedOperationException("Unsupported protocol: " + url.getProtocol());
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Error listing resource files for path: " + path, e);
        }
        return fileList;
    }
}

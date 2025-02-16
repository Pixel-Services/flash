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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * StaticFileServer serves static files from a specified directory and registers routes dynamically.
 * It supports file watching for changes in the directory to update routes in real-time.
 */
public class StaticFileServer {

    private final FlashServer server;
    private final ThreadPoolExecutor executor;
    private final Map<String, String> fileHashes = new ConcurrentHashMap<>();
    private final Map<String, byte[]> fileCache = new ConcurrentHashMap<>();
    private final Set<String> registeredRoutes = new HashSet<>();

    /**
     * Constructs a StaticFileServer instance with the specified FlashServer.
     *
     * @param server the FlashServer instance to register routes
     */
    public StaticFileServer(FlashServer server) {
        this.server = server;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Serves static files from the specified directory, registers routes, and optionally watches the directory.
     *
     * @param endpoint the base endpoint for serving static files
     * @param config   the configuration for the static file server
     */
    public void serve(String endpoint, StaticFileServerConfiguration config) {
        // Get the destination path as a string.
        String dirPathStr = config.getDestinationPath();
        SourceType sourceType = config.getSourceType();
        Path dirPath;

        if (sourceType == SourceType.RESOURCESTREAM) {
            try {
                URL resourceUrl = getClass().getClassLoader().getResource(dirPathStr);
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Resource folder '" + dirPathStr + "' not found.");
                }
                dirPath = Paths.get(resourceUrl.toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to resolve static resource path", e);
            }
        } else {
            dirPath = Paths.get(dirPathStr);
        }

        validateDirectoryPath(dirPath, sourceType);
        boolean isResourceStream = sourceType == SourceType.RESOURCESTREAM;

        try (Stream<Path> paths = getFilePaths(dirPath, isResourceStream, dirPathStr)) {
            paths.parallel().forEach(filePath -> registerFileRoute(filePath, dirPath, endpoint, config));
            if (config.isEnableFileWatcher() && !isResourceStream) {
                startDirectoryWatcher(endpoint, dirPath);
            }
        }
    }

    private void registerFileRoute(Path filePath, Path dirPath, String endpoint, StaticFileServerConfiguration config) {
        if (Files.isRegularFile(filePath)) {
            String relativePath = dirPath.relativize(filePath).toString().replace("\\", "/");
            if (relativePath.startsWith("../")) {
                relativePath = relativePath.replaceFirst("^\\.\\./", "");
            }
            String routePath = endpoint + "/" + relativePath;
            if (!fileHashes.containsKey(routePath)) {
                cacheFileContent(filePath, routePath, endpoint);
                registerStaticFileRoute(routePath, filePath);
            }
            if (relativePath.equals("index.html") && config.isEnableIndexRedirect()) {
                server.redirect(endpoint, routePath);
            }
        }
    }

    /**
     * Registers a static file route with the server.
     *
     * @param routePath the route path to register
     * @param filePath  the file path associated with the route
     */
    private void registerStaticFileRoute(String routePath, Path filePath) {
        if (registeredRoutes.contains(routePath)) {
            return;
        }
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
     * Caches the content of a file and rewrites URLs in HTML and JavaScript files.
     *
     * @param filePath  the path to the file
     * @param routePath the route path to cache
     * @param endpoint  the base endpoint for serving files
     */
    private void cacheFileContent(Path filePath, String routePath, String endpoint) {
        try {
            byte[] fileContent;
            String fileName = filePath.toString().toLowerCase();
            if (fileName.endsWith(".html")) {
                String originalHtml = Files.readString(filePath);
                String rewrittenHtml = rewriteHtml(originalHtml, endpoint);
                fileContent = rewrittenHtml.getBytes(StandardCharsets.UTF_8);
            } else if (fileName.endsWith(".js")) {
                String originalJs = Files.readString(filePath);
                String rewrittenJs = rewriteJs(originalJs, endpoint);
                fileContent = rewrittenJs.getBytes(StandardCharsets.UTF_8);
            } else {
                fileContent = Files.readAllBytes(filePath);
            }
            fileCache.put(routePath, fileContent);
        } catch (IOException e) {
            PrettyLogger.withEmoji("Error caching file: " + e.getMessage(), "⚠️");
        }
    }

    /**
     * Rewrites JavaScript content by prefixing the endpoint if not already present.
     * Assumes endpoint is normalized (no trailing slash).
     */
    private String rewriteJs(String jsContent, String endpoint) {
        Pattern pattern = Pattern.compile("([\"'])(/[^\"']*\\.[^\"']*)([\"'])");
        Matcher matcher = pattern.matcher(jsContent);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String quote = matcher.group(1);
            String assetPath = matcher.group(2);
            String endQuote = matcher.group(3);
            String newRoute = rewriteRoute(assetPath, endpoint);
            String replacement = quote + newRoute + endQuote;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Rewrites HTML content by prefixing the endpoint if not already present.
     * Assumes endpoint is normalized (no trailing slash).
     */
    private String rewriteHtml(String htmlContent, String endpoint) {
        htmlContent = htmlContent.replaceAll("href=\"(?!\\Q" + endpoint + "\\E/)(/[^\"']*)\"", "href=\"" + endpoint + "$1\"")
                .replaceAll("src=\"(?!\\Q" + endpoint + "\\E/)(/[^\"']*)\"", "src=\"" + endpoint + "$1\"");
        return htmlContent;
    }

    /**
     * Rewrites a route by prefixing the endpoint if not already present.
     * Assumes endpoint is normalized (no trailing slash).
     */
    private String rewriteRoute(String route, String endpoint) {
        if (route.startsWith(endpoint)) {
            return route;
        }
        return endpoint + route;
    }

    /**
     * Validates the directory path for serving static files.
     *
     * @param dirPath    the directory path to validate
     * @param sourceType the source type of the directory
     */
    private void validateDirectoryPath(Path dirPath, SourceType sourceType) {
        if (dirPath == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }
        if (sourceType == SourceType.FILESYSTEM && !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Provided path is not a directory: " + dirPath);
        }
    }

    /**
     * Returns a stream of file paths in the specified directory.
     *
     * @param dirPath          the directory path to list
     * @param isResourceStream whether to use the resource stream
     * @param dirPathStr       the original directory path string from configuration
     * @return a stream of file paths in the directory
     */
    private Stream<Path> getFilePaths(Path dirPath, boolean isResourceStream, String dirPathStr) {
        try {
            if (isResourceStream) {
                URL resourceUrl = getClass().getClassLoader().getResource(dirPathStr);
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Resource not found: " + dirPathStr);
                }
                PrettyLogger.withEmoji("Resource URL: " + resourceUrl, "🔗");
                return Files.walk(Paths.get(resourceUrl.toURI()));
            }
            return Files.walk(dirPath);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Error listing directory: " + dirPath, e);
        }
    }

    /**
     * Starts watching the specified directory for changes and dynamically updates routes.
     *
     * @param endpoint the base endpoint for serving files
     * @param dirPath  the directory to watch for changes
     */
    private void startDirectoryWatcher(String endpoint, Path dirPath) {
        executor.submit(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                dirPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

                PrettyLogger.withEmoji("Watching directory for changes: " + dirPath, "👀");

                while (true) {
                    WatchKey key = watchService.take();
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            processWatchEvents(event, endpoint, dirPath);
                        }
                        key.reset();
                    } else {
                        PrettyLogger.withEmoji("Key is null, stopping watcher.", "⚠️");
                        break;
                    }
                }
            } catch (IOException | InterruptedException e) {
                PrettyLogger.withEmoji("Error watching directory: " + e.getMessage(), "⚠️");
            }
        });
    }

    /**
     * Processes watch events for file creation, modification, and deletion.
     *
     * @param event    the watch event to process
     * @param endpoint the base endpoint for serving files
     * @param dirPath  the directory being watched
     */
    private void processWatchEvents(WatchEvent<?> event, String endpoint, Path dirPath) {
        try {
            Path eventPath = dirPath.resolve((Path) event.context());
            if (eventPath.getFileName().toString().endsWith("~") || !Files.isRegularFile(eventPath)) {
                return;
            }
            String relativePath = dirPath.relativize(eventPath).toString().replace("\\", "/");
            String routePath = endpoint + "/" + relativePath;
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                handleFileCreateOrModify(eventPath, routePath, endpoint);
            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                handleFileDelete(routePath);
            }
        } catch (Exception e) {
            PrettyLogger.withEmoji("Error processing watch event: " + e.getMessage(), "⚠️");
        }
    }

    /**
     * Handles file creation or modification by updating the route and cache entry.
     *
     * @param filePath  the path to the file
     * @param routePath the route path to update
     * @param endpoint  the base endpoint for serving files
     */
    private void handleFileCreateOrModify(Path filePath, String routePath, String endpoint) {
        String newHash = computeFileHash(filePath);
        String existingHash = fileHashes.get(routePath);

        if (existingHash == null || !existingHash.equals(newHash)) {
            fileHashes.put(routePath, newHash);
            cacheFileContent(filePath, routePath, endpoint);
            registerStaticFileRoute(routePath, filePath);
        }
    }

    /**
     * Handles file deletion by removing the route and cache entry.
     *
     * @param routePath the route path to remove
     */
    private void handleFileDelete(String routePath) {
        fileHashes.remove(routePath);
        fileCache.remove(routePath);
        server.unregisterRoute(HttpMethod.GET, routePath);
    }

    /**
     * Computes the SHA-256 hash of a file.
     *
     * @param filePath the path to the file
     * @return the SHA-256 hash of the file
     */
    private String computeFileHash(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);
            StringBuilder hashString = new StringBuilder();
            for (byte b : hashBytes) {
                hashString.append(String.format("%02x", b));
            }
            return hashString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

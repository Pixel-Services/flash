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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * StaticFileServer serves static files from a specified directory and registers routes dynamically.
 * It supports file watching for changes in the directory to update routes in real-time.
 * This version supports resources packaged inside JAR files using getResourceAsStream.
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
        String dirPathStr = config.getDestinationPath();
        SourceType sourceType = config.getSourceType();

        if (sourceType == SourceType.RESOURCESTREAM) {
            // For resources inside a jar (or on the classpath), list files via a helper method.
            List<String> resourceFiles = listResourceFiles(dirPathStr);
            // Process each resource file (using parallel stream if desired)
            resourceFiles.parallelStream().forEach(resourceFullPath -> {
                // Remove the configured folder prefix to obtain a relative path.
                String relativePath = resourceFullPath.substring(dirPathStr.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                registerResourceRoute(resourceFullPath, relativePath, endpoint, config);
            });
        } else {
            // For a file system directory, use Paths and Files.walk.
            Path dirPath = Paths.get(dirPathStr);
            validateDirectoryPath(dirPath, sourceType);
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.parallel().forEach(filePath -> registerFileRoute(filePath, dirPath, endpoint, config));
                if (config.isEnableFileWatcher()) {
                    startDirectoryWatcher(endpoint, dirPath);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error listing directory: " + dirPath, e);
            }
        }
    }

    /**
     * Registers a file system–based static file route.
     */
    private void registerFileRoute(Path filePath, Path dirPath, String endpoint, StaticFileServerConfiguration config) {
        if (Files.isRegularFile(filePath)) {
            String relativePath = dirPath.relativize(filePath).toString().replace("\\", "/");
            if (relativePath.startsWith("../")) {
                relativePath = relativePath.replaceFirst("^\\.\\./", "");
            }
            String routePath = endpoint + "/" + relativePath;
            if (!fileHashes.containsKey(routePath)) {
                cacheFileContent(filePath, routePath, endpoint, false, null);
                registerStaticFileRoute(routePath, filePath, false, null);
            }
            if (relativePath.equals("index.html") && config.isEnableIndexRedirect()) {
                server.redirect(endpoint, routePath);
            }
        }
    }

    /**
     * Registers a resource-based static file route (for files loaded via getResourceAsStream).
     *
     * @param resourceName the full resource name (e.g. "static/index.html")
     * @param relativePath the path relative to the resource folder
     * @param endpoint     the base endpoint for serving files
     * @param config       the static file server configuration
     */
    private void registerResourceRoute(String resourceName, String relativePath, String endpoint, StaticFileServerConfiguration config) {
        String routePath = endpoint + "/" + relativePath;
        if (!fileHashes.containsKey(routePath)) {
            cacheFileContent(null, routePath, endpoint, true, resourceName);
            registerStaticFileRoute(routePath, null, true, resourceName);
        }
        if (relativePath.equals("index.html") && config.isEnableIndexRedirect()) {
            server.redirect(endpoint, routePath);
        }
    }

    /**
     * Registers a static file route with the server.
     *
     * @param routePath       the route path to register
     * @param filePath        the file system path (null if resource-based)
     * @param isResourceStream whether the file is loaded from a resource
     * @param resourceName    the resource name (if resource-based)
     */
    private void registerStaticFileRoute(String routePath, Path filePath, boolean isResourceStream, String resourceName) {
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
            if (isResourceStream) {
                contentType = getContentType(resourceName);
            } else {
                try {
                    contentType = Files.probeContentType(filePath);
                } catch (IOException e) {
                    contentType = "application/octet-stream";
                }
            }
            res.status(200).type(contentType).body(fileContent);
            return res.getBody();
        }, HandlerType.STATIC);

        registeredRoutes.add(routePath);
    }

    /**
     * Caches the content of a file and rewrites URLs in HTML and JavaScript files.
     *
     * @param filePath         the file system path (or null if resource)
     * @param routePath        the route path used for caching
     * @param endpoint         the base endpoint for serving files
     * @param isResourceStream whether to load from a resource
     * @param resourceName     the resource name (if resource-based)
     */
    private void cacheFileContent(Path filePath, String routePath, String endpoint,
                                  boolean isResourceStream, String resourceName) {
        try {
            byte[] fileContent;
            String fileName = isResourceStream ? resourceName.toLowerCase() : filePath.toString().toLowerCase();
            if (fileName.endsWith(".html")) {
                String originalHtml;
                if (isResourceStream) {
                    originalHtml = new String(getResourceBytes(resourceName), StandardCharsets.UTF_8);
                } else {
                    originalHtml = Files.readString(filePath);
                }
                String rewrittenHtml = rewriteHtml(originalHtml, endpoint);
                fileContent = rewrittenHtml.getBytes(StandardCharsets.UTF_8);
            } else if (fileName.endsWith(".js")) {
                String originalJs;
                if (isResourceStream) {
                    originalJs = new String(getResourceBytes(resourceName), StandardCharsets.UTF_8);
                } else {
                    originalJs = Files.readString(filePath);
                }
                String rewrittenJs = rewriteJs(originalJs, endpoint);
                fileContent = rewrittenJs.getBytes(StandardCharsets.UTF_8);
            } else {
                if (isResourceStream) {
                    fileContent = getResourceBytes(resourceName);
                } else {
                    fileContent = Files.readAllBytes(filePath);
                }
            }
            fileCache.put(routePath, fileContent);
        } catch (IOException e) {
            PrettyLogger.withEmoji("Error caching file: " + e.getMessage(), "⚠️");
        }
    }

    /**
     * Reads all bytes from a resource using getResourceAsStream.
     *
     * @param resourceName the resource name (full path)
     * @return the file content as a byte array
     * @throws IOException if an I/O error occurs
     */
    private byte[] getResourceBytes(String resourceName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            return is.readAllBytes();
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
        htmlContent = htmlContent.replaceAll("href=\"(?!\\Q" + endpoint + "\\E/)(/[^\"']*)\"",
                        "href=\"" + endpoint + "$1\"")
                .replaceAll("src=\"(?!\\Q" + endpoint + "\\E/)(/[^\"']*)\"",
                        "src=\"" + endpoint + "$1\"");
        return htmlContent;
    }

    /**
     * Rewrites a route by prefixing the endpoint if not already present.
     * Assumes endpoint is normalized (no trailing slash).
     */
    private String rewriteRoute(String route, String endpoint) {
        return FileServerUtility.rewriteRoute(route, endpoint);
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
     * Lists resource files under the specified resource folder.
     * Works for both file-system (e.g. running in IDE) and jar protocols.
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
                try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
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
            cacheFileContent(filePath, routePath, endpoint, false, null);
            registerStaticFileRoute(routePath, filePath, false, null);
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

    /**
     * A simple content type mapping based on file name extension.
     *
     * @param fileName the name of the file (or resource)
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
}

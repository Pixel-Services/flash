package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.models.HandlerType;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.utils.LogType;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

public class StaticFileServer {

    private final FlashServer server;
    private final ThreadPoolExecutor executor;
    private final AssetCache assetCache;
    private final Set<String> registeredRoutes = new HashSet<>();

    public StaticFileServer(FlashServer server) {
        this.server = server;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
        this.assetCache = new AssetCache();
    }

    /**
     * Serves static files from the specified directory, registers routes, and optionally watches the directory.
     */
    public void serve(String endpoint, StaticFileServerConfiguration config) {
        Path dirPath = config.getDestinationPath();
        boolean isSPA = FileServerUtility.autodetectSPA(dirPath);
        if(isSPA){
            PrettyLogger.withEmoji("Auto-detected modern SPA in a static context. If you are using a frontend router, please use &#00ffffFlashServer#serveDynamic &#resetinstead.", "⚠️", LogType.WARN);
        }
        SourceType sourceType = config.getSourceType();
        if (sourceType == SourceType.RESOURCESTREAM) {
            try {
                URL resourceUrl = getClass().getClassLoader().getResource("static");
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Resource folder 'static' not found.");
                }
                dirPath = Paths.get(resourceUrl.toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to resolve static resource path", e);
            }
        }
        validateDirectoryPath(dirPath, sourceType);
        boolean isResourceStream = sourceType == SourceType.RESOURCESTREAM;
        try (Stream<Path> paths = getFilePaths(dirPath, isResourceStream)) {
            Path finalDirPath = dirPath;
            paths.parallel().forEach(filePath -> registerFileRoute(filePath, finalDirPath, endpoint, config));
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
            if (!assetCache.contains(routePath)) {
                assetCache.cacheFile(filePath, routePath, endpoint);
                registerStaticFileRoute(routePath, filePath);
            }
            if (relativePath.equals("index.html") && config.isEnableIndexRedirect()) {
                server.redirect(endpoint, routePath);
            }
        }
    }

    /**
     * Registers a static file route with the server.
     */
    private void registerStaticFileRoute(String routePath, Path filePath) {
        if (registeredRoutes.contains(routePath)) {
            return;
        }
        server.registerRoute(HttpMethod.GET, routePath, (req, res) -> {
            byte[] fileContent = assetCache.getFileContent(routePath);
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

    private void validateDirectoryPath(Path dirPath, SourceType sourceType) {
        if (dirPath == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }
        if (sourceType == SourceType.FILESYSTEM && !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Provided path is not a directory: " + dirPath);
        }
    }

    private Stream<Path> getFilePaths(Path dirPath, boolean isResourceStream) {
        try {
            if (isResourceStream) {
                String resourcePath = dirPath.toString().replace("\\", "/");
                if (!resourcePath.startsWith("/")) {
                    resourcePath = "/" + resourcePath;
                }
                URL resourceUrl = getClass().getClassLoader().getResource("static");
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Resource not found: " + resourcePath);
                }
                PrettyLogger.withEmoji("Resource URL: " + resourceUrl, "🔗");
                return Files.walk(Paths.get(resourceUrl.toURI()));
            }
            return Files.walk(dirPath);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Error listing directory: " + dirPath, e);
        }
    }

    private void startDirectoryWatcher(String endpoint, Path dirPath) {
        executor.submit(() -> {
            FileServerUtility.startDirectoryWatcher(dirPath, (event, rootDir) -> {
                try {
                    Path eventPath = rootDir.resolve((Path) event.context());
                    if (eventPath.getFileName().toString().endsWith("~") || !Files.isRegularFile(eventPath)) {
                        return;
                    }
                    String relativePath = rootDir.relativize(eventPath).toString().replace("\\", "/");
                    String routePath = endpoint + "/" + relativePath;
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                            kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        handleFileCreateOrModify(eventPath, routePath, endpoint);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        handleFileDelete(routePath);
                    }
                } catch (Exception e) {
                    PrettyLogger.withEmoji("Error processing watch event: " + e.getMessage(), "⚠️");
                }
            });
        });
    }

    private void handleFileCreateOrModify(Path filePath, String routePath, String endpoint) {
        String newHash = assetCache.computeFileHash(filePath);
        String existingHash = assetCache.getFileHash(routePath);
        if (existingHash == null || !existingHash.equals(newHash)) {
            assetCache.cacheFile(filePath, routePath, endpoint);
            registerStaticFileRoute(routePath, filePath);
        }
    }

    private void handleFileDelete(String routePath) {
        assetCache.remove(routePath);
        server.unregisterRoute(HttpMethod.GET, routePath);
        registeredRoutes.remove(routePath);
    }
}

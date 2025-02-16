package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.models.HandlerType;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.utils.LogType;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

public class DynamicFileServer {
    private final FlashServer server;
    private final ThreadPoolExecutor executor;
    private final Map<String, byte[]> fileCache = new ConcurrentHashMap<>();
    private final Set<String> registeredRoutes = ConcurrentHashMap.newKeySet();
    private Path rootPath;
    private byte[] indexHtmlContent;
    private boolean isResourceStream;

    public DynamicFileServer(FlashServer server) {
        this.server = server;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    public void serve(String endpoint, DynamicFileServerConfiguration config) {
        endpoint = endpoint.replaceAll("/+$", "");
        SourceType sourceType = config.getSourceType();
        Path dirPath = config.getDestinationPath();

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
        this.rootPath = dirPath;
        this.isResourceStream = sourceType == SourceType.RESOURCESTREAM;

        Path indexPath = rootPath.resolve(config.getDynamicEntrypoint());
        boolean isSPA = FileServerUtility.autodetectSPA(rootPath);
        if (isSPA) {
            PrettyLogger.withEmoji("Auto-detected modern SPA in dynamic context!", "⚠️", LogType.WARN);
        }

        try {
            indexHtmlContent = loadFileContent(indexPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read index file at: " + indexPath, e);
        }

        registerStaticRoutes(endpoint);
        registerDynamicFallback(endpoint);

        if (config.isEnableFileWatcher() && !isResourceStream) {
            startDirectoryWatcher(endpoint);
        }
    }

    private void validateDirectoryPath(Path dirPath, SourceType sourceType) {
        if (dirPath == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }
        if (sourceType == SourceType.FILESYSTEM && !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Provided path is not a directory: " + dirPath);
        }
    }

    private void registerStaticRoutes(String endpoint) {
        try (Stream<Path> paths = getFilePaths(rootPath, isResourceStream)) {
            paths.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        String relativePath = rootPath.relativize(filePath)
                                .toString()
                                .replace("\\", "/");
                        String routePath = endpoint + "/" + relativePath;
                        cacheAndRegisterFile(routePath, filePath);
                    });
        } catch (IOException e) {
            PrettyLogger.withEmoji("Error scanning static files: " + e.getMessage(), "⚠️");
        }
    }

    private Stream<Path> getFilePaths(Path dirPath, boolean isResourceStream) throws IOException {
        if (isResourceStream) {
            try {
                URL resourceUrl = getClass().getClassLoader().getResource("static");
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Resource folder 'static' not found.");
                }
                PrettyLogger.withEmoji("Resource URL: " + resourceUrl, "🔗");
                return Files.walk(Paths.get(resourceUrl.toURI()));
            } catch (URISyntaxException e) {
                throw new IOException("Failed to resolve resource URL", e);
            }
        }
        return Files.walk(dirPath);
    }

    private void registerDynamicFallback(String endpoint) {
        String fallbackRoute = endpoint.endsWith("/") ? endpoint + "*" : endpoint + "/*";
        server.registerRoute(HttpMethod.GET, fallbackRoute, (req, res) -> {
            res.type("text/html");
            return indexHtmlContent;
        });
    }

    private void startDirectoryWatcher(String endpoint) {
        executor.submit(() -> {
            FileServerUtility.startDirectoryWatcher(rootPath, (event, rootDir) -> {
                processWatchEvent(event, endpoint);
            });
        });
    }

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

    private void cacheAndRegisterFile(String routePath, Path filePath) {
        if (!registeredRoutes.contains(routePath)) {
            try {
                byte[] fileContent = loadFileContent(filePath);
                fileCache.put(routePath, fileContent);
                registerStaticFileRoute(routePath, filePath);
            } catch (IOException e) {
                PrettyLogger.withEmoji("Error caching file: " + e.getMessage(), "⚠️");
            }
        }
    }

    private byte[] loadFileContent(Path filePath) throws IOException {
        if (isResourceStream) {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath.toString())) {
                if (inputStream == null) {
                    throw new IOException("Resource not found: " + filePath);
                }
                return inputStream.readAllBytes();
            }
        } else {
            return Files.readAllBytes(filePath);
        }
    }

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

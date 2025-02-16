package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.models.HandlerType;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.utils.LogType;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public DynamicFileServer(FlashServer server) {
        this.server = server;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    public void serve(String endpoint, DynamicFileServerConfiguration config) {
        endpoint = endpoint.replaceAll("/+$", "");
        this.rootPath = config.getDestinationPath();
        Path indexPath = rootPath.resolve(config.getDynamicEntrypoint());
        boolean isSPA = FileServerUtility.autodetectSPA(rootPath);

        if(isSPA){
            PrettyLogger.withEmoji("Auto-detected modern SPA in a dynamic server context, make sure to configure your frontend router to handle root correctly!", "⚠️", LogType.WARN);
        }

        try {
            indexHtmlContent = Files.readAllBytes(indexPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read index file at: " + indexPath, e);
        }

        registerStaticRoutes(endpoint);
        registerDynamicFallback(endpoint);

        if (config.isEnableFileWatcher()) {
            startDirectoryWatcher(endpoint);
        }
    }

    private void registerStaticRoutes(String endpoint) {
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
            PrettyLogger.withEmoji("Error scanning static files: " + e.getMessage(), "⚠️");
        }
    }

    private void registerDynamicFallback(String endpoint) {
        String fallbackRoute = endpoint.endsWith("/") ? endpoint + "*" : endpoint + "/*";

        if (!endpoint.endsWith("/*")) {
            PrettyLogger.withEmoji("&#FF746CDynamicFileServer endpoint should end with '/*' to handle all routes. Enforcing it as it's required for dynamic routing.", "‼️", LogType.ERROR);
        }

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

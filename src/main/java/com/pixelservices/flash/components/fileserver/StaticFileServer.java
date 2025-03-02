package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.models.HandlerType;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

/**
 * StaticFileServer serves static files efficiently using an asset cache.
 */
public class StaticFileServer {

    private final FlashServer server;
    private final ThreadPoolExecutor executor;
    private final AssetCache assetCache = new AssetCache(); // Unified asset cache
    private Path rootPath;

    /**
     * Constructs a StaticFileServer instance with the specified FlashServer.
     *
     * @param server the FlashServer instance used for registering routes
     */
    public StaticFileServer(FlashServer server) {
        this.server = server;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Serves static files from the specified directory.
     *
     * @param endpoint the base endpoint for serving static files
     * @param config   the configuration for the static file server
     */
    public void serve(String endpoint, StaticFileServerConfiguration config) {
        this.rootPath = Paths.get(config.getDestinationPath());
        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Provided path is not a directory: " + rootPath);
        }

        // Register all static file routes.
        registerStaticRoutes(endpoint);

        // Start file watcher if enabled.
        if (config.isEnableFileWatcher()) {
            startDirectoryWatcher(endpoint);
        }
    }

    /**
     * Scans the root directory for regular files and registers a route for each.
     *
     * @param endpoint the base endpoint for file routes
     */
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
            throw new RuntimeException("Error listing directory: " + rootPath, e);
        }
    }

    /**
     * Starts a directory watcher to monitor file system changes and update routes dynamically.
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
        Path fullPath = Path.of(rootPath.resolve(eventPath).toString().replace("~", ""));
        String relativePath = rootPath.relativize(fullPath).toString().replace("\\", "/");
        String routePath = endpoint + "/" + relativePath;
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            if (Files.isRegularFile(fullPath)) {
                cacheAndRegisterFile(routePath, fullPath);
            }
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            assetCache.remove(routePath);
            server.unregisterRoute(HttpMethod.GET, routePath);
        }
    }

    /**
     * Caches the content of a file and registers a static file route.
     *
     * @param routePath the route path to register
     * @param filePath  the file system path for the content
     */
    private void cacheAndRegisterFile(String routePath, Path filePath) {
        if (!assetCache.contains(routePath)) {
            try {
                byte[] fileContent = Files.readAllBytes(filePath);
                assetCache.put(routePath, fileContent);
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
     * @param filePath  the file system path associated with the route
     */
    private void registerStaticFileRoute(String routePath, Path filePath) {
        server.registerRoute(HttpMethod.GET, routePath, (req, res) -> {
            byte[] fileContent = assetCache.get(routePath);

            if (fileContent == null) {
                res.status(404).body("File not found");
                return res.getBody();
            }

            String contentType = FileServerUtility.getContentType(filePath.toString());

            // this enables partial content requests (range requests) for stuff like in-browser video/content streaming
            String rangeHeader = req.header("Range");
            if (rangeHeader != null) {
                try {
                    String[] ranges = rangeHeader.replace("bytes=", "").split("-");
                    long start = Long.parseLong(ranges[0]);
                    long end = (ranges.length > 1) ? Long.parseLong(ranges[1]) : fileContent.length - 1;

                    if (start > fileContent.length) {
                        res.status(416).body("Requested Range Not Satisfiable");
                        return res.getBody();
                    }

                    byte[] rangeContent = Arrays.copyOfRange(fileContent, (int) start, (int) (end + 1));

                    res.status(206)
                            .header("Content-Range", "bytes " + start + "-" + end + "/" + fileContent.length)
                            .type(contentType)
                            .body(rangeContent);
                    return res.getBody();
                } catch (Exception e) {
                    res.status(400).body("Invalid Range Request");
                    return res.getBody();
                }
            }

            res.status(200).type(contentType).body(fileContent);
            return res.getBody();
        }, HandlerType.STATIC);
    }

}
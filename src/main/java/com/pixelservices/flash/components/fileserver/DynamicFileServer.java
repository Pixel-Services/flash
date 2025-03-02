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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
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
    private final AssetCache assetCache = new AssetCache();
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
    }

    /**
     * Serves dynamic files from a given directory or resource folder.
     * Supports both FILESYSTEM and RESOURCESTREAM source types.
     *
     * @param endpoint the base endpoint for serving dynamic files
     * @param config   the configuration for the dynamic file server
     */
    public void serve(String endpoint, DynamicFileServerConfiguration config) {
        endpoint = endpoint.replaceAll("/+$", "");
        this.sourceType = config.getSourceType();
        String destString = config.getDestinationPath();
        boolean isResourceStream = (sourceType == SourceType.RESOURCESTREAM);

        if (!isResourceStream) {
            this.rootPath = Paths.get(destString);
        }

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

        registerStaticRoutes(endpoint, isResourceStream, destString);

        registerDynamicFallback(endpoint);
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
     * Caches the content of a file (from the file system) and registers a static file route.
     *
     * @param routePath the route path to register
     * @param filePath  the file system path for the content
     */
    private void cacheAndRegisterFile(String routePath, Path filePath) {
        if (!assetCache.contains(routePath)) {
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
                assetCache.put(routePath, fileContent);
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
            byte[] fileContent = assetCache.get(routePath);
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

            // If no range is requested, return the whole file
            res.status(200).type(contentType).body(fileContent);
            return res.getBody();
        }, HandlerType.STATIC);
    }

    /**
     * Caches the content of a resource file (loaded via getResourceAsStream) and registers a static file route.
     *
     * @param routePath    the route path to register
     * @param resourcePath the full resource path (e.g. "static/index.html")
     */
    private void cacheAndRegisterResourceFile(String routePath, String resourcePath) {
        if (!assetCache.contains(routePath)) {
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
                assetCache.put(routePath, fileContent);
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
            byte[] fileContent = assetCache.get(routePath);
            if (fileContent == null) {
                res.status(404).body("File not found");
                return res.getBody();
            }

            String contentType = FileServerUtility.getContentType(resourcePath);

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
}
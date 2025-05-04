package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.components.http.HandlerType;
import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Supplier;

/**
 * Serves dynamic files from either the filesystem or a resource stream.
 * Supports single-page applications with an entrypoint fallback.
 */
public class DynamicFileServer {

    private final FlashServer server;
    private final AssetCache assetCache = new AssetCache();

    /**
     * Constructs the dynamic file server.
     *
     * @param server the server to register routes to
     */
    public DynamicFileServer(FlashServer server) {
        this.server = server;
    }

    /**
     * Serves files dynamically from a filesystem path or a resource folder.
     *
     * @param endpoint      the base URL endpoint
     * @param config        the configuration object
     * @param contextClass  optional class used for loading resources (for RESOURCESTREAM)
     */
    public void serve(String endpoint, DynamicFileServerConfiguration config, Class<?> contextClass) {
        endpoint = endpoint.replaceAll("/+$", "");
        SourceType sourceType = config.getSourceType();
        String destString = config.getDestinationPath();
        boolean isResourceStream = sourceType == SourceType.RESOURCESTREAM;
        Path rootPath = isResourceStream ? null : Paths.get(destString);

        byte[] indexHtmlContent = loadEntrypoint(config.getDynamicEntrypoint(), destString, rootPath, contextClass, isResourceStream);

        registerStaticRoutes(endpoint, destString, rootPath, contextClass, isResourceStream);
        registerFallback(endpoint, indexHtmlContent);
    }

    /**
     * Loads the main entrypoint file (e.g., index.html).
     */
    private byte[] loadEntrypoint(String entrypoint, String basePath, Path rootPath, Class<?> contextClass, boolean isResourceStream) {
        try {
            if (isResourceStream) {
                String fullPath = basePath + "/" + entrypoint;
                InputStream is = Objects.requireNonNull(
                        contextClass.getClassLoader().getResourceAsStream(fullPath),
                        "Index resource not found: " + fullPath);
                return is.readAllBytes();
            } else {
                return Files.readAllBytes(rootPath.resolve(entrypoint));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load index file: " + entrypoint, e);
        }
    }

    /**
     * Registers static file routes by scanning directory or resources.
     */
    private void registerStaticRoutes(String endpoint, String basePath, Path rootPath, Class<?> contextClass, boolean isResource) {
        if (isResource) {
            for (String resourcePath : listResourceFiles(basePath, contextClass)) {
                String relativePath = resourcePath.substring(basePath.length()).replaceFirst("^/", "");
                String routePath = endpoint + "/" + relativePath;
                cacheAndRegisterFile(routePath, () -> contextClass.getClassLoader().getResourceAsStream(resourcePath), resourcePath, true);
            }
        } else {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile).forEach(filePath -> {
                    String relativePath = rootPath.relativize(filePath).toString().replace("\\", "/");
                    String routePath = endpoint + "/" + relativePath;
                    cacheAndRegisterFile(routePath, () -> {
                        try {
                            return Files.newInputStream(filePath);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, filePath.toString(), false);
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to list directory: " + rootPath, e);
            }
        }
    }

    /**
     * Registers a fallback route that serves the index HTML.
     */
    private void registerFallback(String endpoint, byte[] indexContent) {
        String fallback = endpoint.endsWith("/") ? endpoint + "*" : endpoint + "/*";
        server.registerRoute(HttpMethod.GET, fallback, (req, res) -> {
            res.type("text/html").body(indexContent);
            return res.getBody();
        });
    }

    /**
     * Caches and registers a static file route.
     */
    private void cacheAndRegisterFile(String routePath, Supplier<InputStream> streamSupplier, String sourcePath, boolean isResource) {
        if (!assetCache.contains(routePath)) {
            try (InputStream is = streamSupplier.get()) {
                if (is == null) throw new IllegalArgumentException("Not found: " + sourcePath);

                byte[] content;
                String lower = sourcePath.toLowerCase();
                if (lower.endsWith(".html") || lower.endsWith(".js")) {
                    String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    text = text.replaceAll("(href|src)=([\"'])/", "$1=$2" + routePath + "/");
                    content = text.getBytes(StandardCharsets.UTF_8);
                } else {
                    content = is.readAllBytes();
                }

                assetCache.put(routePath, content);
                registerStaticFileRoute(routePath, sourcePath, isResource);

            } catch (IOException e) {
                PrettyLogger.withEmoji("Error caching file: " + e.getMessage(), "⚠️");
            }
        }
    }

    /**
     * Registers a static file route.
     */
    private void registerStaticFileRoute(String routePath, String sourcePath, boolean isResource) {
        server.registerRoute(HttpMethod.GET, routePath, (req, res) -> {
            byte[] content = assetCache.get(routePath);
            if (content == null) {
                return res.status(404).body("File not found").getBody();
            }

            String rangeHeader = req.header("Range");
            String contentType;

            try {
                contentType = isResource
                        ? FileServerUtility.getContentType(sourcePath)
                        : Optional.ofNullable(Files.probeContentType(Paths.get(sourcePath))).orElse("application/octet-stream");
            } catch (IOException e) {
                PrettyLogger.withEmoji("Error determining content type: " + e.getMessage(), "⚠️");
                contentType = "application/octet-stream";
            }

            if (rangeHeader != null) {
                try {
                    String[] parts = rangeHeader.replace("bytes=", "").split("-");
                    int start = Integer.parseInt(parts[0]);
                    int end = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1]) : content.length - 1;

                    if (start > end || start >= content.length) {
                        return res.status(416).body("Requested Range Not Satisfiable").getBody();
                    }

                    byte[] rangedContent = Arrays.copyOfRange(content, start, Math.min(end + 1, content.length));

                    return res.status(206)
                            .header("Content-Range", "bytes " + start + "-" + end + "/" + content.length)
                            .type(contentType)
                            .body(rangedContent)
                            .getBody();
                } catch (Exception e) {
                    return res.status(400).body("Invalid Range Request").getBody();
                }
            }

            return res.status(200).type(contentType).body(content).getBody();
        }, HandlerType.STATIC);
    }

    /**
     * Parses a Range header and responds with partial content.
     */
    private byte[] handleRange(String rangeHeader, byte[] fileContent, Response res) {
        try {
            String[] parts = rangeHeader.replace("bytes=", "").split("-");
            long start = Long.parseLong(parts[0]);
            long end = (parts.length > 1) ? Long.parseLong(parts[1]) : fileContent.length - 1;

            if (start >= fileContent.length) {
                res.status(416).body("Requested Range Not Satisfiable");
                return null;
            }

            byte[] slice = Arrays.copyOfRange(fileContent, (int) start, (int) (end + 1));
            res.status(206)
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + fileContent.length)
                    .body(slice);
            return slice;
        } catch (Exception e) {
            res.status(400).body("Invalid Range Request");
            return null;
        }
    }

    /**
     * Lists all resource files from the given path.
     */
    private List<String> listResourceFiles(String base, Class<?> contextClass) {
        List<String> result = new ArrayList<>();
        try {
            URL url = contextClass.getClassLoader().getResource(base);
            if (url == null) throw new IllegalArgumentException("Resource path not found: " + base);

            if (url.getProtocol().equals("jar")) {
                String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (!entry.isDirectory() && entry.getName().startsWith(base)) {
                            result.add(entry.getName());
                        }
                    }
                }
            } else if (url.getProtocol().equals("file")) {
                Path dir = Paths.get(url.toURI());
                try (Stream<Path> walk = Files.walk(dir)) {
                    result = walk.filter(Files::isRegularFile)
                            .map(p -> base + "/" + dir.relativize(p).toString().replace("\\", "/"))
                            .collect(Collectors.toList());
                }
            } else {
                throw new UnsupportedOperationException("Unsupported protocol: " + url.getProtocol());
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Error listing resource files: " + base, e);
        }
        return result;
    }
}

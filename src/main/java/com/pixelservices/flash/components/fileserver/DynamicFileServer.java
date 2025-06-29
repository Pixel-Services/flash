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

import static com.pixelservices.flash.utils.Constant.MIME_TYPES;

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
            listResourceFiles(basePath, contextClass).forEach(resourcePath -> {
                String relative = resourcePath.substring(basePath.length()).replaceFirst("^/", "");
                registerFile(endpoint + "/" + relative, () -> contextClass.getClassLoader().getResourceAsStream(resourcePath), resourcePath, true);
            });
        } else {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    String relative = rootPath.relativize(path).toString().replace("\\", "/");
                    registerFile(endpoint + "/" + relative, () -> {
                        try {
                            return Files.newInputStream(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, path.toString(), false);
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to list directory: " + rootPath, e);
            }
        }
    }

    private void registerFile(String routePath, Supplier<InputStream> streamSupplier, String sourcePath, boolean isResource) {
        cacheAndRegisterFile(routePath, streamSupplier, sourcePath, isResource);
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
        if (assetCache.contains(routePath)) return;

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
            String type = getContentType(sourcePath, isResource);

            //PrettyLogger.withEmoji("📦 Cached: " + routePath + " (" + content.length + " bytes, type=" + type + ")", "📁");

            registerStaticFileRoute(routePath, sourcePath, isResource);

        } catch (IOException e) {
            PrettyLogger.withEmoji("⚠️ Error caching file: " + e.getMessage(), "⚠️");
        }
    }


    /**
     * Registers a static file route.
     */
    private void registerStaticFileRoute(String routePath, String sourcePath, boolean isResource) {
        server.registerRoute(HttpMethod.GET, routePath, (req, res) -> {
            byte[] content = assetCache.get(routePath);
            if (content == null) return res.status(404).body("File not found").getBody();

            String rangeHeader = req.header("Range");
            String contentType = getContentType(sourcePath, isResource);

            Optional<Range> rangeOpt = parseRange(rangeHeader, content.length);
            if (rangeOpt.isPresent()) {
                Range r = rangeOpt.get();
                byte[] rangedContent = Arrays.copyOfRange(content, r.start, r.end + 1);
                return res.status(206)
                        .header("Content-Range", "bytes " + r.start + "-" + r.end + "/" + content.length)
                        .type(contentType)
                        .body(rangedContent)
                        .getBody();
            }

            return res.status(200).type(contentType).body(content).getBody();
        }, HandlerType.STATIC);
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

    private String getContentType(String sourcePath, boolean isResource) {
        String lower = sourcePath.toLowerCase();

        String baseName = lower;
        if (lower.endsWith(".gz") || lower.endsWith(".br")) {
            int idx = lower.lastIndexOf('.');
            baseName = lower.substring(0, idx);
        }

        for (Map.Entry<String, String> entry : MIME_TYPES.entrySet()) {
            if (baseName.endsWith(entry.getKey())) {
                String mime = entry.getValue();

                if (lower.endsWith(".gz")) {
                    mime += "; charset=utf-8";
                }
                return mime;
            }
        }

        //fallback to probe or default
        try {
            return isResource
                    ? FileServerUtility.getContentType(sourcePath)
                    : Optional.ofNullable(Files.probeContentType(Paths.get(sourcePath))).orElse("application/octet-stream");
        } catch (IOException e) {
            PrettyLogger.withEmoji("⚠️ Content-Type fallback: " + e.getMessage(), "⚠️");
            return "application/octet-stream";
        }
    }

    private Optional<Range> parseRange(String rangeHeader, int contentLength) {
        try {
            if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) return Optional.empty();

            String[] parts = rangeHeader.replace("bytes=", "").split("-");
            int start = Integer.parseInt(parts[0].trim());
            int end = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1].trim()) : contentLength - 1;

            if (start > end || start >= contentLength) return Optional.empty();
            return Optional.of(new Range(start, end));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static class Range {
        int start, end;
        Range(int start, int end) { this.start = start; this.end = end; }
    }


}

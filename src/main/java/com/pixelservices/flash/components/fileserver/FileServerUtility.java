package com.pixelservices.flash.components.fileserver;


import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileServerUtility {

    /**
     * Registers the given directory and all its subdirectories with the provided WatchService.
     */
    public static void registerAllSubdirectories(Path startPath, WatchService watchService) throws IOException {
        Files.walk(startPath)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                    } catch (IOException e) {
                        PrettyLogger.withEmoji("Error registering directory: " + dir, "⚠️");
                    }
                });
    }

    /**
     * Starts a directory watcher on the given rootPath. For every watch event, the callback is invoked.
     * Note: This method blocks the current thread.
     */
    public static void startDirectoryWatcher(Path rootPath, FileWatcherCallback callback) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            registerAllSubdirectories(rootPath, watchService);
            PrettyLogger.withEmoji("Watching directory for changes: &#80EF80" + rootPath, "👀");

            while (true) {
                WatchKey key = watchService.take();
                if (key == null) continue;
                for (WatchEvent<?> event : key.pollEvents()) {
                    callback.onEvent(event, rootPath);
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            PrettyLogger.withEmoji("Error watching directory: " + e.getMessage(), "⚠️");
        }
    }

    /**
     * Attempts to auto-detect if the given directory is a modern SPA (e.g. React, Next.js, Svelte).
     * This method checks for the presence of an index.html along with either an asset-manifest.json or next.config.js.
     */
    public static boolean autodetectSPA(Path dirPath) {
        boolean hasIndexHtml = Files.exists(dirPath.resolve("index.html"));
        boolean hasAssetManifest = Files.exists(dirPath.resolve("asset-manifest.json"));
        boolean hasNextConfig = Files.exists(dirPath.resolve("next.config.js"));
        return (hasIndexHtml && hasAssetManifest) || hasNextConfig;
    }

    /**
     * Rewrites HTML content by prefixing static asset URLs with the given endpoint.
     */
    public static String rewriteHtml(String htmlContent, String endpoint) {
        return htmlContent.replaceAll("href=\"(?!\\Q" + endpoint + "\\E/)(/[^\"']*)\"",
                        "href=\"" + endpoint + "$1\"")
                .replaceAll("src=\"(?!\\Q" + endpoint + "\\E/)(/[^\"']*)\"",
                        "src=\"" + endpoint + "$1\"");
    }

    /**
     * Rewrites JavaScript content by prefixing asset URLs with the given endpoint.
     */
    public static String rewriteJs(String jsContent, String endpoint) {
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
     * Rewrites a route by prefixing it with the given endpoint if it isn’t already.
     */
    public static String rewriteRoute(String route, String endpoint) {
        if (route.startsWith(endpoint)) {
            return route;
        }
        return endpoint + route;
    }

    /**
     * Callback interface for file watcher events.
     */
    public interface FileWatcherCallback {
        void onEvent(WatchEvent<?> event, Path rootDir);
    }
}


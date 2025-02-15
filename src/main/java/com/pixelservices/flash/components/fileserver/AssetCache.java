package com.pixelservices.flash.components.fileserver;

import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AssetCache {

    private final Map<String, byte[]> fileCache;
    private final Map<String, String> fileHashes;

    public AssetCache() {
        fileCache = new ConcurrentHashMap<>();
        fileHashes = new ConcurrentHashMap<>();
    }

    /**
     * Caches the file content for the given file.
     * If the file is HTML or JavaScript, its content is rewritten using FileServerUtility.
     */
    public void cacheFile(Path filePath, String routePath, String endpoint) {
        try {
            byte[] fileContent;
            String fileName = filePath.toString().toLowerCase();
            if (fileName.endsWith(".html")) {
                String originalHtml = Files.readString(filePath);
                String rewrittenHtml = FileServerUtility.rewriteHtml(originalHtml, endpoint);
                fileContent = rewrittenHtml.getBytes(StandardCharsets.UTF_8);
            } else if (fileName.endsWith(".js")) {
                String originalJs = Files.readString(filePath);
                String rewrittenJs = FileServerUtility.rewriteJs(originalJs, endpoint);
                fileContent = rewrittenJs.getBytes(StandardCharsets.UTF_8);
            } else {
                fileContent = Files.readAllBytes(filePath);
            }
            fileCache.put(routePath, fileContent);
            fileHashes.put(routePath, computeFileHash(filePath));
        } catch (IOException e) {
            PrettyLogger.withEmoji("Error caching file: " + e.getMessage(), "⚠️");
        }
    }

    /**
     * Retrieves the cached file content for the given route.
     */
    public byte[] getFileContent(String routePath) {
        return fileCache.get(routePath);
    }

    /**
     * Checks whether the cache contains an entry for the given route.
     */
    public boolean contains(String routePath) {
        return fileCache.containsKey(routePath);
    }

    /**
     * Removes the cached entry for the given route.
     */
    public void remove(String routePath) {
        fileCache.remove(routePath);
        fileHashes.remove(routePath);
    }

    /**
     * Retrieves the stored hash for the given route.
     */
    public String getFileHash(String routePath) {
        return fileHashes.get(routePath);
    }

    /**
     * Computes the SHA-256 hash of the given file.
     */
    public String computeFileHash(Path filePath) {
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
            throw new RuntimeException("Error computing file hash", e);
        }
    }
}


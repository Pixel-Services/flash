package com.pixelservices.flash.components.fileserver;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AssetCache provides a thread-safe cache for storing file bytes.
 * It is independent of any file system or resource stream details.
 */
public class AssetCache {

    private final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();

    /**
     * Stores the asset bytes associated with the given key.
     *
     * @param key        the key (e.g., a route path)
     * @param assetBytes the bytes to cache
     * @throws IllegalArgumentException if key or assetBytes is null
     */
    public void put(String key, byte[] assetBytes) {
        if (key == null || assetBytes == null) {
            throw new IllegalArgumentException("Key and assetBytes cannot be null");
        }
        cache.put(key, assetBytes);
    }

    /**
     * Retrieves the cached asset bytes for the given key.
     *
     * @param key the key to look up
     * @return the asset bytes, or null if not found
     */
    public byte[] get(String key) {
        return cache.get(key);
    }

    /**
     * Checks if the cache contains an asset for the given key.
     *
     * @param key the key to check
     * @return true if the cache contains the key; false otherwise
     */
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    /**
     * Removes the asset bytes associated with the given key.
     *
     * @param key the key whose entry should be removed
     * @return the removed asset bytes, or null if there was no mapping
     */
    public byte[] remove(String key) {
        return cache.remove(key);
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        cache.clear();
    }
}

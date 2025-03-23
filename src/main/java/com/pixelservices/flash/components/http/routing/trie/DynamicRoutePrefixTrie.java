package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.routing.models.RouteEntry;
import com.pixelservices.flash.components.http.routing.models.RouteMatch;
import com.pixelservices.flash.components.http.HttpMethod;

import java.util.*;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

public class DynamicRoutePrefixTrie {
    private static final int INITIAL_CAPACITY = 16;

    private static final class CompactTrieNode {
        String[] segments = new String[INITIAL_CAPACITY];
        CompactTrieNode[] children = new CompactTrieNode[INITIAL_CAPACITY];
        RouteEntry[] routeEntries = new RouteEntry[INITIAL_CAPACITY];
        int segmentCount = 0;
        int routeCount = 0;

        CompactTrieNode addSegment(String segment) {
            int index = Arrays.binarySearch(segments, 0, segmentCount, segment);
            if (index >= 0) {
                return children[index];
            }

            if (segmentCount == segments.length) {
                int newCapacity = segments.length * 2;
                segments = Arrays.copyOf(segments, newCapacity);
                children = Arrays.copyOf(children, newCapacity);
            }

            index = -(index + 1);
            System.arraycopy(segments, index, segments, index + 1, segmentCount - index);
            System.arraycopy(children, index, children, index + 1, segmentCount - index);

            CompactTrieNode newNode = new CompactTrieNode();
            segments[index] = segment;
            children[index] = newNode;
            segmentCount++;
            return newNode;
        }

        void addRouteEntry(RouteEntry entry) {
            if (routeCount == routeEntries.length) {
                routeEntries = Arrays.copyOf(routeEntries, routeEntries.length * 2);
            }
            routeEntries[routeCount++] = entry;
        }
    }

    private final CompactTrieNode root = new CompactTrieNode();
    private final StampedLock lock = new StampedLock();

    public void insert(RouteEntry routeEntry) {
        long stamp = lock.writeLock();
        try {
            CompactTrieNode currentNode = root;
            String path = routeEntry.getPath();
            String prefix = path.endsWith("/*") ? path.substring(0, path.length() - 2) : path;
            String[] segments = prefix.split("/");

            for (String segment : segments) {
                if (!segment.isEmpty()) {
                    currentNode = currentNode.addSegment(segment);
                }
            }

            currentNode.addRouteEntry(routeEntry);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public RouteMatch search(HttpMethod method, String path) {
        long stamp = lock.tryOptimisticRead();
        RouteMatch match = searchInternal(method, path);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                return searchInternal(method, path);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return match;
    }

    private RouteMatch searchInternal(HttpMethod method, String path) {
        CompactTrieNode currentNode = root;
        RouteMatch potentialMatch = null; // Store potential wildcard match

        // Check if root itself has a wildcard route (e.g., "/*")
        for (int i = 0; i < root.routeCount; i++) {
            RouteEntry routeEntry = root.routeEntries[i];
            if (routeEntry.getMethod() == method && routeEntry.getPath().equals("/*")) {
                potentialMatch = new RouteMatch(routeEntry, Collections.emptyMap()); // Found potential wildcard match
                break; // No need to check other root routes if wildcard is found
            }
        }

        String[] segments = path.split("/");

        for (String segment : segments) {
            if (!segment.isEmpty()) {
                int index = Arrays.binarySearch(currentNode.segments, 0, currentNode.segmentCount, segment);
                if (index >= 0) {
                    currentNode = currentNode.children[index];
                } else {
                    return potentialMatch; // No more literal segment match, return potential wildcard if any
                }
            }
        }

        // After segment matching, check for exact match in the last node
        if (currentNode != null) {
            for (int i = 0; i < currentNode.routeCount; i++) {
                RouteEntry routeEntry = currentNode.routeEntries[i];
                if (routeEntry.getMethod() == method) {
                    String routePath = routeEntry.getPath();
                    String prefix = routePath.endsWith("/*") ? routePath.substring(0, routePath.length() - 2) : routePath;

                    if (routePath.equals("/*") || path.startsWith(prefix)) { // Condition for both "/*" and prefix based dynamic routes
                        Map<String, String> params = new HashMap<>();
                        if (!routePath.equals("/*")) {
                            params.put("path", path.substring(prefix.length()));
                        }
                        return new RouteMatch(routeEntry, params);
                    }
                }
            }
        }

        return potentialMatch; // Return potential wildcard match if no exact match found
    }

    public int size() {
        long stamp = lock.tryOptimisticRead();
        int totalRoutes = countRoutes(root);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                return countRoutes(root);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return totalRoutes;
    }

    private int countRoutes(CompactTrieNode node) {
        int count = node.routeCount;
        for (int i = 0; i < node.segmentCount; i++) {
            count += countRoutes(node.children[i]);
        }
        return count;
    }
}

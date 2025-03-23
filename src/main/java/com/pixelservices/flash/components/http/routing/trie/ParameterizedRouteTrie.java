package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.routing.models.RouteEntry;
import com.pixelservices.flash.components.http.routing.models.RouteMatch;
import com.pixelservices.flash.components.http.HttpMethod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

public class ParameterizedRouteTrie {
    private static final class CompactTrieNode {
        private static final int INITIAL_CAPACITY = 16;

        String[] literalSegments = new String[INITIAL_CAPACITY];
        CompactTrieNode[] literalChildren = new CompactTrieNode[INITIAL_CAPACITY];
        int literalCount = 0;

        CompactTrieNode parameterChild = null;
        RouteEntry parameterRouteEntry = null;
        String parameterName = null;
        RouteEntry routeEntry = null;

        CompactTrieNode findOrCreateLiteralChild(String segment) {
            int index = Arrays.binarySearch(literalSegments, 0, literalCount, segment);
            if (index >= 0) {
                return literalChildren[index];
            }

            if (literalCount == literalSegments.length) {
                int newCapacity = literalSegments.length * 2;
                literalSegments = Arrays.copyOf(literalSegments, newCapacity);
                literalChildren = Arrays.copyOf(literalChildren, newCapacity);
            }

            index = -(index + 1);
            System.arraycopy(literalSegments, index, literalSegments, index + 1, literalCount - index);
            System.arraycopy(literalChildren, index, literalChildren, index + 1, literalCount - index);

            CompactTrieNode newChild = new CompactTrieNode();
            literalSegments[index] = segment;
            literalChildren[index] = newChild;
            literalCount++;
            return newChild;
        }
    }

    private final CompactTrieNode root = new CompactTrieNode();
    private final StampedLock lock = new StampedLock();

    public void insert(RouteEntry routeEntry) {
        long stamp = lock.writeLock();
        try {
            CompactTrieNode currentNode = root;
            String[] segments = routeEntry.getPathSegments();

            for (String segment : segments) {
                if (segment.startsWith(":")) {
                    if (currentNode.parameterChild == null) {
                        currentNode.parameterChild = new CompactTrieNode();
                        currentNode.parameterChild.parameterName = segment.substring(1);
                    }
                    currentNode = currentNode.parameterChild;
                    currentNode.parameterRouteEntry = routeEntry;
                } else {
                    currentNode = currentNode.findOrCreateLiteralChild(segment);
                }
            }
            currentNode.routeEntry = routeEntry;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public RouteMatch search(HttpMethod method, String path) {
        long stamp = lock.tryOptimisticRead();

        CompactTrieNode currentNode = root;
        Map<String, String> params = null;
        String[] segments = path.split("/");

        for (String segment : segments) {
            if (segment.isEmpty()) continue;

            int index = Arrays.binarySearch(currentNode.literalSegments, 0, currentNode.literalCount, segment);
            if (index >= 0) {
                currentNode = currentNode.literalChildren[index];
            } else if (currentNode.parameterChild != null) {
                if (params == null) params = new HashMap<>();
                params.put(currentNode.parameterChild.parameterName, segment);
                currentNode = currentNode.parameterChild;
            } else {
                return null;
            }
        }

        RouteEntry matchedEntry = currentNode.routeEntry != null
                ? currentNode.routeEntry
                : currentNode.parameterRouteEntry;

        return (matchedEntry != null && matchedEntry.getMethod() == method)
                ? new RouteMatch(matchedEntry, params != null ? params : Map.of())
                : null;
    }

    public int size() {
        long stamp = lock.tryOptimisticRead();
        int count = countRoutes(root);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                return countRoutes(root);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return count;
    }

    private int countRoutes(CompactTrieNode node) {
        int cnt = (node.routeEntry != null || node.parameterRouteEntry != null) ? 1 : 0;

        for (int i = 0; i < node.literalCount; i++) {
            cnt += countRoutes(node.literalChildren[i]);
        }

        if (node.parameterChild != null) {
            cnt += countRoutes(node.parameterChild);
        }

        return cnt;
    }
}

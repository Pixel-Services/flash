package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.routing.models.RouteEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.logging.Logger;

public abstract class AbstractRadixRouteTrie<T> {
    private static final Logger logger = Logger.getLogger(AbstractRadixRouteTrie.class.getName());

    protected static class Node<T> {
        String segment;
        String paramName;
        T value;
        Map<String, Node<T>> children = new ConcurrentHashMap<>();

        boolean isParameter() {
            return paramName != null;
        }

        Node<T> shallowCopy() {
            Node<T> copy = new Node<>();
            copy.segment = this.segment;
            copy.paramName = this.paramName;
            copy.value = this.value;
            copy.children = new ConcurrentHashMap<>(this.children);
            return copy;
        }
    }

    protected volatile Node<T> root = new Node<>();

    public void insert(String path, T value) {
        logger.warning("Using deprecated insert(String path). Prefer insert(RouteEntry, T) for performance.");
        String[] segments = path.split("/");
        segments = java.util.Arrays.stream(segments)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        Node<T> oldRoot = this.root;
        this.root = insertCopyOnWrite(oldRoot, segments, value);
    }

    public void insert(RouteEntry entry, T value) {
        long startTime = System.nanoTime();
        Node<T> oldRoot = this.root;
        this.root = insertCopyOnWrite(oldRoot, entry.getPathSegments(), value);
        long endTime = System.nanoTime();
        //logger.info("Insert (RouteEntry) took " + (endTime - startTime) + " ns");
    }

    private Node<T> insertCopyOnWrite(Node<T> originalRoot, String[] segments, T value) {
        Node<T> newRoot = originalRoot.shallowCopy();
        Node<T> currentSrc = originalRoot;
        Node<T> currentDst = newRoot;

        for (String seg : segments) {
            boolean isParam = isParameterSegment(seg);
            String key = isParam ? "*" : seg;

            Node<T> srcChild = currentSrc.children.get(key);
            Node<T> dstChild;

            if (srcChild != null) {
                dstChild = srcChild.shallowCopy();
            } else {
                dstChild = new Node<>();
                dstChild.segment = seg;
                if (isParam) dstChild.paramName = extractParameterName(seg);
            }

            currentDst.children.put(key, dstChild);
            currentSrc = srcChild != null ? srcChild : new Node<>();
            currentDst = dstChild;
        }

        currentDst.value = value;
        return newRoot;
    }

    /**
     * Search for a route match using a raw path string.
     * @param path The path to search for
     * @return The matched value, or null if no match is found
     * @deprecated Use {@link #searchWithParams(String[])} for better performance
     */
    @Deprecated
    public T search(String path) {
        MatchResult<T> result = searchWithParams(path);
        return result != null ? result.value : null;
    }

    // LEGACY fallback
    public MatchResult<T> searchWithParams(String path) {
        String[] segments = path.split("/");
        segments = java.util.Arrays.stream(segments)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return searchWithParams(segments);
    }

    // lookup with precomputed segments
    public MatchResult<T> searchWithParams(String[] segments) {
        long startTime = System.nanoTime();
        Map<String, String> params = new HashMap<>();
        Node<T> current = root;

        for (String seg : segments) {
            Node<T> next = current.children.get(seg);
            if (next != null) {
                current = next;
                continue;
            }

            next = current.children.get("*");
            if (next != null && next.paramName != null) {
                params.put(next.paramName, seg);
                current = next;
            } else {
                logger.info("Search with params failed. Time: " + (System.nanoTime() - startTime) + " ns");
                return null;
            }
        }

        logger.info("Search with params success. Time: " + (System.nanoTime() - startTime) + " ns");
        return current.value != null
                ? new MatchResult<>(current.value, params)
                : null;
    }

    public static class MatchResult<T> {
        public final T value;
        public final Map<String, String> params;

        public MatchResult(T value, Map<String, String> params) {
            this.value = value;
            this.params = params;
        }
    }

    public int size() {
        return countNodes(root);
    }

    private int countNodes(Node<T> node) {
        int count = 1;
        for (Node<T> child : node.children.values()) {
            count += countNodes(child);
        }
        return count;
    }

    protected abstract boolean isParameterSegment(String segment);
    protected abstract String extractParameterName(String segment);
    protected abstract T matchResult(T candidate, String fullPath);
}

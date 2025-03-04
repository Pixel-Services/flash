package com.pixelservices.flash.components.routing.trie;

import com.pixelservices.flash.components.routing.models.RouteEntry;
import com.pixelservices.flash.components.routing.models.RouteMatch;
import com.pixelservices.flash.models.HttpMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trie for dynamic route prefix lookup.
 */
public class DynamicRoutePrefixTrie {
    private static class TrieNode {
        final Map<String, TrieNode> children = new HashMap<>();
        final List<RouteEntry> routeEntries = new ArrayList<>(); // Store multiple dynamic routes with the same prefix

        public synchronized void addRouteEntry(RouteEntry routeEntry) {
            this.routeEntries.add(routeEntry);
        }
        public List<RouteEntry> getRouteEntries() {
            return this.routeEntries;
        }
    }
    private final TrieNode root = new TrieNode();

    public synchronized void insert(RouteEntry routeEntry) {
        TrieNode currentNode = root;
        String path = routeEntry.getPath();
        String prefix = path.substring(0, path.length() - 2); // remove "/*"
        String[] segments = prefix.split("/");

        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            currentNode = currentNode.children.computeIfAbsent(segment, s -> new TrieNode());
        }
        currentNode.addRouteEntry(routeEntry);
    }


    public RouteMatch search(HttpMethod method, String path) {
        TrieNode currentNode = root;
        String[] segments = path.split("/");
        TrieNode prefixMatchNode = null;

        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            currentNode = currentNode.children.get(segment);
            if (currentNode == null) {
                break;
            }
            prefixMatchNode = currentNode;
        }

        if (prefixMatchNode != null) {
            for (RouteEntry routeEntry : prefixMatchNode.getRouteEntries()) {
                if (routeEntry.getMethod() == method) {
                    String prefix = routeEntry.getPath().substring(0, routeEntry.getPath().length() - 2);
                    if (path.startsWith(prefix)) {
                        Map<String, String> params = new HashMap<>();
                        params.put("path", path.substring(prefix.length()));
                        return new RouteMatch(routeEntry, params);
                    }
                }
            }
        }
        return null;
    }

    public int size() {
        return count(root);
    }
    private int count(TrieNode node) {
        int cnt = node.routeEntries.size();
        for (TrieNode child : node.children.values()) {
            cnt += count(child);
        }
        return cnt;
    }
}
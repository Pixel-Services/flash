package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.routing.models.RouteEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple thread-safe trie for storing literal route entries.
 * The key is the concatenation of HTTP method and literal path.
 */
public class RouteTrie {
    private static class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>();
        RouteEntry routeEntry = null;
    }
    private final TrieNode root = new TrieNode();

    public synchronized void insert(String key, RouteEntry routeEntry) {
        TrieNode node = root;
        for (char c : key.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.routeEntry = routeEntry;
    }

    public RouteEntry search(String key) {
        TrieNode node = root;
        for (char c : key.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return null;
        }
        return node.routeEntry;
    }

    public int size() {
        return count(root);
    }
    private int count(TrieNode node) {
        int cnt = (node.routeEntry != null) ? 1 : 0;
        for (TrieNode child : node.children.values()) {
            cnt += count(child);
        }
        return cnt;
    }
}

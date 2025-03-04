package com.pixelservices.flash.components.routing.trie;

import com.pixelservices.flash.components.routing.models.RouteEntry;
import com.pixelservices.flash.components.routing.models.RouteMatch;
import com.pixelservices.flash.models.HttpMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * Trie for storing parameterized routes.
 */
public class ParameterizedRouteTrie {
    private static class TrieNode {
        final Map<String, TrieNode> children = new HashMap<>(); // Literal path segments
        TrieNode parameterChild = null; // Child for parameter segment (e.g., ":param")
        RouteEntry routeEntry = null;

        // for debugging and visualization
        private String segmentType = "literal"; // "literal", "parameter", "root"

        TrieNode(String segmentType) {
            this.segmentType = segmentType;
        }
        TrieNode() {} // default constructor for root
    }

    private final TrieNode root = new TrieNode("root");

    public synchronized void insert(RouteEntry routeEntry) {
        TrieNode currentNode = root;
        String[] segments = routeEntry.getPathSegments(); // Parse path into segments

        for (String segment : segments) {
            if (segment.startsWith(":")) {
                // Parameter segment
                if (currentNode.parameterChild == null) {
                    currentNode.parameterChild = new TrieNode("parameter");
                }
                currentNode = currentNode.parameterChild;
            } else {
                // Literal segment
                currentNode = currentNode.children.computeIfAbsent(segment, s -> new TrieNode("literal"));
            }
        }
        currentNode.routeEntry = routeEntry;
    }

    public RouteMatch search(HttpMethod method, String path) {
        TrieNode currentNode = root;
        Map<String, String> params = new HashMap<>();
        String[] segments = path.split("/");

        for (String segment : segments) {
            if (segment.isEmpty()) continue;

            TrieNode literalChildNode = currentNode.children.get(segment);
            if (literalChildNode != null) {
                currentNode = literalChildNode;
            } else if (currentNode.parameterChild != null) {
                String paramName =
                        currentNode.parameterChild.segmentType.equals("parameter") && currentNode.parameterChild.routeEntry != null && currentNode.parameterChild.routeEntry.getPathSegments().length > 0 ?
                                currentNode.parameterChild.routeEntry.getPathSegments()[currentNode.parameterChild.routeEntry.getPathSegments().length-1].substring(1) : "param";
                params.put(paramName, segment);
                currentNode = currentNode.parameterChild;
            } else {
                return null;
            }
        }

        if (currentNode.routeEntry != null && currentNode.routeEntry.getMethod() == method) {
            return new RouteMatch(currentNode.routeEntry, params);
        }
        return null;
    }

    public int size() {
        return count(root);
    }

    private int count(TrieNode node) {
        int cnt = (node.routeEntry != null) ? 1 : 0;
        for (TrieNode child : node.children.values()) {
            cnt += count(child);
        }
        if (node.parameterChild != null) {
            cnt += count(node.parameterChild);
        }
        return cnt;
    }
}

package com.pixelservices.flash.components.routing;

import com.pixelservices.flash.components.routing.models.RouteEntry;
import com.pixelservices.flash.components.routing.models.RouteMatch;
import com.pixelservices.flash.components.routing.trie.DynamicRoutePrefixTrie;
import com.pixelservices.flash.components.routing.trie.ParameterizedRouteTrie;
import com.pixelservices.flash.components.routing.trie.RouteTrie;
import com.pixelservices.flash.models.HttpMethod;

import java.util.Collections;

/**
 * Manages route registration and lookup using different Trie structures.
 */
public class RouteRegistry {
    private final RouteTrie literalTrie = new RouteTrie();
    private final ParameterizedRouteTrie parameterizedRouteTrie = new ParameterizedRouteTrie();
    private final DynamicRoutePrefixTrie dynamicRoutePrefixTrie = new DynamicRoutePrefixTrie();

    public void registerLiteralRoute(RouteEntry entry) {
        literalTrie.insert(entry.getLiteralKey(), entry);
    }

    public void registerParameterizedRoute(RouteEntry entry) {
        parameterizedRouteTrie.insert(entry);
    }

    public void registerDynamicRoute(RouteEntry entry) {
        dynamicRoutePrefixTrie.insert(entry);
    }

    public RouteMatch resolveRoute(HttpMethod method, String path) {
        String literalKey = method.name() + ":" + path;
        RouteMatch match = literalTrie.search(literalKey) != null ? new RouteMatch(literalTrie.search(literalKey), Collections.emptyMap()) : null;
        if (match == null) {
            match = parameterizedRouteTrie.search(method, path);
        }
        if (match == null) {
            match = dynamicRoutePrefixTrie.search(method, path);
        }
        return match;
    }

    public int getLiteralRouteCount() {
        return literalTrie.size();
    }

    public int getParameterizedRouteCount() {
        return parameterizedRouteTrie.size();
    }

    public int getDynamicRouteCount() {
        return dynamicRoutePrefixTrie.size();
    }
}

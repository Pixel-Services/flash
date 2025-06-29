package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.routing.models.RouteEntry;
import com.pixelservices.flash.components.http.routing.models.RouteMatch;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.MatchResult;

public class ParameterizedRouteTrie extends AbstractRadixRouteTrie<RouteEntry> {

    public void insert(RouteEntry routeEntry) {
        super.insert(routeEntry.getPath(), routeEntry);
    }

    public RouteMatch search(HttpMethod method, String path) {
        MatchResult<RouteEntry> result = super.searchWithParams(path);
        if (result == null || result.value == null) return null;
        if (result.value.getMethod() != method) return null;
        return new RouteMatch(result.value, result.params != null ? result.params : Map.of());
    }

    @Override
    protected RouteEntry matchResult(RouteEntry candidate, String fullPath) {
        return candidate;
    }

    @Override
    protected boolean isParameterSegment(String segment) {
        return segment.startsWith(":");
    }

    @Override
    protected String extractParameterName(String segment) {
        return segment.substring(1);
    }
}

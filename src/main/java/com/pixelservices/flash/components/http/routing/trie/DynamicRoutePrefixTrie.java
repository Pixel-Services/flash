package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.routing.models.RouteEntry;
import com.pixelservices.flash.components.http.routing.models.RouteMatch;
import com.pixelservices.flash.components.http.HttpMethod;

import java.util.*;

public class DynamicRoutePrefixTrie extends AbstractRadixRouteTrie<RouteEntry> {

    @Override
    protected RouteEntry matchResult(RouteEntry candidate, String fullPath) {
        return candidate != null && candidate.getPath().equals(fullPath) ? candidate : null;
    }

    @Override
    protected boolean isParameterSegment(String segment) {
        return segment.startsWith(":");
    }

    @Override
    protected String extractParameterName(String segment) {
        return segment.substring(1);
    }

    public RouteMatch search(HttpMethod method, String path) {
        RouteMatch match = searchInternal(method, path);
        if (match != null) {
            return match;
        }
        return searchWithWildcard(method, path);
    }

    private RouteMatch searchInternal(HttpMethod method, String path) {
        MatchResult<RouteEntry> result = super.searchWithParams(path);
        if (result != null && result.value != null && result.value.getMethod() == method) {
            return new RouteMatch(result.value, result.params);
        }
        return null;
    }

    private RouteMatch searchWithWildcard(HttpMethod method, String path) {
        MatchResult<RouteEntry> result = super.searchWithParams("/*");
        if (result != null && result.value != null && result.value.getMethod() == method) {
            Map<String, String> params = new HashMap<>();
            params.put("path", path);
            return new RouteMatch(result.value, params);
        }
        return null;
    }

    public int size() {
        return super.size();
    }
}


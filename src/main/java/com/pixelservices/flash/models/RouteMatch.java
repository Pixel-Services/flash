package com.pixelservices.flash.models;

import com.pixelservices.flash.components.RouteEntry;

import java.util.Map;

public class RouteMatch {
    private final RouteEntry entry;
    private final Map<String, String> params;
    public RouteMatch(RouteEntry entry, Map<String, String> params) {
        this.entry = entry;
        this.params = params;
    }
    public RouteEntry getEntry() { return entry; }
    public Map<String, String> getParams() { return params; }
}

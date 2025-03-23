package com.pixelservices.flash.components.http.routing.models;

import com.pixelservices.flash.components.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an HTTP request, parsed from a raw buffer.
 */
public class RequestInfo {
    private final HttpMethod method;
    private final String path;
    private final List<String> headers = new ArrayList<>();
    public RequestInfo(HttpMethod method, String path, List<String> headers) {
        this.method = method;
        this.path = path;
        this.headers.addAll(headers);
    }
    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public String getHeader(String name) {
        return headers.stream()
                .filter(h -> h.startsWith(name + ":"))
                .findFirst()
                .map(h -> h.substring(name.length() + 2))
                .orElse(null);
    }
    public List<String> getHeaders() { return headers; }
}

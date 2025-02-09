package com.pixelservices.models;

import java.util.HashMap;

/**
 * Enum for HTTP methods
 */
public enum HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, TRACE, CONNECT, OPTIONS, BEFORE, AFTER, AFTERAFTER, UNSUPPORTED;

    private static final HashMap<String, HttpMethod> methods = new HashMap<>();

    static {
        for (HttpMethod method : values()) {
            methods.put(method.toString(), method);
        }
    }

    /**
     * Gets the HttpMethod corresponding to the provided string. If no corresponding method can be found
     * {@link com.pixelservices.models.HttpMethod#UNSUPPORTED} will be returned.
     *
     * @param methodStr The string containing HTTP method name
     * @return          The HttpMethod corresponding to the provided string
     */
    public static HttpMethod get(String methodStr) {
        HttpMethod method = methods.get(methodStr.toUpperCase());
        return method != null ? method : UNSUPPORTED;
    }
}

package com.pixelservices.lifecycle;

import com.pixelservices.models.HttpMethod;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an HTTP request, parsed from a raw buffer.
 */
public class Request {
    private HttpMethod method;
    private String path;
    private final Map<String, String> headers;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> routeParams = new HashMap<>();
    private String body;
    private final InetSocketAddress clientAddress;

    /**
     * Constructs a Request object from a raw HTTP request buffer and client address.
     *
     * @param rawRequestBuffer the raw ByteBuffer containing the HTTP request
     * @param clientAddress    the client's remote address
     * @throws IllegalArgumentException if the request format is invalid
     */
    public Request(ByteBuffer rawRequestBuffer, InetSocketAddress clientAddress) {
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
        this.body = "";
        this.clientAddress = clientAddress;

        String rawRequest = decodeBuffer(rawRequestBuffer);
        String[] requestLines = rawRequest.split("\r\n");

        if (requestLines.length == 0 || requestLines[0].isEmpty()) {
            throw new IllegalArgumentException("Invalid or empty request line");
        }

        parseRequestLine(requestLines[0]);
        parseHeaders(requestLines);
        parseQueryParams();
        parseBody(requestLines);
    }

    /**
     * Decodes a ByteBuffer into a UTF-8 encoded string.
     *
     * @param buffer the ByteBuffer to decode
     * @return the decoded string
     */
    private String decodeBuffer(ByteBuffer buffer) {
        buffer.flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    /**
     * Parses the request line (method, path, and version).
     *
     * @param requestLine the first line of the HTTP request
     * @throws IllegalArgumentException if the request line format is invalid
     */
    private void parseRequestLine(String requestLine) {
        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid request line format");
        }
        this.method = parseHttpMethod(parts[0]);
        this.path = parts[1];
    }

    /**
     * Parses HTTP headers from the raw request lines.
     *
     * @param requestLines the lines of the HTTP request
     */
    private void parseHeaders(String[] requestLines) {
        int index = 1;
        while (index < requestLines.length && !requestLines[index].isEmpty()) {
            String[] header = requestLines[index].split(": ", 2);
            if (header.length == 2) {
                headers.put(header[0], header[1]);
            }
            index++;
        }
    }

    /**
     * Parses query parameters from the request path.
     */
    private void parseQueryParams() {
        if (path.contains("?")) {
            String[] parts = path.split("\\?", 2);
            String queryString = parts[1];
            for (String param : queryString.split("&")) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    queryParams.computeIfAbsent(keyValue[0], k -> new ArrayList<>()).add(keyValue[1]);
                }
            }
        }
    }

    /**
     * Parses the request body.
     *
     * @param requestLines the lines of the HTTP request
     */
    private void parseBody(String[] requestLines) {
        int bodyStartIndex = -1;
        for (int i = 0; i < requestLines.length; i++) {
            if (requestLines[i].isEmpty()) {
                bodyStartIndex = i + 1;
                break;
            }
        }

        if (bodyStartIndex != -1) {
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = bodyStartIndex; i < requestLines.length; i++) {
                bodyBuilder.append(requestLines[i]).append("\r\n");
            }
            this.body = bodyBuilder.toString().trim();
        }
    }

    /**
     * Parses the HTTP method from the request.
     *
     * @param method the HTTP method as a string
     * @return the HttpMethod enum corresponding to the method
     * @throws IllegalArgumentException if the method is unsupported
     */
    private HttpMethod parseHttpMethod(String method) {
        try {
            return HttpMethod.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method, e);
        }
    }

    /**
     * Retrieves the HTTP method of the request.
     *
     * @return the HttpMethod
     */
    public HttpMethod method() {
        return method;
    }

    /**
     * Retrieves the request path.
     *
     * @return the path as a string
     */
    public String path() {
        return path;
    }

    /**
     * Retrieves the value of a specific header.
     *
     * @param name the header name
     * @return the header value, or null if not present
     */
    public String header(String name) {
        return headers.get(name);
    }

    /**
     * Retrieves the query parameters of the request.
     *
     * @return a map of query parameters
     */
    public Map<String, List<String>> queryParams() {
        return queryParams;
    }

    /**
     * Retrieves the request body.
     *
     * @return the body as a string
     */
    public String body() {
        return body;
    }

    public void setRouteParams(Map<String, String> params) {
        routeParams.putAll(params);
    }

    public String getRouteParam(String name) {
        return routeParams.get(name);
    }

    public Map<String, String> getRouteParams() {
        return routeParams;
    }

    /**
     * Retrieves the client's remote address.
     *
     * @return the remote address as an InetSocketAddress
     */
    public InetSocketAddress clientAddress() {
        return clientAddress;
    }

    /**
     * Creates a unique route key combining method and path.
     *
     * @param method the HTTP method
     * @param path   the request path
     * @return a unique route key string
     */
    public static String createRouteKey(HttpMethod method, String path) {
        return method.name() + ":" + path.split("\\?")[0];
    }

    @Override
    public String toString() {
        return "Request{" +
                "method=" + method +
                ", path='" + path + '\'' +
                ", headers=" + headers +
                ", queryParams=" + queryParams +
                ", body='" + body + '\'' +
                '}';
    }
}

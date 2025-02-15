package com.pixelservices.flash.lifecycle;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP response, supporting serialization to a ByteBuffer.
 */
public class Response {
    private final Map<String, String> headers;
    private int statusCode;
    private String contentType;
    private Object body;
    private boolean finalized;

    /**
     * Constructs a new Response object with default status and content type.
     */
    public Response() {
        this.headers = Collections.synchronizedMap(new HashMap<>());
        this.statusCode = 200;
        this.contentType = "text/plain";
        this.finalized = false;
    }

    /**
     * Sets the HTTP status code of the response.
     *
     * @param code the status code to set
     * @return the updated Response object
     * @throws IllegalStateException if the response is already finalized
     */
    public Response status(int code) {
        ensureNotFinalized();
        this.statusCode = code;
        return this;
    }

    /**
     * Sets the content type of the response.
     *
     * @param contentType the content type to set
     * @return the updated Response object
     * @throws IllegalStateException if the response is already finalized
     */
    public Response type(String contentType) {
        ensureNotFinalized();
        this.contentType = contentType;
        return this;
    }

    /**
     * Adds a header to the response.
     *
     * @param key   the header name
     * @param value the header value
     * @return the updated Response object
     * @throws IllegalStateException if the response is already finalized
     */
    public Response header(String key, String value) {
        ensureNotFinalized();
        headers.put(key, value);
        return this;
    }

    /**
     * Sets the body of the response.
     * Warning: Overrides existing body content.
     *
     * @param body the body content
     * @return the updated Response object
     * @throws IllegalStateException if the response is already finalized
     */
    public Response body(Object body) {
        ensureNotFinalized();
        this.body = body;
        return this;
    }

    /**
     * Retrieves the body of the response.
     *
     * @return the body content
     */
    public Object getBody() {
        return body;
    }

    /**
     * Marks the response as finalized, preventing further modifications.
     */
    public void finalizeResponse() {
        this.finalized = true;
    }

    /**
     * Serializes the response into a ByteBuffer.
     *
     * @return the serialized response as a ByteBuffer
     */
    public ByteBuffer getSerialized() {
        StringBuilder responseBuilder = new StringBuilder();

        // Append status line
        responseBuilder.append("HTTP/1.1 ")
                .append(statusCode)
                .append(" ")
                .append(getStatusMessage(statusCode))
                .append("\r\n");

        // Ensure Content-Type header is set
        headers.putIfAbsent("Content-Type", contentType);

        // Append headers
        headers.forEach((key, value) -> responseBuilder.append(key).append(": ").append(value).append("\r\n"));
        responseBuilder.append("\r\n"); // End of headers

        // Append body
        byte[] bodyBytes = getSerializedBody();
        responseBuilder.append(new String(bodyBytes, StandardCharsets.UTF_8));

        return ByteBuffer.wrap(responseBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Serializes the response body based on its content type.
     *
     * @return the serialized body as a byte array
     * @throws UnsupportedOperationException if the content type is unsupported or the body type is invalid
     */
    public byte[] getSerializedBody() {
        if (body == null) {
            return new byte[0];
        }

        try {
            return serializeBody(body, contentType);
        } catch (ClassCastException e) {
            throw new UnsupportedOperationException("Invalid body type for content type: " + contentType, e);
        }
    }

    /**
     * Serializes the body based on its type and content type.
     *
     * @param body the body content
     * @param contentType the content type
     * @return the serialized body as a byte array
     * @throws UnsupportedOperationException if the body type is invalid for the content type
     */
    private byte[] serializeBody(Object body, String contentType) {
        return switch (body) {
            case String s -> s.getBytes(StandardCharsets.UTF_8);
            case JSONObject jsonObject -> body.toString().getBytes(StandardCharsets.UTF_8);
            case byte[] bytes -> bytes;
            case null, default ->
                    throw new UnsupportedOperationException("Unsupported body type for content type: " + contentType + ", received " + body.getClass().getSimpleName() + " instead");
        };
    }

    private String getStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "Unknown Status";
        };
    }

    private void ensureNotFinalized() {
        if (finalized) {
            throw new IllegalStateException("Response is already finalized");
        }
    }

    @Override
    public String toString() {
        return "Response{" +
                "headers=" + headers +
                ", statusCode=" + statusCode +
                ", contentType='" + contentType + '\'' +
                ", body=" + body +
                ", finalized=" + finalized +
                '}';
    }
}
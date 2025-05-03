package com.pixelservices.flash.components.http.lifecycle;

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
     * Constructs a new Response object with default status and a default content type of "text/plain".
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
        if (!this.finalized) { // Only finalize once
            this.finalized = true;
            byte[] bodyBytes = getSerializedBody();
            headers.put("Content-Length", String.valueOf(bodyBytes.length));
        }
    }

    /**
     * Serializes the response into a ByteBuffer.
     *
     * @return the serialized response as a ByteBuffer
     */
    public ByteBuffer getSerialized() {
        // Build headers
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("HTTP/1.1 ")
                .append(statusCode)
                .append(" ")
                .append(getStatusMessage(statusCode))
                .append("\r\n");

        headers.putIfAbsent("Content-Type", contentType); // Ensure Content-Type is set if not already

        headers.forEach((key, value) -> headerBuilder.append(key).append(": ").append(value).append("\r\n"));
        headerBuilder.append("\r\n"); // End of headers

        byte[] headerBytes = headerBuilder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bodyBytes = getSerializedBody(); // Get body bytes here

        // Combine headers and body without string conversion for body
        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + bodyBytes.length);
        buffer.put(headerBytes);
        buffer.put(bodyBytes);
        buffer.flip(); // Prepare buffer for reading

        return buffer;
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
                ", body=" + body.toString() +
                ", finalized=" + finalized +
                '}';
    }

    /**
     * Gets just the header portion of the response as a ByteBuffer.
     *
     * @return the headers as a ByteBuffer
     */
    /*
    public ByteBuffer getHeaderBuffer() {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("HTTP/1.1 ")
                .append(statusCode)
                .append(" ")
                .append(getStatusMessage(statusCode))
                .append("\r\n");

        headers.putIfAbsent("Content-Type", contentType);

        headers.forEach((key, value) -> headerBuilder.append(key).append(": ").append(value).append("\r\n"));
        headerBuilder.append("\r\n"); // End of headers

        byte[] headerBytes = headerBuilder.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length);
        buffer.put(headerBytes);
        buffer.flip();
        return buffer;
    }*/

    /**
     * Gets the body as a byte array.
     * 
     * @return the body as a byte array
     */
    public byte[] getBodyBytes() {
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
     * Gets the content type of the response.
     * 
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Gets just the header portion of the response as a byte array.
     * This is useful for chunked sending of large responses.
     *
     * @return the headers as a byte array
     */
    public byte[] getHeaderBytes() {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("HTTP/1.1 ")
                .append(statusCode)
                .append(" ")
                .append(getStatusMessage(statusCode))
                .append("\r\n");

        // Don't add Content-Type if it's null
        if (contentType != null) {
            headers.putIfAbsent("Content-Type", contentType);
        }

        // Add all headers except those that are null
        headers.entrySet().stream()
               .filter(entry -> entry.getValue() != null)
               .forEach(entry -> 
                   headerBuilder.append(entry.getKey())
                               .append(": ")
                               .append(entry.getValue())
                               .append("\r\n"));
        
        headerBuilder.append("\r\n"); // End of headers
        return headerBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Gets a ByteBuffer containing just the headers.
     * 
     * @return ByteBuffer with headers
     */
    public ByteBuffer getHeaderBuffer() {
        return ByteBuffer.wrap(getHeaderBytes());
    }
}
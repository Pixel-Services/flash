package com.pixelservices.flash.components;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MultipartParser parses HTTP multipart/form-data payloads into fields and files.
 *
 * Note: This is a simplified implementation and may not handle all edge cases or binary data correctly.
 */
public class MultipartParser {
    private final Map<String, MultipartFile> files = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();

    /**
     * Constructs a MultipartParser to parse the given content type and body.
     *
     * @param contentType the Content-Type header, expected to be "multipart/form-data".
     * @param body        the raw HTTP body to parse.
     * @throws IllegalArgumentException if the Content-Type is invalid or boundary is missing.
     */
    public MultipartParser(String contentType, String body) {
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            throw new IllegalArgumentException("Invalid Content-Type for multipart");
        }
        String[] boundaryParts = contentType.split("boundary=");
        if (boundaryParts.length < 2) {
            throw new IllegalArgumentException("No boundary found in Content-Type");
        }
        String boundary = "--" + boundaryParts[1];
        String[] parts = body.split(boundary);
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty() || part.equals("--")) {
                continue;
            }
            // Remove trailing "--" if present (end marker)
            if (part.endsWith("--")) {
                part = part.substring(0, part.length() - 2);
            }
            String[] headersAndBody = part.split("\r\n\r\n", 2);
            if (headersAndBody.length < 2) {
                continue;
            }
            String partHeaders = headersAndBody[0];
            String partBody = headersAndBody[1].trim();

            if (partHeaders.contains("Content-Disposition: form-data")) {
                String name = extractHeaderValue(partHeaders, "name");
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (partHeaders.contains("filename=")) {
                    String fileName = extractHeaderValue(partHeaders, "filename");
                    InputStream inputStream = new ByteArrayInputStream(partBody.getBytes(StandardCharsets.UTF_8));
                    files.put(name, new MultipartFile(fileName, inputStream));
                } else {
                    fields.put(name, partBody);
                }
            }
        }
    }

    /**
     * Retrieves a file by field name.
     *
     * @param fieldName the name of the field.
     * @return the MultipartFile associated with the field, or null if not found.
     */
    public MultipartFile getFile(String fieldName) {
        return files.get(fieldName);
    }

    /**
     * Retrieves a field value by name.
     *
     * @param fieldName the name of the field.
     * @return the field value, or null if not found.
     */
    public String getField(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * Extracts a header value from the Content-Disposition header.
     *
     * @param headers the headers string.
     * @param key     the key to extract (e.g., "name" or "filename").
     * @return the value associated with the key, or null if not found.
     */
    private String extractHeaderValue(String headers, String key) {
        String[] tokens = headers.split(";\\s*");
        for (String token : tokens) {
            if (token.startsWith(key + "=")) {
                String[] keyValue = token.split("=", 2);
                if (keyValue.length == 2) {
                    return keyValue[1].replace("\"", "");
                }
            }
        }
        return null;
    }

    /**
         * Represents a file included in a multipart request.
         */
        public record MultipartFile(String fileName, InputStream inputStream) {
        /**
         * Constructs a MultipartFile with a file name and input stream.
         *
         * @param fileName    the name of the file.
         * @param inputStream the input stream containing the file data.
         */
        public MultipartFile {
        }

            /**
             * Gets the name of the file.
             *
             * @return the file name.
             */
            @Override
            public String fileName() {
                return fileName;
            }

            /**
             * Gets the input stream of the file.
             *
             * @return the input stream.
             */
            @Override
            public InputStream inputStream() {
                return inputStream;
            }
        }
}

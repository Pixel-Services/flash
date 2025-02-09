package com.pixelservices.components;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MultipartParser parses HTTP multipart/form-data payloads into fields and files.
 */
public class MultipartParser {
    private final Map<String, MultipartFile> files = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();

    /**
     * Constructs a MultipartParser to parse the given content type and body.
     *
     * @param contentType the Content-Type header, expected to be "multipart/form-data"
     * @param body        the raw HTTP body to parse
     * @throws IllegalArgumentException if the Content-Type is invalid
     */
    public MultipartParser(String contentType, String body) {
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            throw new IllegalArgumentException("Invalid Content-Type for multipart");
        }

        String boundary = "--" + contentType.split("boundary=")[1];
        String[] parts = body.split(boundary);

        for (String part : parts) {
            if (part.trim().isEmpty() || part.equals("--")) continue;

            String[] headersAndBody = part.split("\r\n\r\n", 2);
            if (headersAndBody.length < 2) continue;

            String partHeaders = headersAndBody[0];
            String partBody = headersAndBody[1].trim();

            if (partHeaders.contains("Content-Disposition: form-data")) {
                String name = extractHeaderValue(partHeaders, "name");

                if (partHeaders.contains("filename=")) {
                    String fileName = extractHeaderValue(partHeaders, "filename");
                    files.put(name, new MultipartFile(fileName, new ByteArrayInputStream(partBody.getBytes(StandardCharsets.UTF_8))));
                } else {
                    fields.put(name, partBody);
                }
            }
        }
    }

    /**
     * Retrieves a file by field name.
     *
     * @param fieldName the name of the field
     * @return the MultipartFile associated with the field, or null if not found
     */
    public MultipartFile getFile(String fieldName) {
        return files.get(fieldName);
    }

    /**
     * Retrieves a field value by name.
     *
     * @param fieldName the name of the field
     * @return the field value, or null if not found
     */
    public String getField(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * Extracts a header value from the Content-Disposition header.
     *
     * @param headers the headers string
     * @param key     the key to extract (e.g., "name" or "filename")
     * @return the value associated with the key, or null if not found
     */
    private String extractHeaderValue(String headers, String key) {
        String[] tokens = headers.split(";\\s*");
        for (String token : tokens) {
            if (token.startsWith(key + "=")) {
                return token.split("=")[1].replace("\"", "");
            }
        }
        return null;
    }

    /**
     * Represents a file included in a multipart request.
     */
    public static class MultipartFile {
        private final String fileName;
        private final InputStream inputStream;

        /**
         * Constructs a MultipartFile with a file name and input stream.
         *
         * @param fileName    the name of the file
         * @param inputStream the input stream containing the file data
         */
        public MultipartFile(String fileName, InputStream inputStream) {
            this.fileName = fileName;
            this.inputStream = inputStream;
        }

        /**
         * Gets the name of the file.
         *
         * @return the file name
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * Gets the input stream of the file.
         *
         * @return the input stream
         */
        public InputStream getInputStream() {
            return inputStream;
        }
    }
}


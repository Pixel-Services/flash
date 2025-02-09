package com.pixelservices.components;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The ExpectedBodyFile lets you assume the existence of a file in the request body and work with it
 */
public class ExpectedBodyFile {
    private final String fieldName;
    private final String description;
    private final RequestHandler requestHandler;

    /**
     * Constructor for ExpectedBodyFile
     * @param fieldName The name of the file to be retrieved from the request body
     * @param requestHandler The RequestHandler object
     */
    public ExpectedBodyFile(String fieldName, RequestHandler requestHandler) {
        this.fieldName = fieldName;
        this.requestHandler = requestHandler;
        this.description = null;
    }

    /**
     * Constructor for ExpectedBodyFile
     * @param fieldName The name of the file to be retrieved from the request body
     * @param requestHandler The RequestHandler object
     */
    public ExpectedBodyFile(String fieldName, String description, RequestHandler requestHandler) {
        this.fieldName = fieldName;
        this.description = description;
        this.requestHandler = requestHandler;
    }

    /**
     * Get the InputStream of the file
     * @return The InputStream of the file
     */
    public InputStream getInputStream() {
        try {
            MultipartParser.MultipartFile file = parseMultipartFile();
            if (file == null) {
                throw new IllegalArgumentException("File not found for field: " + fieldName);
            }
            return file.getInputStream();
        } catch (Exception e) {
            throw new IllegalArgumentException("Error retrieving InputStream for field " + fieldName, e);
        }
    }

    /**
     * Get the name of the file
     * @return The name of the file
     */
    public String getFileName() {
        try {
            MultipartParser.MultipartFile file = parseMultipartFile();
            if (file == null) {
                throw new IllegalArgumentException("File not found for field: " + fieldName);
            }
            return file.getFileName();
        } catch (Exception e) {
            throw new IllegalArgumentException("Error retrieving file name for field " + fieldName, e);
        }
    }

    /**
     * Process the file with a BiConsumer
     * @param fileProcessor The BiConsumer to process the file
     */
    public void processFile(BiConsumer<InputStream, String> fileProcessor) {
        try (InputStream inputStream = getInputStream()) {
            String fileName = getFileName();
            fileProcessor.accept(inputStream, fileName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error processing file for field " + fieldName, e);
        }
    }

    /**
     * Get the field name
     * @return The field name
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Get description of the field
     * @return The description of the field
     */
    public String getDescription() {
        return description;
    }

    /**
     * Create a file from the InputStream
     * @param path The path to create the file
     * @return The created file
     */
    public File createFile(Path path) {
        try {
            File file = new File(path.toString());
            Files.copy(getInputStream(), file.toPath());
            return file;
        } catch (IOException e) {
            sendErrorResponse("Error creating file: " + e.getMessage());
            throw new IllegalArgumentException("Error creating file: " + e.getMessage(), e);
        }
    }

    /**
     * Create a file from the InputStream
     * @param path The path to create the file
     * @return The created file
     */
    public File createFile(String path) {
        return createFile(Path.of(path));
    }

    /**
     * Send an error response
     * @param message The error message
     */
    private void sendErrorResponse(String message) {
        requestHandler.getResponse().type("application/json");
        requestHandler.getResponse().status(400);
        requestHandler.getResponse().body(new JSONObject().put("error", message).toString());
    }

    /**
     * Parse the MultipartFile from the request body
     * @return The MultipartFile
     */
    private MultipartParser.MultipartFile parseMultipartFile() {
        String contentType = requestHandler.getRequest().header("Content-Type");
        String body = requestHandler.getRequest().body();
        MultipartParser parser = new MultipartParser(contentType, body);
        return parser.getFile(fieldName);
    }
}

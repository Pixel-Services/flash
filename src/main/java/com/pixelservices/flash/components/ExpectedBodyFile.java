package com.pixelservices.flash.components;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;

/**
 * The ExpectedBodyFile lets you assume the existence of a file in the request body and work with it.
 * <p>
 * Changes made:
 * - Removed caching of the parsed file.
 * - Fixed file creation by resolving the destination path using Path.resolve().
 * - Added sanitization of the file name to remove illegal characters.
 */
public class ExpectedBodyFile {
    private final String fieldName;
    private final String description;
    private final RequestHandler requestHandler;

    /**
     * Constructor for ExpectedBodyFile.
     *
     * @param fieldName      The name of the file to be retrieved from the request body.
     * @param requestHandler The RequestHandler object.
     */
    public ExpectedBodyFile(String fieldName, RequestHandler requestHandler) {
        this(fieldName, null, requestHandler);
    }

    /**
     * Constructor for ExpectedBodyFile.
     *
     * @param fieldName      The name of the file to be retrieved from the request body.
     * @param description    An optional description.
     * @param requestHandler The RequestHandler object.
     */
    public ExpectedBodyFile(String fieldName, String description, RequestHandler requestHandler) {
        this.fieldName = fieldName;
        this.description = description;
        this.requestHandler = requestHandler;
    }

    /**
     * Get the InputStream of the file.
     *
     * @return the InputStream of the file.
     */
    public InputStream getInputStream() {
        try {
            MultipartParser.MultipartFile file = parseMultipartFile();
            if (file == null) {
                throw new IllegalArgumentException("File not found for field: " + fieldName);
            }
            return file.inputStream();
        } catch (Exception e) {
            throw new IllegalArgumentException("Error retrieving InputStream for field " + fieldName, e);
        }
    }

    /**
     * Get the name of the file.
     *
     * @return the file name.
     */
    public String getFileName() {
        try {
            MultipartParser.MultipartFile file = parseMultipartFile();
            if (file == null) {
                throw new IllegalArgumentException("File not found for field: " + fieldName);
            }
            String fileName = file.fileName();
            if (fileName == null) {
                throw new IllegalArgumentException("File name is null for field: " + fieldName);
            }
            // Remove any extra content after a newline (e.g., Content-Type header info)
            fileName = fileName.replaceAll("\\r?\\n.*", "").trim();
            return fileName;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error retrieving file name for field " + fieldName, e);
        }
    }

    /**
     * Process the file with a BiConsumer.
     *
     * @param fileProcessor The BiConsumer that receives the file’s InputStream and file name.
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
     * Get the field name.
     *
     * @return the field name.
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Get the description of the field.
     *
     * @return the description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Create a file from the InputStream in the specified directory.
     *
     * @param directory       the target directory.
     * @param overrideExisting if true, overwrite any existing file; if false, do not override an existing file.
     * @return the created File or null if an error occurred.
     */
    public File createFile(Path directory, boolean overrideExisting) {
        String fileName = getFileName();
        // Resolve the destination using the sanitized file name.
        Path destination = directory.resolve(sanitizeFileName(fileName));
        System.out.println("Destination: " + destination);

        // If the file exists, and we are not overriding, return the existing file.
        if (!overrideExisting && Files.exists(destination)) {
            System.out.println("File already exists and override is disabled.");
            return destination.toFile();
        }

        try (InputStream in = getInputStream()) {
            if (overrideExisting) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(in, destination);
            }
            return destination.toFile();
        } catch (IOException e) {
            sendErrorResponse("Error creating file: " + e.getMessage());
            return null;
        }
    }


    /**
     * Overloaded method to create a file using a directory path as a String.
     *
     * @param directory the target directory as a String.
     * @return the created File.
     */
    public File createFile(String directory) {
        return createFile(Path.of(directory), false);
    }

    /**
     * Sanitize the file name by replacing illegal characters with an underscore.
     *
     * @param fileName the original file name.
     * @return the sanitized file name.
     */
    private String sanitizeFileName(String fileName) {
        // Replace characters not allowed in Windows file names: < > : " / \ | ? *
        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    /**
     * Send an error response.
     *
     * @param message The error message.
     */
    private void sendErrorResponse(String message) {
        requestHandler.getResponse().type("application/json");
        requestHandler.getResponse().status(400);
        requestHandler.getResponse().body(new JSONObject().put("error", message).toString());
    }

    /**
     * Parse the MultipartFile from the request body.
     *
     * @return the MultipartFile.
     */
    private MultipartParser.MultipartFile parseMultipartFile() {
        String contentType = requestHandler.getRequest().header("Content-Type");
        String body = requestHandler.getRequest().body();
        MultipartParser parser = new MultipartParser(contentType, body);
        return parser.getFile(fieldName);
    }
}

package flash.models;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.server.Request;
import org.json.JSONObject;

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
     * Get the file part
     * @return The file part
     */
    public Part getFilePart() {
        Part filePart = null;
        HttpServletRequest rawRequest = requestHandler.getRequest().raw();
        rawRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, new MultipartConfigElement("/temp"));

        try {
            filePart = rawRequest.getPart(fieldName);
            if (filePart == null) {
                sendErrorResponse("Missing expected file: " + fieldName);
                throw new IllegalArgumentException("Missing expected file: " + fieldName);
            }
        } catch (Exception e) {
            sendErrorResponse("Error processing file upload: " + e.getMessage());
            throw new IllegalArgumentException("Error processing file upload: " + e.getMessage(), e);
        }

        return filePart;
    }

    /**
     * Get the InputStream of the file
     * @return The InputStream of the file
     */
    public InputStream getInputStream() {
        try {
            return getFilePart().getInputStream();
        } catch (IOException e) {
            sendErrorResponse("Error reading file InputStream: " + e.getMessage());
            throw new IllegalArgumentException("Error reading file InputStream: " + e.getMessage(), e);
        }
    }

    /**
     * Get the name of the file
     * @return The name of the file
     */
    public String getFileName() {
        return getFilePart().getSubmittedFileName();
    }

    /**
     * Process the file with a BiConsumer
     * @param fileProcessor The BiConsumer to process the file
     */
    public void processFile(BiConsumer<InputStream, String> fileProcessor) {
        fileProcessor.accept(getInputStream(), getFilePart().getSubmittedFileName());
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
        requestHandler.getResponse().type("text");
        requestHandler.getResponse().status(400);
        requestHandler.getResponse().body(new JSONObject().put("error", message).toString());
    }
}

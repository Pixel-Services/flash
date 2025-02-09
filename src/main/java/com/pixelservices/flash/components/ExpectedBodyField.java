package com.pixelservices.flash.components;

import com.pixelservices.flash.utils.Parser;
import org.json.JSONObject;

/**
 * The ExpectedBodyField lets you assume the existence of a field in the request body and work with it
 */
public class ExpectedBodyField {
    private final String fieldName;
    private final RequestHandler requestHandler;
    private final String description;

    /**
     * Constructor for ExpectedBodyField
     * @param fieldName The name of the field to be retrieved from the request
     * @param requestHandler The RequestHandler object
     */
    public ExpectedBodyField(String fieldName, RequestHandler requestHandler) {
        this.fieldName = fieldName;
        this.requestHandler = requestHandler;
        this.description = null;
    }

    /**
     * Constructor for ExpectedBodyField
     * @param fieldName The name of the field to be retrieved from the request
     * @param description The description of the field
     * @param requestHandler The RequestHandler object
     */
    public ExpectedBodyField(String fieldName, String description, RequestHandler requestHandler) {
        this.fieldName = fieldName;
        this.description = description;
        this.requestHandler = requestHandler;
    }

    /**
     * Get the field value
     * @return The field value
     */
    public Object getFieldValue() {
        // Ensure the request and response objects are initialized
        JSONObject reqBody = RequestHandler.getRequestBody(requestHandler.getRequest());

        if (reqBody.isEmpty()) {
            sendErrorResponse("Invalid request body");
            throw new IllegalArgumentException("Invalid request body");
        }

        if (!reqBody.has(fieldName)) {
            sendErrorResponse("Missing expected field: " + fieldName);
            throw new IllegalArgumentException("Missing expected field: " + fieldName);
        }

        return reqBody.get(fieldName);
    }

    /**
     * Get the field value as a String
     * @return The field value as a String
     */
    public String getString() {
        return Parser.parseField(getFieldValue(), String.class, e -> {
            throwTypeError("String");
        });
    }

    /**
     * Get the field value as an Integer
     * @return The field value as an Integer
     */
    public Integer getInt() {
        return Parser.parseField(getFieldValue(), Integer.class, e -> {
            throwTypeError("Integer");
        });
    }

    /**
     * Get the field value as a Boolean
     * @return The field value as a Boolean
     */
    public Boolean getBoolean() {
        return Parser.parseField(getFieldValue(), Boolean.class, e -> {
            throwTypeError("Boolean");
        });
    }

    /**
     * Get the field value as a Long
     * @return The field value as a Long
     */
    public Long getLong() {
        return Parser.parseField(getFieldValue(), Long.class, e -> {
            throwTypeError("Long");
        });
    }

    /**
     * Get the field value as a Double
     * @return The field value as a Double
     */
    public Double getDouble() {
        return Parser.parseField(getFieldValue(), Double.class, e -> {
            throwTypeError("Double");
        });
    }

    /**
     * Get the field value as a Float
     * @return The field value as a Float
     */
    public Float getFloat() {
        return Parser.parseField(getFieldValue(), Float.class, e -> {
            throwTypeError("Float");
        });
    }

    /**
     * Get the field value as a Byte
     * @return The field value as a Byte
     */
    public Byte getByte() {
        return Parser.parseField(getFieldValue(), Byte.class, e -> {
            throwTypeError("Byte");
        });
    }

    /**
     * Get the field value as a Short
     * @return The field value as a Short
     */
    public Short getShort() {
        return Parser.parseField(getFieldValue(), Short.class, e -> {
            throwTypeError("Short");
        });
    }

    /**
     * Get the field value as a char
     * @return The field value as a char
     */
    public char getChar() {
        Object fieldValue = getFieldValue();
        if (fieldValue instanceof String && ((String) fieldValue).length() == 1) {
            return ((String) fieldValue).charAt(0);
        }
        throwTypeError("Char");
        return '\0';  // This is unreachable, added to satisfy compiler.
    }

    /**
     * Get the field value as a JSONObject
     * @return The field value as a JSONObject
     */
    public JSONObject getJSONObject() {
        Object fieldValue = getFieldValue();
        return Parser.parseField(fieldValue, JSONObject.class, e -> {
            throwTypeError("JSONObject");
            throw new IllegalArgumentException("Expected 'JSONObject', but got '" + fieldValue.getClass().getSimpleName() + "' in field '" + fieldName + "'");
        });
    }

    /**
     * Get description of the field
     * @return The description of the field
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the field value as a JSONArray
     */
    private void throwTypeError(String expectedType) {
        Object fieldValue = getFieldValue();
        sendErrorResponse("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request body field \"" + fieldName + "\"");
        throw new IllegalArgumentException("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request body field \"" + fieldName + "\"");
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
}

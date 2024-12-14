package flash.models;

import flash.utils.misc;
import org.json.JSONObject;

/**
 * The ExpectedBodyField lets you assume the existence of a field in the request body and work with it
 */
public class ExpectedBodyField {
    private final String fieldName;
    private final Object fieldValue;
    private final RequestHandler requestHandler;

    /**
     * Constructor for ExpectedBodyField
     * @param fieldName The name of the field to be retrieved from the request body
     * @param requestHandler The RequestHandler object
     */
    public ExpectedBodyField(String fieldName, RequestHandler requestHandler) {
        this.fieldName = fieldName;
        this.requestHandler = requestHandler;
        JSONObject reqBody = RequestHandler.getRequestBody(requestHandler.getRequest());

        if (reqBody == null || reqBody.isEmpty()) {
            sendErrorResponse("Invalid request body");
            throw new IllegalArgumentException("Invalid request body");
        }

        if (!reqBody.has(fieldName)) {
            sendErrorResponse("Missing expected field: " + fieldName);
            throw new IllegalArgumentException("Missing expected field: " + fieldName);
        }

        this.fieldValue = reqBody.get(fieldName);
    }

    /**
     * Get the field value as a String
     * @return The field value as a String
     */
    public String getString() {
        return misc.parseField(fieldValue, String.class, e -> {
            throwTypeError("String");
        });
    }

    /**
     * Get the field value as an Integer
     * @return The field value as an Integer
     */
    public Integer getInt() {
        return misc.parseField(fieldValue, Integer.class, e -> {
            throwTypeError("Integer");
        });
    }

    /**
     * Get the field value as a Boolean
     * @return The field value as a Boolean
     */
    public Boolean getBoolean() {
        return misc.parseField(fieldValue, Boolean.class, e -> {
            throwTypeError("Boolean");
        });
    }

    /**
     * Get the field value as a Long
     * @return The field value as a Long
     */
    public Long getLong() {
        return misc.parseField(fieldValue, Long.class, e -> {
            throwTypeError("Long");
        });
    }

    /**
     * Get the field value as a Double
     * @return The field value as a Double
     */
    public Double getDouble() {
        return misc.parseField(fieldValue, Double.class, e -> {
            throwTypeError("Double");
        });
    }

    /**
     * Get the field value as a Float
     * @return The field value as a Float
     */
    public Float getFloat() {
        return misc.parseField(fieldValue, Float.class, e -> {
            throwTypeError("Float");
        });
    }

    /**
     * Get the field value as a Byte
     * @return The field value as a Byte
     */
    public Byte getByte() {
        return misc.parseField(fieldValue, Byte.class, e -> {
            throwTypeError("Byte");
        });
    }

    /**
     * Get the field value as a Short
     * @return The field value as a Short
     */
    public Short getShort() {
        return misc.parseField(fieldValue, Short.class, e -> {
            throwTypeError("Short");
        });
    }

    /**
     * Get the field value as a char
     * @return The field value as a char
     */
    public char getChar() {
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
        return misc.parseField(fieldValue, JSONObject.class, e -> {
            throwTypeError("JSONObject");
            throw new IllegalArgumentException("Expected 'JSONObject', but got '" + fieldValue.getClass().getSimpleName() + "' in field '" + fieldName + "'");
        });
    }

    /**
     * Get the field value as a JSONArray
     */
    private void throwTypeError(String expectedType) {
        sendErrorResponse("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request body field \"" + fieldName + "\"");
        throw new IllegalArgumentException("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request body field \"" + fieldName + "\"");
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

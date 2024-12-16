package flash.models;

import flash.utils.misc;
import org.json.JSONObject;

/**
 * The ExpectedRequestParameter lets you assume the existence of a parameter in the request and work with it
 */
public class ExpectedRequestParameter {
    private final String parameterName;
    private final Object fieldValue;
    private final RequestHandler requestHandler;

    /**
     * Constructor for ExpectedRequestParameter
     * @param parameterName The name of the parameter to be retrieved from the request
     * @param requestHandler The RequestHandler object
     */
    public ExpectedRequestParameter(String parameterName, RequestHandler requestHandler) {
        this.parameterName = parameterName;
        this.requestHandler = requestHandler;
        if (!requestHandler.getRequest().queryParams().contains(parameterName)) {
            requestHandler.getResponse().status(400);
            requestHandler.getResponse().body("Error: Missing expected parameter: " + parameterName);
            throw new IllegalArgumentException("Missing expected parameter: " + parameterName);
        }

        this.fieldValue = requestHandler.getRequest().queryParams(parameterName);
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
     * Get the field value as a Character
     * @return The field value as a Character
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
        });
    }

    /**
     * Get the field value
     * @return The field value
     */
    public Object getFieldValue() {
        return fieldValue;
    }

    /**
     * Get the field value as a JSONArray
     * @return The field value as a JSONArray
     */
    private void throwTypeError(String expectedType) {
        sendErrorResponse("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request parameter \"" + parameterName + "\"");
        throw new IllegalArgumentException("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request parameter \"" + parameterName + "\"");
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

package flash.models;

import flash.utils.misc;
import org.json.JSONObject;

/**
 * The ExpectedRequestParameter lets you assume the existence of a parameter in the request and work with it
 */
public class ExpectedRequestParameter {
    private final String parameterName;
    private final String description;
    private final RequestHandler requestHandler;

    /**
     * Constructor for ExpectedRequestParameter
     * @param parameterName The name of the parameter to be retrieved from the request
     * @param requestHandler The RequestHandler object
     */
    public ExpectedRequestParameter(String parameterName, RequestHandler requestHandler) {
        this.parameterName = parameterName;
        this.requestHandler = requestHandler;
        this.description = null;
    }

    /**
     * Constructor for ExpectedRequestParameter
     * @param parameterName The name of the parameter to be retrieved from the request
     * @param requestHandler The RequestHandler object
     */
    public ExpectedRequestParameter(String parameterName, String description, RequestHandler requestHandler) {
        this.parameterName = parameterName;
        this.description = description;
        this.requestHandler = requestHandler;
    }

    /**
     * Get the field value
     * @return The field value
     */
    public Object getFieldValue() {
        // Ensure the request and response objects are initialized
        if (requestHandler.getRequest() == null) {
            throw new IllegalStateException("Request is not initialized yet.");
        }

        if (!requestHandler.getRequest().queryParams().contains(parameterName)) {
            sendErrorResponse("Missing expected parameter: " + parameterName);
            throw new IllegalArgumentException("Missing expected parameter: " + parameterName);
        }

        return requestHandler.getRequest().queryParams(parameterName);
    }

    /**
     * Get the field value as a String
     * @return The field value as a String
     */
    public String getString() {
        return misc.parseField(getFieldValue(), String.class, e -> {
            throwTypeError("String");
        });
    }

    /**
     * Get the field value as an Integer
     * @return The field value as an Integer
     */
    public Integer getInt() {
        return misc.parseField(getFieldValue(), Integer.class, e -> {
            throwTypeError("Integer");
        });
    }

    /**
     * Get the field value as a Boolean
     * @return The field value as a Boolean
     */
    public Boolean getBoolean() {
        return misc.parseField(getFieldValue(), Boolean.class, e -> {
            throwTypeError("Boolean");
        });
    }

    /**
     * Get the field value as a Long
     * @return The field value as a Long
     */
    public Long getLong() {
        return misc.parseField(getFieldValue(), Long.class, e -> {
            throwTypeError("Long");
        });
    }

    /**
     * Get the field value as a Double
     * @return The field value as a Double
     */
    public Double getDouble() {
        return misc.parseField(getFieldValue(), Double.class, e -> {
            throwTypeError("Double");
        });
    }

    /**
     * Get the field value as a Float
     * @return The field value as a Float
     */
    public Float getFloat() {
        return misc.parseField(getFieldValue(), Float.class, e -> {
            throwTypeError("Float");
        });
    }

    /**
     * Get the field value as a Byte
     * @return The field value as a Byte
     */
    public Byte getByte() {
        return misc.parseField(getFieldValue(), Byte.class, e -> {
            throwTypeError("Byte");
        });
    }

    /**
     * Get the field value as a Short
     * @return The field value as a Short
     */
    public Short getShort() {
        return misc.parseField(getFieldValue(), Short.class, e -> {
            throwTypeError("Short");
        });
    }

    /**
     * Get the field value as a Character
     * @return The field value as a Character
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
        return misc.parseField(getFieldValue(), JSONObject.class, e -> {
            throwTypeError("JSONObject");
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
     * @return The field value as a JSONArray
     */
    private void throwTypeError(String expectedType) {
        Object fieldValue = getFieldValue();
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

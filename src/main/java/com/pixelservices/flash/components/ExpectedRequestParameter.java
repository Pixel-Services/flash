package com.pixelservices.flash.components;

import com.pixelservices.flash.utils.HDIUtils;
import com.pixelservices.flash.utils.Parser;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

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
     * Get the field value.
     * If the parameter has multiple values, return the first by default.
     * To retrieve all values, use getFieldValues().
     *
     * @return The field value (first value if multiple exist).
     */
    public Object getFieldValue() {
        if (requestHandler.getRequest() == null) {
            throwHDIChainError();
        }

        // Retrieve all query parameters
        Map<String, List<String>> queryParams = requestHandler.getRequest().queryParams();

        // Check if the parameter exists
        if (!queryParams.containsKey(parameterName) || queryParams.get(parameterName).isEmpty()) {
            sendErrorResponse("Missing expected parameter: " + parameterName);
            throw new IllegalArgumentException("Missing expected parameter: " + parameterName);
        }

        return queryParams.get(parameterName).getFirst(); // Retrieve the first value safely
    }

    /**
     * Detects if the request handler is part of an HDI chain and throws a detailed error message.
     */
    private void throwHDIChainError() {
        List<Class<? extends RequestHandler>> hdiChain = HDIUtils.detectHDIChain(requestHandler.getClass());

        StringBuilder errorMsg = new StringBuilder("Request is not initialized yet.");
        errorMsg.append("\nDetected that ExpectedRequestParameter was called inside ").append(requestHandler.getClass().getSimpleName());

        if (!hdiChain.isEmpty()) {
            errorMsg.append(", which is part of an HDI chain: ");
            for (Class<? extends RequestHandler> hdi : hdiChain) {
                errorMsg.append(hdi.getSimpleName()).append(" -> ");
            }
            errorMsg.setLength(errorMsg.length() - 4); // Remove the last " -> "
        }

        errorMsg.append(".\nMake sure to call expectedRequestParameter() inside the handle method and not the constructor.");

        throw new IllegalStateException(errorMsg.toString());
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
        return Parser.parseField(getFieldValue(), JSONObject.class, e -> {
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
        requestHandler.getResponse().type("application/json");
        requestHandler.getResponse().status(400);
        requestHandler.getResponse().body(new JSONObject().put("error", message).toString());
    }
}

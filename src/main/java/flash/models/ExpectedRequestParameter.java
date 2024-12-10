package flash.models;

import flash.utils.misc;
import org.json.JSONObject;

public class ExpectedRequestParameter {
    private final String parameterName;
    private final Object fieldValue;
    private final RequestHandler requestHandler;

    public ExpectedRequestParameter(String parameterName, RequestHandler requestHandler) {
        this.parameterName = parameterName;
        this.requestHandler = requestHandler;
        // Check if the parameter exists in the request, else throw an exception
        if (!requestHandler.getRequest().queryParams().contains(parameterName)) {
            requestHandler.getResponse().status(400);
            requestHandler.getResponse().body("Error: Missing expected parameter: " + parameterName);
            throw new IllegalArgumentException("Missing expected parameter: " + parameterName);
        }

        // Retrieve the parameter value
        this.fieldValue = requestHandler.getRequest().queryParams(parameterName);
    }

    public String getString() {
        return misc.parseField(fieldValue, String.class, e -> {
            throwTypeError("String");
        });
    }

    public Integer getInt() {
        return misc.parseField(fieldValue, Integer.class, e -> {
            throwTypeError("Integer");
        });
    }

    public Boolean getBoolean() {
        return misc.parseField(fieldValue, Boolean.class, e -> {
            throwTypeError("Boolean");
        });
    }

    public Long getLong() {
        return misc.parseField(fieldValue, Long.class, e -> {
            throwTypeError("Long");
        });
    }

    public Double getDouble() {
        return misc.parseField(fieldValue, Double.class, e -> {
            throwTypeError("Double");
        });
    }

    public Float getFloat() {
        return misc.parseField(fieldValue, Float.class, e -> {
            throwTypeError("Float");
        });
    }

    public Byte getByte() {
        return misc.parseField(fieldValue, Byte.class, e -> {
            throwTypeError("Byte");
        });
    }

    public Short getShort() {
        return misc.parseField(fieldValue, Short.class, e -> {
            throwTypeError("Short");
        });
    }

    public char getChar() {
        if (fieldValue instanceof String && ((String) fieldValue).length() == 1) {
            return ((String) fieldValue).charAt(0);
        }
        throwTypeError("Char");
        return '\0';  // This is unreachable, added to satisfy compiler.
    }

    public JSONObject getJSONObject() {
        return misc.parseField(fieldValue, JSONObject.class, e -> {
            throwTypeError("JSONObject");
        });
    }

    private void throwTypeError(String expectedType) {
        sendErrorResponse("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request parameter \"" + parameterName + "\"");
        throw new IllegalArgumentException("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request parameter \"" + parameterName + "\"");
    }

    private void sendErrorResponse(String message) {
        requestHandler.getResponse().status(400);
        requestHandler.getResponse().body("Error: " + message);
    }

    public Object getFieldValue() {
        return fieldValue;
    }
}

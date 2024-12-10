package flash.models;

import flash.utils.misc;
import org.json.JSONObject;

public class ExpectedBodyField {
    private final String fieldName;
    private final Object fieldValue;
    private final RequestHandler requestHandler;

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
            throw new IllegalArgumentException("Expected 'JSONObject', but got '" + fieldValue.getClass().getSimpleName() + "' in field '" + fieldName + "'");
        });
    }

    private void throwTypeError(String expectedType) {
        sendErrorResponse("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request body field \"" + fieldName + "\"");
        throw new IllegalArgumentException("Expected '" + expectedType + "', but got '" + fieldValue.getClass().getSimpleName() + "' in request body field \"" + fieldName + "\"");
    }

    private void sendErrorResponse(String message) {
        requestHandler.getResponse().status(400);
        requestHandler.getResponse().body("Error: " + message);
    }
}

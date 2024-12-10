package flash.models;

import org.json.JSONArray;
import org.json.JSONObject;

public class ExpectedBodyField {
    private final Object fieldValue;

    public ExpectedBodyField(String fieldName, RequestHandler requestHandler) {
        JSONObject reqBody = RequestHandler.getRequestBody(requestHandler.getRequest());
        if (reqBody == null || reqBody.isEmpty()) {
            requestHandler.getResponse().status(400);
            requestHandler.getResponse().body("Error: Invalid request body");
            throw new IllegalArgumentException("Invalid request body");
        }
        if (!reqBody.has(fieldName)) {
            requestHandler.getResponse().status(400);
            requestHandler.getResponse().body("Error: Missing expected field: " + fieldName);
            throw new IllegalArgumentException("Missing expected field: " + fieldName);
        }
        this.fieldValue = reqBody.get(fieldName);
    }

    public String getString() {
        return (String) fieldValue;
    }

    public int getInt() {
        return (int) fieldValue;
    }

    public boolean getBoolean() {
        return (boolean) fieldValue;
    }

    public long getLong() {
        return (long) fieldValue;
    }

    public double getDouble() {
        return (double) fieldValue;
    }

    public float getFloat() {
        return (float) fieldValue;
    }

    public byte getByte() {
        return (byte) fieldValue;
    }

    public short getShort() {
        return (short) fieldValue;
    }

    public char getChar() {
        return (char) fieldValue;
    }

    public JSONObject getJSONObject() {
        return (JSONObject) fieldValue;
    }

    public JSONArray getJSONArray() {
        return (JSONArray) fieldValue;
    }

    public Object getFieldValue() {
        return fieldValue;
    }
}

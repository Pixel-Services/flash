package flash.models;

import org.json.JSONArray;
import org.json.JSONObject;

public class ExpectedRequestParameter {
    private final Object fieldValue;

    public ExpectedRequestParameter(String parameterName, RequestHandler requestHandler) {
        if (!requestHandler.getRequest().queryParams().contains(parameterName)) {
            requestHandler.getResponse().status(400);
            requestHandler.getResponse().body("Error: Missing expected parameter: " + parameterName);
            throw new IllegalArgumentException("Missing expected parameter: " + parameterName);
        }
        this.fieldValue = requestHandler.getRequest().queryParams(parameterName);
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

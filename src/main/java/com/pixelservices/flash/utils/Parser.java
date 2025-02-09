package com.pixelservices.flash.utils;

import org.json.JSONObject;

import java.util.function.Consumer;

/**
 * The Parser class provides methods to parse fields to specific types.
 */
public class Parser {

    /**
     * Parse a field to a specific type.
     *
     * @param fieldValue the field value to parse
     * @param clazz the class to parse the field to
     * @param onError the error handler
     * @param <T> the type to parse the field to
     * @return the parsed field
     */
    public static <T> T parseField(Object fieldValue, Class<T> clazz, Consumer<Exception> onError) {
        try {
            if (clazz == String.class) {
                return clazz.cast(fieldValue instanceof String ? fieldValue : fieldValue.toString());
            } else if (clazz == Integer.class) {
                if (fieldValue instanceof Integer) {
                    return clazz.cast(fieldValue);
                } else if (fieldValue instanceof String) {
                    return clazz.cast(Integer.parseInt((String) fieldValue));
                } else {
                    return clazz.cast(Integer.parseInt(fieldValue.toString()));
                }
            } else if (clazz == Boolean.class) {
                if (fieldValue instanceof Boolean) {
                    return clazz.cast(fieldValue);
                } else {
                    return clazz.cast(Boolean.parseBoolean(fieldValue.toString()));
                }
            } else if (clazz == Long.class) {
                if (fieldValue instanceof Long) {
                    return clazz.cast(fieldValue);
                } else if (fieldValue instanceof String) {
                    return clazz.cast(Long.parseLong((String) fieldValue));
                } else {
                    return clazz.cast(Long.parseLong(fieldValue.toString()));
                }
            } else if (clazz == Double.class) {
                if (fieldValue instanceof Double) {
                    return clazz.cast(fieldValue);
                } else if (fieldValue instanceof String) {
                    return clazz.cast(Double.parseDouble((String) fieldValue));
                } else {
                    return clazz.cast(Double.parseDouble(fieldValue.toString()));
                }
            } else if (clazz == Float.class) {
                if (fieldValue instanceof Float) {
                    return clazz.cast(fieldValue);
                } else if (fieldValue instanceof String) {
                    return clazz.cast(Float.parseFloat((String) fieldValue));
                } else {
                    return clazz.cast(Float.parseFloat(fieldValue.toString()));
                }
            } else if (clazz == Byte.class) {
                if (fieldValue instanceof Byte) {
                    return clazz.cast(fieldValue);
                } else if (fieldValue instanceof String) {
                    return clazz.cast(Byte.parseByte((String) fieldValue));
                } else {
                    return clazz.cast(Byte.parseByte(fieldValue.toString()));
                }
            } else if (clazz == Short.class) {
                if (fieldValue instanceof Short) {
                    return clazz.cast(fieldValue);
                } else if (fieldValue instanceof String) {
                    return clazz.cast(Short.parseShort((String) fieldValue));
                } else {
                    return clazz.cast(Short.parseShort(fieldValue.toString()));
                }
            } else if (clazz == JSONObject.class) {
                if (fieldValue instanceof JSONObject) {
                    return clazz.cast(fieldValue);
                } else {
                    return clazz.cast(new JSONObject(fieldValue.toString()));
                }
            }
        } catch (Exception e) {
            if (onError != null) {
                onError.accept(e);
            } else {
                throw new IllegalArgumentException("Error parsing field: " + e.getMessage(), e);
            }
        }
        return null;
    }
}

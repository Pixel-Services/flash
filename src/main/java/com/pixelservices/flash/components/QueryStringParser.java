package com.pixelservices.flash.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QueryStringParser parses URL query strings into a map of parameter names to their respective values.
 */
public class QueryStringParser {

    /**
     * Parses a URL query string into a map where keys are parameter names and values are lists of parameter values.
     *
     * @param queryString the URL query string to parse
     * @return a map containing parameter names as keys and lists of values as values
     */
    public static Map<String, List<String>> parse(String queryString) {
        Map<String, List<String>> queryMap = new ConcurrentHashMap<>();

        if (queryString == null || queryString.isEmpty()) {
            return queryMap;
        }

        String[] queryPairs = queryString.split("&");
        for (String queryPair : queryPairs) {
            String[] pair = queryPair.split("=", 2); // Limit to 2 parts to handle values with '='
            String key = pair[0];
            String value = pair.length > 1 ? pair[1] : ""; // Use empty string if no value provided

            queryMap
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(value);
        }

        return queryMap;
    }
}

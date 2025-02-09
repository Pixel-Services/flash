package com.pixelservices.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The HeaderParser class is used to parse the headers from a string
 */
public class HeaderParser {

    /**
     * Parse the header string and return a map of the headers
     * @param headerString The header string
     * @param startIndex The index to start parsing from
     * @return A map of the headers
     */
    public static Map<String, String> parse(String headerString, int startIndex) {
        Map<String, String> headerMap = new ConcurrentHashMap<>();
        if (headerString == null || headerString.isEmpty()) {
            return headerMap;
        }
        String[] headerPairs = headerString.substring(startIndex).split("\r\n");
        for (String headerPair : headerPairs) {
            String[] pair = headerPair.split(": ");
            if (pair.length == 2) {
                headerMap.put(pair[0], pair[1]);
            }
        }
        return headerMap;

    }
}

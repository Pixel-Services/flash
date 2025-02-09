package com.pixelservices.flash.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class RouteParameterParser {
    private final Pattern pattern;
    private final String[] paramNames;

    public RouteParameterParser(String endpoint) {
        String regex = endpoint.replaceAll(":(\\w+)", "(?<$1>[^/]+)");
        this.pattern = Pattern.compile(regex);
        this.paramNames = extractParamNames(endpoint);
    }

    private String[] extractParamNames(String endpoint) {
        Matcher matcher = Pattern.compile(":(\\w+)").matcher(endpoint);
        return matcher.results().map(m -> m.group(1)).toArray(String[]::new);
    }

    public Map<String, String> match(String path) {
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (String paramName : paramNames) {
            params.put(paramName, matcher.group(paramName));
        }
        return params;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String[] getParamNames() {
        return paramNames;
    }
}
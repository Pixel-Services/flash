package com.pixelservices.flash.components.routing.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoutePattern {
    private final Pattern regex;
    private final List<String> parameterNames = new ArrayList<>();

    public RoutePattern(String pattern) {
        StringBuilder sb = new StringBuilder("^");
        for (String part : pattern.split("/")) {
            if (part.isEmpty()) continue;
            sb.append("/");
            if (part.startsWith(":")) {
                sb.append("([^/]+)");
                parameterNames.add(part.substring(1));
            } else {
                sb.append(Pattern.quote(part));
            }
        }
        sb.append("/?$");
        this.regex = Pattern.compile(sb.toString());
    }

    public Map<String, String> extractParameters(String path) {
        Matcher matcher = regex.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < parameterNames.size(); i++) {
            params.put(parameterNames.get(i), matcher.group(i + 1));
        }
        return params;
    }
}





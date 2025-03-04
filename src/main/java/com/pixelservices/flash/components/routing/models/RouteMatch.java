package com.pixelservices.flash.components.routing.models;

import java.util.Map;

/**
 * RouteMatch holds a matching route and its extracted parameters.
 */
public record RouteMatch(RouteEntry entry, Map<String, String> params) {}

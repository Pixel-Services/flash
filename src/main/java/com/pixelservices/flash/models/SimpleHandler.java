package com.pixelservices.flash.models;

import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;

@FunctionalInterface
public interface SimpleHandler {
    Object handle(Request req, Response res);
}

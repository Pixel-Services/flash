package com.pixelservices.flash.components.http.routing.models;

import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;

import java.io.IOException;

@FunctionalInterface
public interface SimpleHandler {
    Object handle(Request req, Response res);
}

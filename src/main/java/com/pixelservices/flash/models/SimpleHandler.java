package com.pixelservices.models;

import com.pixelservices.lifecycle.Request;
import com.pixelservices.lifecycle.Response;

import java.io.IOException;

@FunctionalInterface
public interface SimpleHandler {
    Object handle(Request req, Response res);
}

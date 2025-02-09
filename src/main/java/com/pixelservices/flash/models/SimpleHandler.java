package com.pixelservices.flash.models;

import com.pixelservices.flash.lifecycle.Request;
import com.pixelservices.flash.lifecycle.Response;

import java.io.IOException;

@FunctionalInterface
public interface SimpleHandler {
    Object handle(Request req, Response res);
}

package com.pixelservices.flash.models;

import com.pixelservices.flash.lifecycle.Request;
import com.pixelservices.flash.lifecycle.Response;

public interface Middleware {
    /**
     * Process the request and/or response.
     * @return true to continue to the next middleware/handler, false to short-circuit.
     */
    boolean process(Request req, Response res);
}


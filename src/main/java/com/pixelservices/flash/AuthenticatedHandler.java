package com.pixelservices.flash;

import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;

public class AuthenticatedHandler<T extends Enum<T>> extends RequestHandler {
    protected final T authType;

    public AuthenticatedHandler(Request req, Response res) {
        super(req, res);
    }

    @Override
    public Object handle() {
        return null;
    }
}

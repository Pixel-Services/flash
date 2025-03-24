package com.pixelservices.flash.components.http.routing.models;

import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;

public class SimpleHandlerWrapper extends RequestHandler {
    private SimpleHandler simpleHandler;

    public SimpleHandlerWrapper(Request req, Response res) {
        super(req, res);
    }

    public SimpleHandlerWrapper(SimpleHandler simpleHandler, Request req, Response res) {
        super(req, res);
        this.simpleHandler = simpleHandler;
    }
    
    public void setSimpleHandler(SimpleHandler simpleHandler) {
        this.simpleHandler = simpleHandler;
    }

    @Override
    public Object handle() {
        if (simpleHandler == null) {
            throw new IllegalStateException("SimpleHandler not set");
        }
        return simpleHandler.handle(this.req, this.res);
    }
}
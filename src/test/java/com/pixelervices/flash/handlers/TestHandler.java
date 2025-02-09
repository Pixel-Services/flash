package com.pixelervices.flash.handlers;

import com.pixelservices.flash.components.RequestHandler;
import com.pixelservices.flash.lifecycle.Request;
import com.pixelservices.flash.lifecycle.Response;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.models.RouteInfo;

@RouteInfo(method = HttpMethod.GET, endpoint = "/helloworld")
public class TestHandler extends RequestHandler {
    public TestHandler(Request req, Response res) {
        super(req, res);
    }

    @Override
    public Object handle() {
        return "Hello, World!";
    }
}

package com.pixelervices.flash.handlers;

import com.pixelservices.flash.components.ExpectedRequestParameter;
import com.pixelservices.flash.components.RequestHandler;
import com.pixelservices.flash.lifecycle.Request;
import com.pixelservices.flash.lifecycle.Response;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.models.RouteInfo;

@RouteInfo(method = HttpMethod.GET, endpoint = "/reqparam")
public class ReqParamTestHandler extends RequestHandler {
    private final ExpectedRequestParameter testParam;

    public ReqParamTestHandler(Request req, Response res) {
        super(req, res);
        testParam = expectedRequestParameter("testParam", "Expected test parameter");
    }

    @Override
    public Object handle() {
        return "Test param: " + testParam.getString();
    }
}

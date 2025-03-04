package com.pixelervices.flash.handlers;

import com.pixelservices.flash.components.expected.ExpectedBodyField;
import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.models.RouteInfo;

@RouteInfo(method = HttpMethod.POST, endpoint = "/reqbody")
public class ReqBodyTestHandler extends RequestHandler {
    private final ExpectedBodyField testBody;

    public ReqBodyTestHandler(Request req, Response res) {
        super(req, res);
        testBody = expectedBodyField("testParam", "Expected test parameter");
    }

    @Override
    public Object handle() {
        return "Test body: " + testBody.getString();
    }
}

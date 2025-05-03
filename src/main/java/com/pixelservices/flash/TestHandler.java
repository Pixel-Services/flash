package com.pixelservices.flash;

import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.expected.ExpectedRequestParameter;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.components.http.routing.models.RouteInfo;
import com.pixelservices.flash.models.BaseHandler;

@RouteInfo(endpoint = "/test", method = HttpMethod.GET)
public class TestHandler extends BaseHandler {
    private ExpectedRequestParameter email;

    public TestHandler(Request req, Response res) {
        super(req, res);
    }

    @Override
    public void initialize() {
        email = expectedRequestParameter("email");
    }

    @Override
    public Object resolve() {
        return "Your email is: " + email.getString();
    }

}

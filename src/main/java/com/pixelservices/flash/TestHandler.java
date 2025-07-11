package com.pixelservices.flash;

import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.expected.ExpectedRequestParameter;
import com.pixelservices.flash.components.http.routing.models.RouteInfo;
import com.pixelservices.flash.models.BaseHandler;

@RouteInfo(endpoint = "/test", method = HttpMethod.GET)
public class TestHandler extends BaseHandler {
    private ExpectedRequestParameter email;

    @Override
    public void initialize() {
        email = expectedRequestParameter("email");
    }

    @Override
    public Object resolve() {
        System.out.println(req.body());
        return "Your email is: " + email.getString();
    }
}

package com.pixelervices.flash.handlers;

import com.pixelservices.flash.components.expected.ExpectedBodyFile;
import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.models.RouteInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@RouteInfo(method = HttpMethod.POST, endpoint = "/file")
public class FileHandler extends RequestHandler {
    private final ExpectedBodyFile expectedBodyFile;

    public FileHandler(Request req, Response res) {
        super(req, res);
        expectedBodyFile = expectedBodyFile("file", "Expected file");
    }

    @Override
    public Object handle() {
        try {
            InputStream inputStream = expectedBodyFile.getInputStream();
            Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8);
            res.status(200);
            return scanner.useDelimiter("\\A").next();
        } catch (Exception e) {
            res.status(401);
            return "Error";
        }
    }
}

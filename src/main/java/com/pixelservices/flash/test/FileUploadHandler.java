package com.pixelservices.flash.test;

import com.pixelservices.flash.components.ExpectedBodyFile;
import com.pixelservices.flash.components.RequestHandler;
import com.pixelservices.flash.lifecycle.Request;
import com.pixelservices.flash.lifecycle.Response;
import com.pixelservices.flash.models.HttpMethod;
import com.pixelservices.flash.models.RouteInfo;

import java.io.File;
import java.nio.file.Path;

@RouteInfo(endpoint = "/upload", method = HttpMethod.POST)
public class FileUploadHandler extends RequestHandler {
    private final ExpectedBodyFile file;

    public FileUploadHandler(Request req, Response res) {
        super(req, res);
        file = expectedBodyFile("file", "The file to upload");
    }

    @Override
    public Object handle() {
        try {
            File created = file.createFile(
                    Path.of("C://Users/elorc/Documents/Coding/Java/headless/Flash/uploadDir"),
                    true
            );
            return "Hello, Benchmark!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}

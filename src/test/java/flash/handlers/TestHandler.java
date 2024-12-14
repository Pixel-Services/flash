package flash.handlers;

import flash.Request;
import flash.Response;
import flash.models.*;
import flash.route.HttpMethod;

@RouteInfo(endpoint = "test", method = HttpMethod.GET, enforceNonNullBody = false)
public class TestHandler extends RequestHandler {

    public TestHandler(Request req, Response res) {
        super(req, res);
    }

    @Override
    public Object handle() {
        new ExpectedBodyFile("file", this)
            .processFile((inputStream, fileName) -> {
                System.out.println("Received file: " + fileName);
            });

        res.status(200);
        return "aa";
    }
}

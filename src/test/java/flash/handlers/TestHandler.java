package flash.handlers;

import flash.Request;
import flash.Response;
import flash.models.*;
import flash.route.HttpMethod;

@RouteInfo(endpoint = "test", method = HttpMethod.GET, enforceNonNullBody = false)
public class TestHandler extends RequestHandler {
    private final ExpectedRequestParameter p1;
    private final ExpectedBodyField b1;

    public TestHandler(Request req, Response res) {
        super(req, res);
        p1 = expectedRequestParameter("p1");
        b1 = expectedBodyField("b1");
    }

    @Override
    public Object handle() {
        res.status(200);
        return "param is : " + p1.getString();
    }
}

package flash;

import flash.models.ExpectedRequestParameter;
import flash.models.RequestHandler;
import flash.models.RequestMethod;
import flash.models.RouteInfo;

@RouteInfo(endpoint = "greet", method = RequestMethod.GET)
public class TestHandler extends RequestHandler {

    public TestHandler(Request req, Response res) {
        super(req, res);
    }

    @Override
    public Object handle() {
        String name = new ExpectedRequestParameter("name", this).getString();
        return "Hello, " + name + "!";
    }

}

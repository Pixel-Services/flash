package flash;

import flash.models.*;
import org.json.JSONObject;

@RouteInfo(endpoint = "greet", method = RequestMethod.GET, enforceNonNullBody = false)
public class TestHandler extends RequestHandler {

    public TestHandler(Request req, Response res) {
        super(req, res);
    }

    @Override
    public Object handle() {
        int fooParam = new ExpectedRequestParameter("fooParam", this).getInt();
        int barBodyField = new ExpectedBodyField("barBodyField", this).getInt();

        String name = fooParam + " " + barBodyField;
        res.type("text/plain");
        return "Hello, " + name + "!";
    }

}

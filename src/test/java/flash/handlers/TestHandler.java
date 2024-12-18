package flash.handlers;

import flash.Request;
import flash.Response;
import flash.models.*;
import flash.route.HttpMethod;
import org.json.JSONObject;

import java.util.List;

@RouteInfo(endpoint = "/test", method = HttpMethod.GET, enforceNonNullBody = false)
public class TestHandler extends RequestHandler {
    private final ExpectedRequestParameter p1;
    private final ExpectedRequestParameter p2;
    private final ExpectedRequestParameter token;

    public TestHandler(Request req, Response res) {
        super(req, res);
        p1 = expectedRequestParameter("p1", "The first parameter");
        p2 = expectedRequestParameter("p2", "The second parameter");
        token = expectedRequestParameter("token", "The authentication token");
    }

    @Override
    public Object handle() {
        res.status(200);
        res.type("application/json");
        JSONObject responseObject = new JSONObject();
        responseObject.put("params",
            List.of(
                p1.getString(),
                p2.getString()
            )
        );
        responseObject.put("token", token.getString());
        return responseObject;
    }
}

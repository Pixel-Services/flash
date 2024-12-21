package flash.interceptors;

import flash.Request;
import flash.Response;
import flash.models.RequestHandlerInterceptor;

import java.util.Objects;

public class AuthInterceptor implements RequestHandlerInterceptor {
    @Override
    public boolean preHandle(Request req, Response res) {
        if(req.queryParams("token") == null) {
            res.type("text/plain");
            res.status(400);
            res.body("Unauthorized: Token is required.");
            return false;
        }
        if (!Objects.equals(req.queryParams("token"), "token123")) {
            res.type("text/plain");
            res.status(400);
            res.body("Unauthorized: Supplied token is invalid.");
            return false;
        }
        return true;
    }
}

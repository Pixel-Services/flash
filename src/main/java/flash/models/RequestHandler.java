package flash.models;

import flash.Request;
import flash.Response;
import org.json.JSONObject;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.InputStream;
import java.util.Collection;

public abstract class RequestHandler {
    protected Request req;
    protected Response res;

    public RequestHandler(Request req, Response res) {
        this.req = req;
        this.res = res;
        if (shouldEnforceNonNullBody() && !assertNonNullReqBody()) {
            throw new IllegalArgumentException("Invalid request body");
        }
    }

    private boolean shouldEnforceNonNullBody() {
        RouteInfo routeInfo = this.getClass().getAnnotation(RouteInfo.class);
        return routeInfo != null && routeInfo.enforceNonNullBody();
    }

    private boolean assertNonNullReqBody() {
        JSONObject reqBody = getRequestBody();
        if (reqBody == null || reqBody.isEmpty()) {
            res.status(400);
            res.body("Error: Invalid request body");
            return false;
        }
        return true;
    }

    public JSONObject getRequestBody() {
        JSONObject json = new JSONObject();
        try {
            String contentType = req.contentType();
            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                return json; // Return empty JSON object if content type is not multipart/form-data
            }

            req.raw().setAttribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
            Collection<Part> parts = req.raw().getParts();

            for (Part part : parts) {
                if (part.getSubmittedFileName() == null) {
                    InputStream inputStream = part.getInputStream();
                    byte[] bytes = inputStream.readAllBytes();
                    String value = new String(bytes);
                    json.put(part.getName(), value);
                }
            }
            return json;

        } catch (ServletException e) {
            // Handle the case where the request body is null or unsupported content type
            return json; // Return empty JSON object
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request body", e);
        }
    }

    public static JSONObject getRequestBody(Request req) {
        JSONObject json = new JSONObject();
        try {
            req.raw().setAttribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
            Collection<Part> parts = req.raw().getParts();

            for (Part part : parts) {
                if (part.getSubmittedFileName() == null) {
                    InputStream inputStream = part.getInputStream();
                    byte[] bytes = inputStream.readAllBytes();
                    String value = new String(bytes);
                    json.put(part.getName(), value);
                }
            }
            return json;

        } catch (ServletException e) {
            // Handle the case where the request body is null or unsupported content type
            return json; // Return empty JSON object
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Request getRequest() {
        return req;
    }

    public Response getResponse() {
        return res;
    }

    public abstract Object handle();
}

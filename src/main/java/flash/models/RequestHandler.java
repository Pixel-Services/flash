package flash.models;

import flash.Request;
import flash.Response;
import org.json.JSONObject;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all request handlers
 */
public abstract class RequestHandler {
    protected Request req;
    protected Response res;
    protected HandlerSpecification specification;

    protected Map<String, ExpectedRequestParameter> expectedRequestParameters = new HashMap<>();
    protected Map<String, ExpectedBodyField> expectedBodyFields = new HashMap<>();
    protected Map<String, ExpectedBodyFile> expectedBodyFiles = new HashMap<>();

    /**
     * Constructor for RequestHandler
     * @param req The Request object
     * @param res The Response object
     */
    public RequestHandler(Request req, Response res) {
        this.req = req;
        this.res = res;
        if (shouldEnforceNonNullBody() && !assertNonNullReqBody()) {
            throw new IllegalArgumentException("Invalid request body");
        }
    }

    /**
     * Check if the enforceNonNullBody flag is set in the RouteInfo annotation
     * @return True if the enforceNonNullBody flag is set, false otherwise
     */
    private boolean shouldEnforceNonNullBody() {
        RouteInfo routeInfo = this.getClass().getAnnotation(RouteInfo.class);
        return routeInfo != null && routeInfo.enforceNonNullBody();
    }

    /**
     * Assert that the request body is not null
     * @return True if the request body is not null, false otherwise
     */
    private boolean assertNonNullReqBody() {
        JSONObject reqBody = getRequestBody();
        if (reqBody == null || reqBody.isEmpty()) {
            res.status(400);
            res.body("Error: Invalid request body");
            return false;
        }
        return true;
    }

    /**
     * Get the request body as a JSONObject
     * @return The request body as a JSONObject
     */
    public JSONObject getRequestBody() {
        return getRequestBody(req);
    }

    /**
     * Get the request object
     * @return The request object
     */
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
            return json; // Return empty JSON object
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Specify an expected request parameter with description
     * @param parameterName The name of the parameter
     * @param description The description of the parameter
     * @return The ExpectedRequestParameter object
     */
    public ExpectedRequestParameter expectedRequestParameter(String parameterName, String description) {
        if (expectedRequestParameters.containsKey(parameterName)) {
            return expectedRequestParameters.get(parameterName);
        }
        ExpectedRequestParameter instance = new ExpectedRequestParameter(parameterName, description, this);
        expectedRequestParameters.put(parameterName, instance);
        return instance;
    }

    /**
     * Specify an expected request parameter
     * @param parameterName The name of the parameter
     * @return The ExpectedRequestParameter object
     */
    public ExpectedRequestParameter expectedRequestParameter(String parameterName) {
        return expectedRequestParameter(parameterName, null);
    }

    /**
     * Specify an expected body field with description
     * @param fieldName The name of the field
     * @param description The description of the field
     * @return The ExpectedBodyField object
     */
    public ExpectedBodyField expectedBodyField(String fieldName, String description) {
        if (expectedBodyFields.containsKey(fieldName)) {
            return expectedBodyFields.get(fieldName);
        }
        ExpectedBodyField instance = new ExpectedBodyField(fieldName, description, this);
        expectedBodyFields.put(fieldName, instance);
        return instance;
    }

    /**
     * Specify an expected body field
     * @param fieldName The name of the field
     * @return The ExpectedBodyField object
     */
    public ExpectedBodyField expectedBodyField(String fieldName) {
        return expectedBodyField(fieldName, null);
    }

    /**
     * Specify an expected body file with description
     * @param fieldName The name of the file
     * @param description The description of the file
     * @return The ExpectedBodyFile object
     */
    public ExpectedBodyFile expectedBodyFile(String fieldName, String description) {
        if (expectedBodyFiles.containsKey(fieldName)) {
            return expectedBodyFiles.get(fieldName);
        }
        ExpectedBodyFile instance = new ExpectedBodyFile(fieldName, description, this);
        expectedBodyFiles.put(fieldName, instance);
        return instance;
    }

    /**
     * Specify an expected body file
     * @param fieldName The name of the file
     * @return The ExpectedBodyFile object
     */
    public ExpectedBodyFile expectedBodyFile(String fieldName) {
        return expectedBodyFile(fieldName, null);
    }

    /**
     * Get the request object
     * @return The request object
     */
    public Request getRequest() {
        return req;
    }

    /**
     * Get the response object
     * @return The response object
     */
    public Response getResponse() {
        return res;
    }

    /**
     * @return The expected request parameters
     */
    public Map<String, ExpectedRequestParameter> getExpectedRequestParameters() {
        return expectedRequestParameters;
    }

    /**
     * @return The expected body fields
     */
    public Map<String, ExpectedBodyField> getExpectedBodyFields() {
        return expectedBodyFields;
    }

    /**
     * @return The expected body files
     */
    public Map<String, ExpectedBodyFile> getExpectedBodyFiles() {
        return expectedBodyFiles;
    }

    /**
     * Set the request and response objects
     * @param req The request object
     * @param res The response object
     */
    public void setRequestResponse(Request req, Response res) {
        this.req = req;
        this.res = res;
    }

    /**
     * Get the handler specification
     * @return The handler specification
     */
    public HandlerSpecification getSpecification() {
        return specification;
    }

    /**
     * Sets the handler specification
     * @param specification The handler specification
     */
    public void setSpecification(HandlerSpecification specification) {
        this.specification = specification;
    }

    /**
     * Handle the request
     * @return The response object
     */
    public abstract Object handle();
}

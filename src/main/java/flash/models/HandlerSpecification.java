package flash.models;

import flash.models.*;
import flash.route.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class HandlerSpecification {
    private final String endpoint;
    private final HttpMethod method;
    private final Map<String, String> expectedValues;
    private final String responseType;
    private final boolean enforcedNonNullBody;

    public HandlerSpecification(RequestHandler requestHandler, String endpoint, HttpMethod method, String responseType, boolean enforcedNonNullBody) {
        this.endpoint = endpoint;
        this.method = method;
        this.responseType = responseType;
        this.enforcedNonNullBody = enforcedNonNullBody;

        Map<String, String> expectedFields = requestHandler.getExpectedFields();

        this.expectedValues = new HashMap<>(expectedFields);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public Map<String, String> getExpectedValues() {
        return expectedValues;
    }

    public String getResponseType() {
        return responseType;
    }

    public boolean isEnforcedNonNullBody() {
        return enforcedNonNullBody;
    }

    @Override
    public String toString() {
        return "HandlerSpecification{" +
            "endpoint='" + endpoint + '\'' +
            ", method=" + method +
            ", expectedValues=" + expectedValues +
            ", responseType='" + responseType + '\'' +
            ", enforcedNonNullBody=" + enforcedNonNullBody +
            '}';
    }
}

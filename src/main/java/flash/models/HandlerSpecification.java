package flash.models;

import flash.route.HttpMethod;

import java.util.Map;

public class HandlerSpecification {
    private final String endpoint;
    private final HttpMethod method;
    private final Map<String, ExpectedRequestParameter> expectedRequestParameters;
    private final Map<String, ExpectedBodyField> expectedBodyFields;
    private final Map<String, ExpectedBodyFile> expectedBodyFiles;
    private final boolean enforcedNonNullBody;

    public HandlerSpecification(RequestHandler requestHandler, String endpoint, HttpMethod method, boolean enforcedNonNullBody) {
        this.endpoint = endpoint;
        this.method = method;
        this.enforcedNonNullBody = enforcedNonNullBody;

        this.expectedRequestParameters = requestHandler.getExpectedRequestParameters();
        this.expectedBodyFields = requestHandler.getExpectedBodyFields();
        this.expectedBodyFiles = requestHandler.getExpectedBodyFiles();
    }

    public String getEndpoint() {
        return endpoint;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public boolean isEnforcedNonNullBody() {
        return enforcedNonNullBody;
    }

    public Map<String, ExpectedRequestParameter> getExpectedRequestParameters() {
        return expectedRequestParameters;
    }

    public Map<String, ExpectedBodyField> getExpectedBodyFields() {
        return expectedBodyFields;
    }

    public Map<String, ExpectedBodyFile> getExpectedBodyFiles() {
        return expectedBodyFiles;
    }

    @Override
    public String toString() {
        return "HandlerSpecification{" +
                "endpoint='" + endpoint + '\'' +
                ", method=" + method +
                ", expectedRequestParameters=" + expectedRequestParameters +
                ", expectedBodyFields=" + expectedBodyFields +
                ", expectedBodyFiles=" + expectedBodyFiles +
                ", enforcedNonNullBody=" + enforcedNonNullBody +
                '}';
    }
}

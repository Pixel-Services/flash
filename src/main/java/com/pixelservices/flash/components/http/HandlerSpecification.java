package com.pixelservices.flash.components.http;

import com.pixelservices.flash.components.http.expected.ExpectedBodyField;
import com.pixelservices.flash.components.http.expected.ExpectedBodyFile;
import com.pixelservices.flash.components.http.expected.ExpectedRequestParameter;

import java.util.Map;

/**
 * Represents the specification for a request handler, including details about the endpoint,
 * HTTP method, and expected request structure.
 */
public class HandlerSpecification {

    private final String endpoint;
    private final HttpMethod method;
    private final Map<String, ExpectedRequestParameter> expectedRequestParameters;
    private final Map<String, ExpectedBodyField> expectedBodyFields;
    private final Map<String, ExpectedBodyFile> expectedBodyFiles;
    private final boolean enforcedNonNullBody;

    /**
     * Constructs a HandlerSpecification for a given request handler, endpoint, HTTP method,
     * and body enforcement policy.
     *
     * @param requestHandler       the associated RequestHandler
     * @param endpoint             the endpoint for this handler
     * @param method               the HTTP method (e.g., GET, POST)
     * @param enforcedNonNullBody  whether a non-null body is enforced
     */
    public HandlerSpecification(RequestHandler requestHandler, String endpoint, HttpMethod method, boolean enforcedNonNullBody) {
        this.endpoint = endpoint;
        this.method = method;
        this.enforcedNonNullBody = enforcedNonNullBody;

        this.expectedRequestParameters = requestHandler.getExpectedRequestParameters();
        this.expectedBodyFields = requestHandler.getExpectedBodyFields();
        this.expectedBodyFiles = requestHandler.getExpectedBodyFiles();
    }

    /**
     * Retrieves the endpoint for this handler.
     *
     * @return the endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Retrieves the HTTP method for this handler.
     *
     * @return the HTTP method
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * Indicates whether a non-null body is enforced for requests.
     *
     * @return {@code true} if a non-null body is enforced, otherwise {@code false}
     */
    public boolean isEnforcedNonNullBody() {
        return enforcedNonNullBody;
    }

    /**
     * Retrieves the expected request parameters for this handler.
     *
     * @return a map of parameter names to their specifications
     */
    public Map<String, ExpectedRequestParameter> getExpectedRequestParameters() {
        return expectedRequestParameters;
    }

    /**
     * Retrieves the expected body fields for this handler.
     *
     * @return a map of field names to their specifications
     */
    public Map<String, ExpectedBodyField> getExpectedBodyFields() {
        return expectedBodyFields;
    }

    /**
     * Retrieves the expected body files for this handler.
     *
     * @return a map of file names to their specifications
     */
    public Map<String, ExpectedBodyFile> getExpectedBodyFiles() {
        return expectedBodyFiles;
    }

    /**
     * Returns a string representation of the HandlerSpecification, including endpoint,
     * HTTP method, expected request details, and body enforcement status.
     *
     * @return a string representation of the specification
     */
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


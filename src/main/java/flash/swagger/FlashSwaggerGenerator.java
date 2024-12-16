package flash.swagger;

import flash.FlashServer;
import flash.models.ExpectedBodyField;
import flash.models.ExpectedBodyFile;
import flash.models.ExpectedRequestParameter;
import flash.models.HandlerSpecification;
import flash.route.RouteController;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class FlashSwaggerGenerator {
    private final FlashServer server;
    private final OpenAPI openAPI;

    public FlashSwaggerGenerator(FlashServer server, String version, String title, String description) {
        this.server = server;
        openAPI = new OpenAPI();
        openAPI.setInfo(new Info()
                .title(title)
                .version(version)
                .description(description));
    }

    public JSONObject generate() {
        Map<String, RouteController> routeControllers = server.getRouteControllers();
        routeControllers.forEach((base, controller) -> {
            controller.getHandlers().forEach(handler -> {
                HandlerSpecification handlerSpec = handler.getSpecification();
                addEndpoint(handlerSpec);
            });
        });
        String json = io.swagger.v3.core.util.Json.pretty(openAPI);
        return new JSONObject(json);
    }

    /**
     * Adds an endpoint to the OpenAPI specification based on the given HandlerSpecification.
     *
     * @param handlerSpec The handler specification containing endpoint metadata.
     */
    private void addEndpoint(HandlerSpecification handlerSpec) {
        // Ensure the path exists in the OpenAPI Paths object
        openAPI.setPaths(openAPI.getPaths() != null ? openAPI.getPaths() : new Paths());
        PathItem pathItem = openAPI.getPaths().computeIfAbsent(handlerSpec.getEndpoint(), k -> new PathItem());

        // Create the operation for the HTTP method
        Operation operation = new Operation()
            .responses(new ApiResponses());

        // Add expected values (parameters or body)
        Map<String, ExpectedRequestParameter> expectedRequestParameters = handlerSpec.getExpectedRequestParameters();
        Map<String, ExpectedBodyField> expectedBodyFields = handlerSpec.getExpectedBodyFields();
        Map<String, ExpectedBodyFile> expectedBodyFiles = handlerSpec.getExpectedBodyFiles();

        expectedRequestParameters.forEach((paramName, expectedParam) ->
            operation.addParametersItem(createQueryParameter(paramName))
        );
        expectedBodyFields.forEach((fieldName, expectedField) ->
            operation.setRequestBody(createJsonBody(fieldName))
        );
        expectedBodyFiles.forEach((fieldName, expectedFile) ->
            operation.setRequestBody(createFileBody(fieldName))
        );

        // Map the method to the path
        setOperationForHttpMethod(pathItem, handlerSpec.getMethod(), operation);

        // Update the paths in the OpenAPI spec
        openAPI.getPaths().addPathItem(handlerSpec.getEndpoint(), pathItem);
    }

    // Helper methods for creating OpenAPI components

    private static ApiResponse createSuccessResponse() {
        return new ApiResponse()
                .description("Success")
                .content(new Content()
                        .addMediaType("application/json", new MediaType().schema(new Schema<>().type("object"))));
    }

    private static Parameter createQueryParameter(String paramName) {
        return new Parameter()
                .name(paramName)
                .in("query")
                .schema(new Schema<>().type("String, casted to the correct type"))
                .required(true);
    }

    private static RequestBody createJsonBody(String fieldName) {
        return new RequestBody()
                .required(true)
                .content(new Content()
                        .addMediaType("multipart/form-data", new MediaType()
                                .schema(new Schema<>().type("object")
                                        .addProperties(fieldName, new Schema<>().type("Text, casted to the correct type")))));
    }

    private static RequestBody createFileBody(String fieldName) {
        return new RequestBody()
                .required(true)
                .content(new Content()
                        .addMediaType("multipart/form-data", new MediaType()
                                .schema(new Schema<>().type("object")
                                        .addProperties(fieldName, new Schema<>().type("file").format("binary")))));
    }

    private static void setOperationForHttpMethod(PathItem pathItem, flash.route.HttpMethod method, Operation operation) {
        switch (method) {
            case GET -> pathItem.setGet(operation);
            case POST -> pathItem.setPost(operation);
            case PUT -> pathItem.setPut(operation);
            case DELETE -> pathItem.setDelete(operation);
            case PATCH -> pathItem.setPatch(operation);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }
}

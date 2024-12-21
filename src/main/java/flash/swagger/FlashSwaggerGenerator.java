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
import io.swagger.v3.oas.models.servers.Server;
import org.json.JSONObject;

import java.util.Map;

public class FlashSwaggerGenerator {
    private final FlashServer server;
    private final OpenAPI openAPI;
    private final FlashSwaggerConfiguration config;

    public FlashSwaggerGenerator(FlashServer server, FlashSwaggerConfiguration config) {
        this.server = server;
        this.config = config;
        openAPI = new OpenAPI();
        openAPI.setInfo(new Info()
            .title(config.getTitle())
            .version(config.getVersion())
            .description(config.getDescription()));
        config.getServers().forEach(url -> openAPI.addServersItem(new Server().url(url)));
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

    private void addEndpoint(HandlerSpecification handlerSpec) {
        openAPI.setPaths(openAPI.getPaths() != null ? openAPI.getPaths() : new Paths());
        PathItem pathItem = openAPI.getPaths().computeIfAbsent(handlerSpec.getEndpoint(), k -> new PathItem());

        Operation operation = new Operation()
            .responses(new ApiResponses());

        Map<String, ExpectedRequestParameter> expectedRequestParameters = handlerSpec.getExpectedRequestParameters();
        Map<String, ExpectedBodyField> expectedBodyFields = handlerSpec.getExpectedBodyFields();
        Map<String, ExpectedBodyFile> expectedBodyFiles = handlerSpec.getExpectedBodyFiles();

        expectedRequestParameters.forEach((paramName, expectedParam) ->
            operation.addParametersItem(createQueryParameter(paramName, expectedParam))
        );
        expectedBodyFields.forEach((fieldName, expectedField) ->
            operation.setRequestBody(createJsonBody(fieldName, expectedField))
        );
        expectedBodyFiles.forEach((fieldName, expectedFile) ->
            operation.setRequestBody(createFileBody(fieldName, expectedFile))
        );

        setOperationForHttpMethod(pathItem, handlerSpec.getMethod(), operation);

        openAPI.getPaths().addPathItem(handlerSpec.getEndpoint(), pathItem);
    }

    private static ApiResponse createSuccessResponse() {
        return new ApiResponse()
            .description("Success")
            .content(new Content()
                .addMediaType("application/json", new MediaType().schema(new Schema<>().type("object"))));
    }

    private static Parameter createQueryParameter(String paramName, ExpectedRequestParameter exp) {
        String description = exp.getDescription();
        Parameter parameter = new Parameter()
            .name(paramName)
            .in("query")
            .schema(new Schema<>().type("string"))
            .required(true);
        if (description != null && !description.isEmpty()) {
            parameter.setDescription(description);
        }
        return parameter;
    }

    private static RequestBody createJsonBody(String fieldName, ExpectedBodyField exp) {
        String description = exp.getDescription();
        Schema<?> schema = new Schema<>().type("object")
            .addProperties(fieldName, new Schema<>().type("string"));
        if (description != null && !description.isEmpty()) {
            schema.setDescription(description);
        }
        return new RequestBody()
            .required(true)
            .content(new Content()
                .addMediaType("application/json", new MediaType().schema(schema)));
    }

    private static RequestBody createFileBody(String fieldName, ExpectedBodyFile exp) {
        String description = exp.getDescription();
        Schema<?> schema = new Schema<>().type("object")
            .addProperties(fieldName, new Schema<>().type("string").format("binary"));
        if (description != null && !description.isEmpty()) {
            schema.setDescription(description);
        }
        return new RequestBody()
            .required(true)
            .content(new Content()
                .addMediaType("multipart/form-data", new MediaType().schema(schema)));
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

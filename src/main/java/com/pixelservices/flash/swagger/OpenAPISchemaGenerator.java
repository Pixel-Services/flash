package com.pixelservices.flash.swagger;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.expected.ExpectedRequestParameter;
import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.HandlerSpecification;
import com.pixelservices.flash.components.http.HandlerType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Operation;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * Generates Swagger documentation for a FlashServer instance.
 */
public class OpenAPISchemaGenerator {
    private final FlashServer server;
    private final OpenAPI openAPI;
    private final OpenAPIConfiguration config;

    public OpenAPISchemaGenerator(FlashServer server, OpenAPIConfiguration config) {
        this.server = server;
        this.config = config;
        openAPI = new OpenAPI();
        openAPI.setInfo(new Info()
                .title(config.getTitle())
                .version(config.getVersion())
                .description(config.getDescription()));
        config.getServers().forEach(url -> openAPI.addServersItem(new Server().url(url)));
    }

    /**
     * Generates the OpenAPI documentation as a JSONObject.
     *
     * @return JSONObject containing the OpenAPI documentation.
     */
    public JSONObject generate() {
        Map<String, RequestHandler> routeHandlers = server.getRouteHandlers();
        routeHandlers.forEach((routeKey, handler) -> {
            List<HandlerType> ignoredTypes = List.of(
                    HandlerType.STATIC, HandlerType.INTERNAL, HandlerType.REDIRECT
            );
            if (!ignoredTypes.contains(handler.getHandlerType())) {
                HandlerSpecification handlerSpec = handler.getSpecification();
                if (handlerSpec != null) {
                    addEndpoint(handlerSpec);
                }
            }
        });
        String json = io.swagger.v3.core.util.Json.pretty(openAPI);
        return new JSONObject(json);
    }

    private void addEndpoint(HandlerSpecification handlerSpec) {
        openAPI.setPaths(openAPI.getPaths() != null ? openAPI.getPaths() : new Paths());
        PathItem pathItem = openAPI.getPaths().computeIfAbsent(handlerSpec.getEndpoint(), k -> new PathItem());

        Operation operation = new Operation()
                .responses(new ApiResponses().addApiResponse("200", createSuccessResponse()));

        handlerSpec.getExpectedRequestParameters().forEach((paramName, expectedParam) ->
                operation.addParametersItem(createQueryParameter(paramName, expectedParam))
        );

        if (!handlerSpec.getExpectedBodyFields().isEmpty() || !handlerSpec.getExpectedBodyFiles().isEmpty()) {
            operation.setRequestBody(createRequestBody(handlerSpec));
        }

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

    private RequestBody createRequestBody(HandlerSpecification handlerSpec) {
        Content content = new Content();

        if (!handlerSpec.getExpectedBodyFields().isEmpty()) {
            Schema<?> schema = new Schema<>().type("object");
            handlerSpec.getExpectedBodyFields().forEach((fieldName, expectedField) -> {
                schema.addProperties(fieldName, new Schema<>().type("string"));
            });
            content.addMediaType("application/json", new MediaType().schema(schema));
        }

        if (!handlerSpec.getExpectedBodyFiles().isEmpty()) {
            Schema<?> schema = new Schema<>().type("object");
            handlerSpec.getExpectedBodyFiles().forEach((fieldName, expectedFile) -> {
                schema.addProperties(fieldName, new Schema<>().type("string").format("binary"));
            });
            content.addMediaType("multipart/form-data", new MediaType().schema(schema));
        }

        return new RequestBody().content(content).required(true);
    }

    private static void setOperationForHttpMethod(PathItem pathItem, HttpMethod method, Operation operation) {
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
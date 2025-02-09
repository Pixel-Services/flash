package com.pixelservices.flash.swagger;

public enum OpenAPIUITemplate {
    SWAGGER_UI("""
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Swagger UI</title>
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.0.0/swagger-ui.css">
        </head>
        <body>
            <div id="swagger-ui"></div>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.0.0/swagger-ui-bundle.js"></script>
            <script>
                window.onload = () => {
                    const ui = SwaggerUIBundle({
                        url: "%s/schema.json",
                        dom_id: '#swagger-ui',
                    });
                };
            </script>
        </body>
        </html>
        """),

    REDOC("""
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Redoc</title>
        </head>
        <body>
            <redoc spec-url="%s/schema.json"></redoc>
            <script src="https://cdn.redoc.ly/redoc/latest/bundles/redoc.standalone.js"> </script>
        </body>
        </html>
        """),

    CUSTOM("");

    private String template;

    OpenAPIUITemplate(String template) {
        this.template = template;
    }

    /**
     * Gets the template for the UI.
     *
     * @return The template for the UI.
     */
    public String getTemplate() {
        return this.template;
    }

    /**
     * Sets the custom HTML template for the UI.
     * It will be available as {@link OpenAPIUITemplate#CUSTOM}.
     * You can reference the endpoint using the placeholder %s, the schema.json
     * will be accessible at the endpoint as %s/schema.json.
     *
     * @param customTemplate The custom template for the UI as an HTML string.
     */
    public static void setCustom(String customTemplate) {
        CUSTOM.applyCustom(customTemplate);
    }

    private void applyCustom(String customTemplate) {
        this.template = customTemplate;
    }
}

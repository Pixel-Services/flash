package com.pixelservices.flash.swagger;

import java.util.List;

/**
 * Represents the configuration for the Swagger UI.
 */
public class OpenAPIConfiguration {
    private final String title;
    private final String description;
    private final String version;
    private final List<String> servers;

    /**
     * Creates a new FlashSwaggerConfiguration object.
     *
     * @param title        The title of the Swagger UI.
     * @param description  The description of the Swagger UI.
     * @param version      The version of the Swagger UI.
     * @param servers      The list of servers for the Swagger UI.
     */
    public OpenAPIConfiguration(String title, String description, String version, List<String> servers) {
        this.title = title;
        this.description = description;
        this.version = version;
        this.servers = servers;
    }

    /**
     * Gets the title of the Swagger UI.
     *
     * @return The title of the Swagger UI.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets the description of the Swagger UI.
     *
     * @return The description of the Swagger UI.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the version of the Swagger UI.
     *
     * @return The version of the Swagger UI.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the list of servers for the Swagger UI.
     *
     * @return The list of servers for the Swagger UI.
     */
    public List<String> getServers() {
        return servers;
    }
}

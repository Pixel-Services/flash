package com.pixelservices.flash;

public enum AuthenticationType {
    NONE, // No authentication required
    BASIC, // Basic authentication (username/password)
    TOKEN, // Token-based authentication (e.g., JWT)
    JWT, // JSON Web Token authentication
    API_KEY; // API key authentication

    /**
     * Checks if the authentication type requires credentials.
     *
     * @return true if credentials are required, false otherwise.
     */
    public boolean requiresCredentials() {
        return this != NONE;
    }
}
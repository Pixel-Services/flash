package com.pixelservices.flash.components.http.routing.models;

import com.pixelservices.flash.components.http.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify routing information for a request handler.
 * This annotation defines the endpoint, HTTP method, and whether a non-null body
 * is enforced for the annotated request handler class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RouteInfo {

    /**
     * Specifies the endpoint for the request handler.
     *
     * @return the endpoint path (e.g., "/resource")
     */
    String endpoint();

    /**
     * Specifies the HTTP method for the request handler.
     *
     * @return the HTTP method (e.g., GET, POST)
     */
    HttpMethod method();

    /**
     * Indicates whether a non-null body is enforced for requests handled by this route.
     * By default, this is set to {@code false}.
     *
     * @return {@code true} if a non-null body is required, otherwise {@code false}
     */
    boolean enforceNonNullBody() default false;
}


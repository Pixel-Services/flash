package com.pixelservices.flash.models;

import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;

/**
 * A base handler class that eliminates the need for boilerplate constructors.
 * Developers can simply extend this class without having to create a constructor
 * that passes request and response objects to the parent class.
 */
public abstract class BaseHandler extends RequestHandler {
    
    /**
     * Default constructor that passes null request and response objects.
     * These will be set later by the HandlerPool when the handler is acquired.
     */
    public BaseHandler() {
        super(null, null);
    }
    
    /**
     * Constructor with request and response objects.
     * This is used internally by the framework.
     */
    public BaseHandler(Request req, Response res) {
        super(req, res);
    }
    
    /**
     * Initialize method that's called after request and response are set.
     * Override this method instead of creating a constructor.
     */
    protected void initialize() {
        // default implementation
    }
    
    @Override
    public void setRequestResponse(Request req, Response res) {
        super.setRequestResponse(req, res);
        if (req != null && res != null) {
            initialize();
        }
    }
}

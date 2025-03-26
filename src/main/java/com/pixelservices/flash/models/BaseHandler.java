package com.pixelservices.flash.models;

import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import org.json.JSONObject;

/**
 * A base handler class that eliminates the need for boilerplate constructors.
 * Developers can simply extend this class without having to create a constructor
 * that passes request and response objects to the parent class.
 */
public abstract class BaseHandler extends RequestHandler {
    
    // State tracking for the current request
    private boolean shouldContinue = true;
    private Object result = null;
    
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
    
    /**
     * The main handle method that processes the request.
     * This implementation calls the process method and then either returns the result
     * or calls the resolve method.
     */
    @Override
    public final Object handle() {
        // Reset state for this request
        shouldContinue = true;
        result = null;
        
        // Process the request
        process();
        
        // If we shouldn't continue, return the result
        if (!shouldContinue) {
            return result;
        }
        
        return resolve();
    }
    
    /**
     * Process method that should be implemented by handler classes.
     * This is where validation and middleware-like logic should be placed.
     * By default, it just continues to the next step.
     */
    protected void process() {
        // Default implementation just continues
        next();
    }
    
    /**
     * Handle implementation method that should be implemented by concrete handlers.
     * This is where the final handler logic should be placed.
     */
    protected Object resolve() {
        return null;
    }
    
    /**
     * Continues processing to the next step.
     * This should be called when the current processing is successful.
     */
    protected final void next() {
        shouldContinue = true;
    }
    
    /**
     * Fails the request with a custom message and status code.
     * This should be called when an error condition is detected.
     * 
     * @param message The error message
     * @param status The HTTP status code
     */
    protected final void fail(String message, int status) {
        shouldContinue = false;
        res.status(status);
        
        JSONObject errorJson = new JSONObject();
        errorJson.put("error", message);
        
        result = errorJson.toString();
        res.type("application/json");
    }
    
    /**
     * Fails the request with a custom message and default 400 status code.
     * 
     * @param message The error message
     */
    protected final void fail(String message) {
        fail(message, 400);
    }
    
    /**
     * Completes the request with a custom result.
     * This should be called when you want to return a result without
     * continuing to the next step.
     * 
     * @param customResult The result to return
     */
    protected final void complete(Object customResult) {
        shouldContinue = false;
        result = customResult;
    }
}

package com.pixelservices.flash.components.http.routing.models;

import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;

/**
 * Wraps a SimpleHandler into a full RequestHandler with ID and metadata.
 */
public class SimpleHandlerWrapper extends RequestHandler {

    private SimpleHandler simpleHandler;
    private String handlerId;
    private String handlerName;
    private Class<?> handlerClass;

    public SimpleHandlerWrapper(Request req, Response res) {
        super(req, res);
    }

    public void setSimpleHandler(SimpleHandler simpleHandler) {
        if (simpleHandler == null) {
            throw new IllegalArgumentException("SimpleHandler cannot be null");
        }

        this.simpleHandler = simpleHandler;

        this.handlerName = simpleHandler.getClass().getSimpleName();
        if (this.handlerName.contains("$Lambda$")) {
            this.handlerName = "LambdaSimpleHandler";
        }

        this.handlerId = this.handlerName + "-" + Math.abs(simpleHandler.hashCode());
        this.handlerClass = simpleHandler.getClass();
    }

    @Override
    public Object handle() {
        if (simpleHandler == null) {
            throw new IllegalStateException("SimpleHandler not set");
        }
        return simpleHandler.handle(req, res);
    }

    public String getHandlerId() {
        return handlerId;
    }

    @Override
    public String getHandlerName() {
        return handlerName;
    }

    public SimpleHandler getSimpleHandler() {
        return simpleHandler;
    }

    /**
     * Returns the effective class of the underlying handler for use by pooling mechanisms.
     */
    public Class<?> getHandlerClass() {
        return handlerClass != null ? handlerClass : getClass();
    }
}

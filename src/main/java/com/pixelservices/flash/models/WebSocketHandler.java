package com.pixelservices.flash.models;

public abstract class WebSocketHandler {
    public abstract void onOpen(WebSocketSession session);
    public abstract void onClose(WebSocketSession session, int statusCode, String reason);
    public abstract void onMessage(WebSocketSession session, String message);
    public abstract void onError(WebSocketSession session, Throwable error);
}

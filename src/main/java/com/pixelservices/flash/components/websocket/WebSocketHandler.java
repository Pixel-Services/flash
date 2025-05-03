package com.pixelservices.flash.components.websocket;

public abstract class WebSocketHandler {
    public abstract void onOpen(WebSocketSession session);
    public abstract void onClose(WebSocketSession session, int statusCode, String reason);
    public void onMessage(WebSocketSession session, String message) {}
    public void onMessage(WebSocketSession session, byte[] message) {}
    public abstract void onError(WebSocketSession session, Throwable error);
}

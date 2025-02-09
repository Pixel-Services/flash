package com.pixelservices.flash.exceptions;

public class ServerStartupException extends java.lang.RuntimeException {
    public ServerStartupException(String message, Exception e) {
        super(message, e);
    }
}

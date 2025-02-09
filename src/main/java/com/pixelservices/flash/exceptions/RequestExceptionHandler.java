package com.pixelservices.flash.exceptions;

import com.pixelservices.flash.lifecycle.Response;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * Handles exceptions that occur during request processing and sends an appropriate error response to the client.
 */
public class RequestExceptionHandler {
    private final AsynchronousSocketChannel clientChannel;
    private final Exception exception;

    public RequestExceptionHandler(AsynchronousSocketChannel clientChannel, Exception exception) {
        this.clientChannel = clientChannel;
        this.exception = exception;
    }

    /**
     * Handles the exception by sending an appropriate error response to the client.
     */
    public void handle() {
        switch (exception) {
            case IllegalArgumentException illegalArgumentException ->
                    sendErrorResponse(400, "Validation error: " + exception.getMessage());
            case UnsupportedOperationException unsupportedOperationException ->
                    sendErrorResponse(415, "Unsupported operation: " + exception.getMessage());
            case UnmatchedMethodException unmatchedMethodException ->
                    sendErrorResponse(405, "Method not allowed: " + exception.getMessage());
            default -> sendErrorResponse(500, "Internal server error: " + exception.getMessage());
        }
    }

    /**
     * Sends an error response with the given status code and message to the client.
     *
     * @param statusCode the HTTP status code of the error response
     * @param message    the error message to include in the response
     */
    private void sendErrorResponse(int statusCode, String message) {
        Response errorResponse = new Response();
        errorResponse.status(statusCode).body(message).type("text/plain");
        try {
            ByteBuffer responseBuffer = errorResponse.getSerialized();
            clientChannel.write(responseBuffer).get();
        } catch (Exception ex) {
            PrettyLogger.logWithEmoji("Error sending error response: " + ex.getMessage(), "⚠️");
        } finally {
            closeSocket();
        }
    }

    /**
     * Closes the client socket.
     */
    private void closeSocket() {
        try {
            clientChannel.close();
        } catch (IOException e) {
            PrettyLogger.logWithEmoji("Error closing socket: " + e.getMessage(), "❌");
        }
    }
}

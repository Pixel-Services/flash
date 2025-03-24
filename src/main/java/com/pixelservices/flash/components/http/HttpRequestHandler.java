package com.pixelservices.flash.components.http;

import com.pixelservices.flash.components.*;
import com.pixelservices.flash.components.http.expected.ExpectedBodyField;
import com.pixelservices.flash.components.http.expected.ExpectedBodyFile;
import com.pixelservices.flash.components.http.expected.ExpectedRequestParameter;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.components.http.pool.HandlerPool;
import com.pixelservices.flash.components.http.routing.RouteRegistry;
import com.pixelservices.flash.components.http.routing.models.RequestInfo;
import com.pixelservices.flash.components.http.routing.models.RouteMatch;
import com.pixelservices.flash.exceptions.RequestExceptionHandler;
import com.pixelservices.flash.exceptions.UnmatchedHandlerException;
import com.pixelservices.flash.models.ClientAttachment;
import com.pixelservices.flash.utils.PrettyLogger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Collections;
import java.util.Map;

public class HttpRequestHandler {

    private final FlashServer server;
    private final RouteRegistry routeRegistry;

    public HttpRequestHandler(FlashServer server, RouteRegistry routeRegistry) {
        this.server = server;
        this.routeRegistry = routeRegistry;
    }

    public void handle(ClientAttachment att, RequestInfo reqInfo, String fullRequestData) {
        AsynchronousSocketChannel clientChannel = att.channel;
        RequestHandler handler = null;
        RouteMatch match = null;
        
        try {
            final InetSocketAddress remoteAddress = (InetSocketAddress) att.channel.getRemoteAddress();
            match = routeRegistry.resolveRoute(reqInfo.getMethod(), reqInfo.getPath());

            final Map<String, String> params = match != null ? match.params() : Collections.emptyMap();
            final Request request = new Request(fullRequestData, remoteAddress, params);
            final Response response = new Response();

            if (!server.processMiddleware(reqInfo.getPath(), request, response)) {
                sendResponse(response, clientChannel);
                return;
            }
            
            if (match == null) {
                throw new UnmatchedHandlerException("No handler found for " + reqInfo.getMethod() + " " + reqInfo.getPath());
            }
            
            // Acquire a handler from the pool
            handler = match.entry().getHandlerPool().acquire(request, response);
            
            validateHandlerResources(handler);
            final Object responseBody = handler.handle();
            response.body(convertToResponseBody(responseBody));
            sendResponse(response, clientChannel);
            
        } catch (Exception e) {
            new RequestExceptionHandler(clientChannel, e).handle();
        } finally {
            // Return the handler to the pool if it was acquired
            if (handler != null && match != null) {
                try {
                    // Use a type-safe approach to release the handler back to the pool
                    releaseHandlerToPool(match.entry().getHandlerPool(), handler);
                } catch (Exception e) {
                    PrettyLogger.withEmoji("Error returning handler to pool: " + e.getMessage(), "⚠️");
                }
            }
            
            if (!att.isWebSocket) {
                FlashServer.REQUEST_BUFFER_POOL.release(att.buffer);
                FlashServer.closeSocket(clientChannel);
            }
        }
    }

    /**
     * Safely releases a handler back to its pool with proper type handling.
     * This method uses type erasure to safely cast the handler to the appropriate type.
     */
    @SuppressWarnings("unchecked")
    private <T extends RequestHandler> void releaseHandlerToPool(HandlerPool<T> pool, RequestHandler handler) {
        // This cast is safe because the handler was originally acquired from this pool
        // and the pool only contains handlers of type T
        pool.release((T) handler);
    }

    private void sendResponse(Response response, AsynchronousSocketChannel clientChannel) {
        response.finalizeResponse();
        final ByteBuffer responseBuffer = response.getSerialized();
        clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer buf) {
                if (buf.hasRemaining()) clientChannel.write(buf, buf, this);
            }
            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                PrettyLogger.withEmoji("Error sending response: " + exc.getMessage(), "⚠️");
                FlashServer.closeSocket(clientChannel);
            }
        });
    }

    private void validateHandlerResources(RequestHandler handler) {
        for (ExpectedRequestParameter param : handler.getExpectedRequestParameters().values()) {
            param.getFieldValue();
        }
        for (ExpectedBodyField field : handler.getExpectedBodyFields().values()) {
            field.getFieldValue();
        }
        for (ExpectedBodyFile file : handler.getExpectedBodyFiles().values()) {
            file.getInputStream();
        }
    }

    public Object convertToResponseBody(Object responseBody) {
        if (responseBody == null) return "";
        if (responseBody instanceof byte[] || responseBody instanceof String) return responseBody;
        return responseBody.toString();
    }
}

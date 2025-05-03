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
import java.nio.charset.StandardCharsets;
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
            
            if (isLargeFile(response)) {
                sendLargeFileResponse(response, clientChannel);
            } else {
                sendResponse(response, clientChannel);
            }
            
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
                // Note: We don't close the socket here for large responses
                // It will be closed after the entire response is sent
            }
        }
    }
    
    /**
     * Sends a large response in chunks to avoid memory issues.
     * This is particularly useful for large JS bundles.
     */
    private void sendLargeResponse(Response response, AsynchronousSocketChannel clientChannel) {
        response.finalizeResponse();
        
        ByteBuffer headerBuffer = response.getHeaderBuffer();
        
        clientChannel.write(headerBuffer, new SendContext(response, clientChannel, 0), new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesWritten, SendContext context) {
                if (headerBuffer.hasRemaining()) {
                    // Continue sending headers if not complete
                    clientChannel.write(headerBuffer, context, this);
                } else {
                    // Headers sent, now send body in chunks
                    sendBodyChunk(context);
                }
            }
            
            @Override
            public void failed(Throwable exc, SendContext context) {
                PrettyLogger.withEmoji("Error sending large response headers: " + exc.getMessage(), "⚠️");
                FlashServer.closeSocket(clientChannel);
            }
        });
    }
    
    /**
     * Sends a chunk of the response body.
     */
    private void sendBodyChunk(SendContext context) {
        byte[] body = context.response.getBodyBytes();
        if (body == null || body.length == 0 || context.position >= body.length) {
            // All done, close the connection
            FlashServer.closeSocket(context.channel);
            return;
        }
        
        int chunkSize = Math.min(FlashServer.BUFFER_SIZE, body.length - context.position);
        ByteBuffer chunk = ByteBuffer.allocate(chunkSize);
        chunk.put(body, context.position, chunkSize);
        chunk.flip();
        
        context.position += chunkSize;
        
        context.channel.write(chunk, context, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesWritten, SendContext updatedContext) {
                if (chunk.hasRemaining()) {
                    context.channel.write(chunk, updatedContext, this);
                } else {
                    sendBodyChunk(updatedContext);
                }
            }
            
            @Override
            public void failed(Throwable exc, SendContext updatedContext) {
                PrettyLogger.withEmoji("Error sending large response body chunk: " + exc.getMessage(), "⚠️");
                FlashServer.closeSocket(context.channel);
            }
        });
    }
    
    /**
     * Context class to track state during chunked sending.
     */
    private static class SendContext {
        final Response response;
        final AsynchronousSocketChannel channel;
        int position;
        
        SendContext(Response response, AsynchronousSocketChannel channel, int position) {
            this.response = response;
            this.channel = channel;
            this.position = position;
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
        
        // Check if this is a large JavaScript file that needs special handling
        if (isLargeFile(response)) {
            sendLargeFileResponse(response, clientChannel);
            return;
        }
        
        // Regular response handling
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
    private boolean isLargeFile(Response response) {
        byte[] body = response.getSerializedBody();
        return body.length > 1024 * 1024;
    }

    /**
     * Sends a large file response using chunked transfer to avoid memory issues
     */
    private void sendLargeFileResponse(Response response, AsynchronousSocketChannel clientChannel) {
        response.header("Content-Length", null);
        response.finalizeResponse();
        
        response.header("Transfer-Encoding", "chunked");
        
        byte[] headerBytes = response.getHeaderBytes();
        byte[] bodyBytes = response.getSerializedBody();
        
        // First send the headers
        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);
        
        clientChannel.write(headerBuffer, new ChunkedContext(bodyBytes, clientChannel, 0), 
            new CompletionHandler<Integer, ChunkedContext>() {
                @Override
                public void completed(Integer bytesWritten, ChunkedContext context) {
                    if (headerBuffer.hasRemaining()) {
                        // Continue sending headers
                        clientChannel.write(headerBuffer, context, this);
                    } else {
                        // Headers sent, now send body in chunks
                        sendNextChunk(context);
                    }
                }
                
                @Override
                public void failed(Throwable exc, ChunkedContext context) {
                    PrettyLogger.withEmoji("Error sending large file headers: " + exc.getMessage(), "⚠️");
                    FlashServer.closeSocket(clientChannel);
                }
            });
    }

    /**
     * Sends the next chunk of a large file
     */
    private void sendNextChunk(ChunkedContext context) {
        if (context.position >= context.bodyBytes.length) {
            // Send the final chunk (0-length chunk indicates end of transfer)
            ByteBuffer finalChunk = ByteBuffer.wrap("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            context.channel.write(finalChunk, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer bytesWritten, Void attachment) {
                    if (finalChunk.hasRemaining()) {
                        context.channel.write(finalChunk, null, this);
                    } else {
                        // All done, close the connection
                        FlashServer.closeSocket(context.channel);
                    }
                }
                
                @Override
                public void failed(Throwable exc, Void attachment) {
                    PrettyLogger.withEmoji("Error sending final chunk: " + exc.getMessage(), "⚠️");
                    FlashServer.closeSocket(context.channel);
                }
            });
            return;
        }
        
        // Calculate chunk size (max 64KB per chunk)
        int chunkSize = Math.min(65536, context.bodyBytes.length - context.position);
        
        // Format the chunk according to HTTP chunked encoding (size in hex + CRLF + data + CRLF)
        String chunkHeader = Integer.toHexString(chunkSize) + "\r\n";
        ByteBuffer chunk = ByteBuffer.allocate(chunkHeader.length() + chunkSize + 2);
        chunk.put(chunkHeader.getBytes(StandardCharsets.UTF_8));
        chunk.put(context.bodyBytes, context.position, chunkSize);
        chunk.put("\r\n".getBytes(StandardCharsets.UTF_8));
        chunk.flip();
        
        // Update position for next chunk
        int newPosition = context.position + chunkSize;
        ChunkedContext newContext = new ChunkedContext(context.bodyBytes, context.channel, newPosition);
        
        // Send this chunk
        context.channel.write(chunk, newContext, new CompletionHandler<Integer, ChunkedContext>() {
            @Override
            public void completed(Integer bytesWritten, ChunkedContext updatedContext) {
                if (chunk.hasRemaining()) {
                    // Continue sending this chunk
                    context.channel.write(chunk, updatedContext, this);
                } else {
                    // This chunk is done, send next chunk
                    sendNextChunk(updatedContext);
                }
            }
            
            @Override
            public void failed(Throwable exc, ChunkedContext updatedContext) {
                PrettyLogger.withEmoji("Error sending chunk: " + exc.getMessage(), "⚠️");
                FlashServer.closeSocket(context.channel);
            }
        });
    }

    /**
     * Context class to track state during chunked sending
     */
    private static class ChunkedContext {
        final byte[] bodyBytes;
        final AsynchronousSocketChannel channel;
        final int position;
        
        ChunkedContext(byte[] bodyBytes, AsynchronousSocketChannel channel, int position) {
            this.bodyBytes = bodyBytes;
            this.channel = channel;
            this.position = position;
        }
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

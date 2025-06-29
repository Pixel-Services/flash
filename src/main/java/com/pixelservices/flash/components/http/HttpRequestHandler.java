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
import java.util.Arrays;

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
                //response.finalizeResponse(); // finalizziamo sempre la risposta
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
                response.header("Transfer-Encoding", "chunked");
                response.header("Content-Length", null);
            }

            //response.finalizeResponse();

            if (isLargeFile(response)) {
                sendLargeFileResponse(response, clientChannel);
            } else {
                sendResponse(response, clientChannel);
            }

        } catch (Exception e) {
            new RequestExceptionHandler(clientChannel, e).handle();
        } finally {
            if (handler != null) {
                try {
                    releaseHandlerToPool(match.entry().getHandlerPool(), handler);
                } catch (Exception e) {
                    PrettyLogger.withEmoji("Error returning handler to pool: " + e.getMessage(), "⚠️");
                }
            }

            if (!att.isWebSocket) {
                FlashServer.REQUEST_BUFFER_POOL.release(att.buffer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends RequestHandler> void releaseHandlerToPool(HandlerPool<T> pool, RequestHandler handler) {
        pool.release((T) handler);
    }

    private void sendResponse(Response response, AsynchronousSocketChannel clientChannel) {
        final ByteBuffer responseBuffer = response.getSerialized();
        clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer buf) {
                if (buf.hasRemaining()) {
                    clientChannel.write(buf, buf, this);
                }
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

    private void sendLargeFileResponse(Response response, AsynchronousSocketChannel clientChannel) {
        byte[] headerBytes = response.getHeaderBytes();
        byte[] bodyBytes = response.getSerializedBody();

        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);

        clientChannel.write(headerBuffer, new ChunkedContext(bodyBytes, clientChannel, 0),
                new CompletionHandler<Integer, ChunkedContext>() {
                    @Override
                    public void completed(Integer bytesWritten, ChunkedContext context) {
                        if (headerBuffer.hasRemaining()) {
                            clientChannel.write(headerBuffer, context, this);
                        } else {
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

    private void sendNextChunk(ChunkedContext context) {
        if (context.position >= context.bodyBytes.length) {
            // Send the final chunk (0\r\n\r\n)
            ByteBuffer finalChunk = ByteBuffer.wrap("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            context.channel.write(finalChunk, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer bytesWritten, Void attachment) {
                    if (finalChunk.hasRemaining()) {
                        context.channel.write(finalChunk, null, this);
                    } else {
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

        // Use a smaller chunk size for better memory management
        int chunkSize = Math.min(4096, context.bodyBytes.length - context.position);

        // Format: <chunk-size>\r\n<chunk-data>\r\n
        String chunkHeader = Integer.toHexString(chunkSize) + "\r\n";
        byte[] chunkHeaderBytes = chunkHeader.getBytes(StandardCharsets.UTF_8);
        byte[] chunkFooter = "\r\n".getBytes(StandardCharsets.UTF_8);

        // Use direct buffer for better performance with large files
        ByteBuffer chunk = ByteBuffer.allocateDirect(chunkHeaderBytes.length + chunkSize + chunkFooter.length);
        chunk.put(chunkHeaderBytes);
        
        // Copy data in smaller segments to avoid memory issues
        int remaining = chunkSize;
        int offset = context.position;
        while (remaining > 0) {
            int segmentSize = Math.min(1024, remaining);
            chunk.put(context.bodyBytes, offset, segmentSize);
            remaining -= segmentSize;
            offset += segmentSize;
        }
        
        chunk.put(chunkFooter);
        chunk.flip();

        int newPosition = context.position + chunkSize;
        ChunkedContext newContext = new ChunkedContext(context.bodyBytes, context.channel, newPosition);

        context.channel.write(chunk, newContext, new CompletionHandler<Integer, ChunkedContext>() {
            @Override
            public void completed(Integer bytesWritten, ChunkedContext updatedContext) {
                if (chunk.hasRemaining()) {
                    context.channel.write(chunk, updatedContext, this);
                } else {
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

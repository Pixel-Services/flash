package com.pixelservices.flash.components.websocket;

import com.pixelservices.flash.components.http.routing.models.RequestInfo;
import com.pixelservices.flash.utils.PrettyLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;

public class WebSocketSession {
    private final AsynchronousSocketChannel channel;
    private final RequestInfo requestInfo;
    private String id;
    private final String path;
    private ByteBuffer buffer = ByteBuffer.allocate(1024);

    public WebSocketSession(AsynchronousSocketChannel channel, RequestInfo requestInfo, String path) {
        this.channel = channel;
        this.requestInfo = requestInfo;
        this.path = path;
        this.id = "";
    }

    /**
     * Get the channel for the WebSocket session
     * @return The channel
     */
    public AsynchronousSocketChannel getChannel() {
        return channel;
    }

    /**
     * Get the request info instance (path, headers, method)
     * @return The request info
     */
    public RequestInfo getRequestInfo() {
        return requestInfo;
    }

    public String getPath() {
        return path;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the buffer for the WebSocket session
     * @return The buffer
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public void sendMessage(String message) {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        int messageLength = messageBytes.length;

        ByteBuffer buffer;
        if (messageLength <= 125) {
            buffer = ByteBuffer.allocate(2 + messageLength);
            buffer.put((byte) 0x81);
            buffer.put((byte) messageLength);
        } else if (messageLength <= 65535) {
            buffer = ByteBuffer.allocate(4 + messageLength);
            buffer.put((byte) 0x81);
            buffer.put((byte) 126);
            buffer.putShort((short) messageLength);
        } else {
            buffer = ByteBuffer.allocate(10 + messageLength);
            buffer.put((byte) 0x81);
            buffer.put((byte) 127);
            buffer.putLong(messageLength);
        }

        buffer.put(messageBytes);
        buffer.flip();

        channel.write(buffer, buffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, ByteBuffer buf) {
                if (buf.hasRemaining()) {
                    channel.write(buf, buf, this);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                PrettyLogger.error("Failed to send WebSocket message: " + exc.getMessage());
            }
        });
    }

    /**
     * Closes the WebSocket connection with the specified status code and reason
     *
     * @param statusCode WebSocket close status code (1000 = normal, 1001 = going away, etc.)
     * @param reason Reason for closing the connection (optional)
     */
    public void close(int statusCode, String reason) {
        byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];

        int frameSize = 2 + reasonBytes.length;

        ByteBuffer closeFrame = ByteBuffer.allocate(frameSize + 2);
        closeFrame.put((byte) 0x88);
        closeFrame.put((byte) frameSize);

        closeFrame.putShort((short) statusCode);

        if (reasonBytes.length > 0) {
            closeFrame.put(reasonBytes);
        }

        closeFrame.flip();

        // Send the close frame
        channel.write(closeFrame, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                try {
                    Thread.sleep(100);
                    channel.close();
                    PrettyLogger.log("WebSocket session closed with status: " + statusCode);
                } catch (InterruptedException | IOException e) {
                    PrettyLogger.error("Error while closing WebSocket session: " + e.getMessage());
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                PrettyLogger.error("Failed to send WebSocket close frame: " + exc.getMessage());
                try {
                    channel.close();
                } catch (IOException e) {
                    PrettyLogger.error("Failed to close WebSocket channel: " + e.getMessage());
                }
            }
        });
    }
}
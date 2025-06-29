package com.pixelservices.flash.components.websocket;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.components.OffHeapBufferPool;
import com.pixelservices.flash.components.http.routing.models.RequestInfo;
import com.pixelservices.flash.utils.FlashLogger;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class WebSocketRequestHandler {

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final FlashServer server;
    private final Map<String, WebSocketHandler> webSocketHandlers;
    private final Map<String, WebSocketSession> activeSessions;
    private final OffHeapBufferPool websocketBufferPool;

    public WebSocketRequestHandler(FlashServer server, Map<String, WebSocketHandler> webSocketHandlers, Map<String, WebSocketSession> activeSessions, OffHeapBufferPool websocketBufferPool) {
        this.server = server;
        this.webSocketHandlers = webSocketHandlers;
        this.activeSessions = activeSessions;
        this.websocketBufferPool = websocketBufferPool;
    }

    public void handle(AsynchronousSocketChannel clientChannel, RequestInfo reqInfo) {
        String path = reqInfo.getPath();
        WebSocketHandler handler = webSocketHandlers.get(path);

        if (handler == null) {
            FlashLogger.getLogger().info("No WebSocket handler found for path: " + path);
            try {
                FlashServer.closeSocket(clientChannel);
            } catch (Exception e) {
                FlashLogger.getLogger().info("Error closing WebSocket with no handler: " + e.getMessage());
            }
            return;
        }

        try {
            String webSocketKey = reqInfo.getHeader("Sec-WebSocket-Key");
            if (webSocketKey == null) {
                FlashLogger.getLogger().info("WebSocket handshake failed: missing Sec-WebSocket-Key header");
                FlashServer.closeSocket(clientChannel);
                return;
            }

            String acceptKey = generateWebSocketAcceptKey(webSocketKey);

            // handshake response
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                    "\r\n";

            ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));

            clientChannel.write(responseBuffer, responseBuffer, new CompletionHandler<>() {
                @Override
                public void completed(Integer bytesWritten, ByteBuffer buffer) {
                    if (buffer.hasRemaining()) {
                        clientChannel.write(buffer, buffer, this);
                    } else {
                        // handshake completed can now handle the frames
                        WebSocketSession session = new WebSocketSession(clientChannel, reqInfo, path);
                        String sessionId = UUID.randomUUID().toString();
                        activeSessions.put(sessionId, session);
                        session.setId(sessionId);

                        try {
                            handler.onOpen(session);
                            startWebSocketFrameReader(session, handler, websocketBufferPool);
                        } catch (Exception e) {
                            FlashLogger.getLogger().info("Error in WebSocket open handler: " + e.getMessage());
                            handler.onError(session, e);
                            removeSession(session);
                        }
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer buffer) {
                    FlashLogger.getLogger().info("WebSocket handshake response failed: " + exc.getMessage());
                    FlashServer.closeSocket(clientChannel);
                }
            });
        } catch (Exception e) {
            FlashLogger.getLogger().info("Error during WebSocket handshake: " + e.getMessage());
            FlashServer.closeSocket(clientChannel);
        }
    }

    private String generateWebSocketAcceptKey(String webSocketKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            String concatenated = webSocketKey + WEBSOCKET_GUID;
            byte[] sha1Hash = md.digest(concatenated.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sha1Hash);
        } catch (NoSuchAlgorithmException e) {
            FlashLogger.getLogger().info("Error generating WebSocket accept key: " + e.getMessage());
            throw new RuntimeException("Failed to generate WebSocket accept key", e);
        }
    }

    private void startWebSocketFrameReader(WebSocketSession session, WebSocketHandler handler, OffHeapBufferPool websocketBufferPool) {
        ByteBuffer buffer = websocketBufferPool.acquire();
        session.setBuffer(buffer);
        readWebSocketFrame(session, handler);
    }

    private void readWebSocketFrame(WebSocketSession session, WebSocketHandler handler) {
        ByteBuffer buffer = session.getBuffer();
        buffer.clear();

        session.getChannel().read(buffer, session, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, WebSocketSession session) {
                if (bytesRead > 0) {
                    buffer.flip();

                    try {
                        processWebSocketFrame(buffer, session, handler);
                        readWebSocketFrame(session, handler);
                    } catch (Exception e) {
                        FlashLogger.getLogger().info("Error processing WebSocket frame: " + e.getMessage());
                        handler.onError(session, e);
                        removeSession(session);
                    }
                } else if (bytesRead == -1) {
                    // closed
                    handler.onClose(session, 1000, "Connection closed by client");
                    removeSession(session);
                } else {
                    // keep reading
                    readWebSocketFrame(session, handler);
                }
            }

            @Override
            public void failed(Throwable exc, WebSocketSession session) {
                handler.onError(session, exc);
                removeSession(session);
            }
        });
    }

    private void processWebSocketFrame(ByteBuffer buffer, WebSocketSession session, WebSocketHandler handler) {
        if (buffer.remaining() < 2) {
            return;
        }

        byte byte1 = buffer.get();
        byte byte2 = buffer.get();

        int rsv1 = (byte1 & 0x40) != 0 ? 1 : 0;
        int rsv2 = (byte1 & 0x20) != 0 ? 1 : 0;
        int rsv3 = (byte1 & 0x10) != 0 ? 1 : 0;
        int opcode = byte1 & 0x0F;
        boolean masked = (byte2 & 0x80) != 0;
        int payloadLength = byte2 & 0x7F;

        if (rsv1 != 0 || rsv2 != 0 || rsv3 != 0) {
            FlashLogger.getLogger().info("Invalid WebSocket frame: RSV1, RSV2, and RSV3 must be clear");
            session.close(1002, "Protocol error");
            return;
        }

        long actualPayloadLength;
        if (payloadLength < 126) {
            actualPayloadLength = payloadLength;
        } else if (payloadLength == 126) {
            if (buffer.remaining() < 2) {
                return;
            }
            actualPayloadLength = ((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF);
        } else {
            if (buffer.remaining() < 8) {
                return;
            }
            actualPayloadLength = buffer.getLong();
        }

        if (actualPayloadLength > FlashServer.WEBSOCKET_BUFFER_SIZE - 14) { // Use static constant from FlashServer
            FlashLogger.getLogger().info("WebSocket frame too large: " + actualPayloadLength + " bytes");
            session.close(1009, "Message too big");
            return;
        }

        byte[] maskingKey = new byte[4];
        if (masked) {
            if (buffer.remaining() < 4) {
                return;
            }
            buffer.get(maskingKey);
        }

        if (buffer.remaining() < actualPayloadLength) {
            return;
        }

        byte[] payload = new byte[(int) actualPayloadLength];
        buffer.get(payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskingKey[i % 4];
            }
        }

        switch (opcode) {
            case 0x1: // Text frame
                String message = new String(payload, StandardCharsets.UTF_8);
                handler.onMessage(session, message);
                break;
            case 0x2: // Binary frame
                handler.onMessage(session, payload);
                break;
            case 0x8: // Close frame
                int code = 1000;
                String reason = "";
                if (payload.length >= 2) {
                    code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    if (payload.length > 2) {
                        reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
                    }
                }
                handler.onClose(session, code, reason);
                removeSession(session);
                break;
            case 0x9: // Ping
                // Respond with Pong
                sendPong(session, payload);
                break;
            case 0xA: // Pong
                // Ignore
                break;
            default:
                FlashLogger.getLogger().info("Unsupported WebSocket opcode: " + opcode);
                session.close(1003, "Unsupported data");
        }
    }

    private void sendPong(WebSocketSession session, byte[] payload) {
        ByteBuffer pongBuffer = ByteBuffer.allocate(2 + payload.length);
        pongBuffer.put((byte) 0x8A);
        pongBuffer.put((byte) payload.length); // payload length (as long as its not humongous lol)
        pongBuffer.put(payload);
        pongBuffer.flip();

        session.getChannel().write(pongBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                // sent pong
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                FlashLogger.getLogger().info("Error sending WebSocket pong: " + exc.getMessage());
            }
        });
    }

    private void removeSession(WebSocketSession session) {
        try {
            activeSessions.remove(session.getId());
            FlashServer.WEBSOCKET_BUFFER_POOL.release(session.getBuffer());
            server.closeSocket(session.getChannel());
        } catch (Exception e) {
            FlashLogger.getLogger().info("Error removing WebSocket session: " + e.getMessage());
        }
    }
}

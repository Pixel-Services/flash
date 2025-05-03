package com.pixelervices.flash.tests;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;

public class WebsocketTest {
    @Test
    public void testWebSocketTextMessage() throws URISyntaxException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:8080/ws")) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                send("Hello");
            }

            @Override
            public void onMessage(String message) {
                assertEquals("olleH", message);
                latch.countDown();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };

        client.connect();
        latch.await(5, TimeUnit.SECONDS);
        client.close();
    }

    @Test
    public void testWebSocketBinaryMessage() throws URISyntaxException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:8080/ws")) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                byte[] binaryMessage = {1, 2, 3, 4, 5};
                send(binaryMessage);
            }

            @Override
            public void onMessage(String s) {
                // This method is not used for binary messages
            }

            @Override
            public void onMessage(ByteBuffer message) {
                byte[] receivedBytes = new byte[message.remaining()];
                message.get(receivedBytes);
                byte[] expectedBytes = {5, 4, 3, 2, 1};
                assertArrayEquals(expectedBytes, receivedBytes);
                latch.countDown();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };

        client.connect();
        latch.await(5, TimeUnit.SECONDS);
        client.close();
    }
}

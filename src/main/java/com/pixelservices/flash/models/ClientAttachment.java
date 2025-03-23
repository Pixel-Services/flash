package com.pixelservices.flash.models;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

public class ClientAttachment {
    public final ByteBuffer buffer;
    public final StringBuilder requestData = new StringBuilder();
    public final AsynchronousSocketChannel channel;
    public boolean isWebSocket = false;
    public ClientAttachment(ByteBuffer buffer, AsynchronousSocketChannel channel) {
        this.buffer = buffer;
        this.channel = channel;
    }
}

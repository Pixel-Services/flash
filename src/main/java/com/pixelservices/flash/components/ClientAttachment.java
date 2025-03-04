package com.pixelservices.flash.components;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

public class ClientAttachment {
    public final ByteBuffer buffer;
    final StringBuilder requestData = new StringBuilder();
    public final AsynchronousSocketChannel channel;
    public boolean isWebSocket = false;
    ClientAttachment(ByteBuffer buffer, AsynchronousSocketChannel channel) {
        this.buffer = buffer;
        this.channel = channel;
    }
}

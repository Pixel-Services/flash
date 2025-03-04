package com.pixelservices.flash.components;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OffHeapBufferPool {
    private final int bufferSize;
    private final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    OffHeapBufferPool(int initialSize, int bufferSize) {
        this.bufferSize = bufferSize;
        for (int i = 0; i < initialSize; i++) {
            pool.offer(ByteBuffer.allocateDirect(this.bufferSize));
        }
    }
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            return ByteBuffer.allocateDirect(this.bufferSize);
        }
        buffer.clear();
        return buffer;
    }
    public void release(ByteBuffer buffer) {
        pool.offer(buffer);
    }
}

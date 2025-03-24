package com.pixelservices.flash.components;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class OffHeapBufferPool {
    private final int bufferSize;
    private final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger currentlyInUse = new AtomicInteger(0);
    
    public OffHeapBufferPool(int initialSize, int bufferSize) {
        this.bufferSize = bufferSize;
        for (int i = 0; i < initialSize; i++) {
            pool.offer(ByteBuffer.allocateDirect(this.bufferSize));
            totalCreated.incrementAndGet();
        }
    }
    
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(this.bufferSize);
            totalCreated.incrementAndGet();
        }
        currentlyInUse.incrementAndGet();
        buffer.clear();
        return buffer;
    }
    
    public void release(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.clear();
            pool.offer(buffer);
            currentlyInUse.decrementAndGet();
        }
    }
    
    public int getTotalCreated() {
        return totalCreated.get();
    }
    
    public int getCurrentlyInUse() {
        return currentlyInUse.get();
    }
    
    public int getAvailable() {
        return pool.size();
    }
}

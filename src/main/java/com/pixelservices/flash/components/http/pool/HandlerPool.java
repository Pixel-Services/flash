package com.pixelservices.flash.components.http.pool;

import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.lifecycle.Request;
import com.pixelservices.flash.components.http.lifecycle.Response;
import com.pixelservices.flash.utils.FlashLogger;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

public class HandlerPool<T extends RequestHandler> {
    private static final FlashLogger logger = FlashLogger.getLogger(HandlerPool.class);
    private final Class<T> handlerClass;
    private final ConcurrentLinkedQueue<T> availableHandlers = new ConcurrentLinkedQueue<>();
    private final AtomicInteger totalSize = new AtomicInteger(0);
    private final AtomicInteger activeHandlers = new AtomicInteger(0);
    private final StampedLock resizeLock = new StampedLock();

    private volatile int minSize;
    private volatile int maxSize;
    private volatile int initialSize;
    private final AtomicInteger missCount = new AtomicInteger(0);
    private final AtomicInteger hitCount = new AtomicInteger(0);
    private volatile long lastResizeTime = System.currentTimeMillis();
    private static final long RESIZE_INTERVAL_MS = 10000; // 10 seconds

    public HandlerPool(Class<T> handlerClass, int initialSize, int minSize, int maxSize) {
        this.handlerClass = handlerClass;
        this.initialSize = initialSize;
        this.minSize = minSize;
        this.maxSize = maxSize;

        for (int i = 0; i < initialSize; i++) {
            try {
                T handler = createHandlerInstance();
                availableHandlers.offer(handler);
                totalSize.incrementAndGet();
            } catch (Exception e) {
                logger.info("Failed to create initial handler instance: " + e.getMessage());
            }
        }
    }

    public T acquire(Request request, Response response) {
        T handler = availableHandlers.poll();

        if (handler == null) {
            missCount.incrementAndGet();
            if (totalSize.get() < maxSize) {
                try {
                    handler = createHandlerInstance();
                    totalSize.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create handler instance: " + e.getMessage(), e);
                }
            } else {
                handler = waitForAvailableHandler();
            }
        } else {
            hitCount.incrementAndGet();
        }

        activeHandlers.incrementAndGet();
        handler.setRequestResponse(request, response);
        
        // Check if we should adapt pool size based on hit/miss ratio
        maybeAdaptPoolSize();
        
        return handler;
    }
    
    private void maybeAdaptPoolSize() {
        long now = System.currentTimeMillis();
        if (now - lastResizeTime > RESIZE_INTERVAL_MS) {
            long stamp = resizeLock.tryWriteLock();
            if (stamp != 0) {
                try {
                    int hits = hitCount.get();
                    int misses = missCount.get();
                    int total = hits + misses;
                    
                    if (total > 100) {
                        double missRatio = (double) misses / total;
                        
                        if (missRatio > 0.2 && totalSize.get() < maxSize) {
                            int toAdd = Math.min(5, maxSize - totalSize.get());
                            for (int i = 0; i < toAdd; i++) {
                                try {
                                    T handler = createHandlerInstance();
                                    availableHandlers.offer(handler);
                                    totalSize.incrementAndGet();
                                } catch (Exception e) {
                                    logger.info("Failed to create handler for pool expansion: " + e.getMessage());
                                }
                            }
                            logger.info("Pool expanded: added " + toAdd + " handlers due to high miss ratio (" +
                                    String.format("%.1f%%", missRatio * 100) + ")");

                        }
                        
                        // Reset counters
                        hitCount.set(0);
                        missCount.set(0);
                    }
                    
                    lastResizeTime = now;
                } finally {
                    resizeLock.unlockWrite(stamp);
                }
            }
        }
    }

    public void release(T handler) {
        if (handler == null) return;

        handler.setRequestResponse(null, null);
        availableHandlers.offer(handler);
        activeHandlers.decrementAndGet();

        checkPoolSize();
    }

    private T waitForAvailableHandler() {
        T handler = null;
        int spinCount = 0;

        while (handler == null) {
            handler = availableHandlers.poll();

            if (handler == null) {
                if (spinCount < 1000) {
                    Thread.onSpinWait();
                    spinCount++;
                } else {
                    Thread.yield();
                }
            }
        }

        return handler;
    }

    private void checkPoolSize() {
        long stamp = resizeLock.tryOptimisticRead();
        int currentTotal = totalSize.get();
        int currentActive = activeHandlers.get();

        if (!resizeLock.validate(stamp)) {
            return;
        }

        int idleHandlers = currentTotal - currentActive;
        if (idleHandlers > minSize * 2 && currentTotal > minSize) {
            stamp = resizeLock.writeLock();
            try {
                if (totalSize.get() > minSize && (totalSize.get() - activeHandlers.get()) > minSize * 2) {
                    int toRemove = Math.min(availableHandlers.size(), totalSize.get() - minSize);
                    for (int i = 0; i < toRemove; i++) {
                        availableHandlers.poll();
                        totalSize.decrementAndGet();
                    }
                    logger.info("Pool shrunk: removed " + toRemove + " handlers");
                }
            } finally {
                resizeLock.unlockWrite(stamp);
            }
        }
    }

    private T createHandlerInstance() throws Exception {
        try {
            // First try to find a no-args constructor
            Constructor<T> noArgsConstructor = handlerClass.getDeclaredConstructor();
            return noArgsConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            // If no-args constructor not found, try Request, Response constructor
            Constructor<T> reqResConstructor = handlerClass.getConstructor(Request.class, Response.class);
            return reqResConstructor.newInstance(null, null);
        }
    }

    public Class<T> getHandlerClass() {
        return handlerClass;
    }

    public int getTotalSize() {
        return totalSize.get();
    }

    public int getActiveHandlers() {
        return activeHandlers.get();
    }

    public int getAvailableHandlers() {
        return availableHandlers.size();
    }

    public void updatePoolSizeConstraints(int minSize, int maxSize) {
        long stamp = resizeLock.writeLock();
        try {
            this.minSize = minSize;
            this.maxSize = maxSize;
        } finally {
            resizeLock.unlockWrite(stamp);
        }
    }

    public double getHitRatio() {
        int hits = hitCount.get();
        int total = hits + missCount.get();
        return total > 0 ? (double) hits / total : 1.0;
    }

    public String getHandlerName() {
        try {
            T handler = createHandlerInstance();
            return handler.getHandlerName();
        } catch (Exception e) {
            return handlerClass.getName();
        }
    }
}
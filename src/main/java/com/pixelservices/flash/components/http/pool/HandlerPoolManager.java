package com.pixelservices.flash.components.http.pool;

import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.utils.PrettyLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manages all handler pools in the application, providing dynamic resizing
 * and monitoring capabilities.
 */
public class HandlerPoolManager {
    private final Map<Class<? extends RequestHandler>, HandlerPool<? extends RequestHandler>> pools = new ConcurrentHashMap<>();
    private final ScheduledExecutorService monitoringService;
    
    private final int defaultInitialSize;
    private final int defaultMinSize;
    private final int defaultMaxSize;
    
    /**
     * Creates a new HandlerPoolManager with default pool sizes.
     */
    public HandlerPoolManager(int defaultInitialSize, int defaultMinSize, int defaultMaxSize) {
        this.defaultInitialSize = defaultInitialSize;
        this.defaultMinSize = defaultMinSize;
        this.defaultMaxSize = defaultMaxSize;
        
        // Create a monitoring service with daemon threads
        this.monitoringService = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "HandlerPoolMonitor");
            t.setDaemon(true);
            return t;
        });
        
        // Start monitoring pools every 30 seconds
        monitoringService.scheduleAtFixedRate(this::monitorPools, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Gets or creates a handler pool for the specified handler class.
     * Initializes the pool with 5 instances.
     */
    @SuppressWarnings("unchecked")
    public <T extends RequestHandler> HandlerPool<T> getOrCreatePool(Class<T> handlerClass) {
        return (HandlerPool<T>) pools.computeIfAbsent(handlerClass, k -> {
            return new HandlerPool<>(handlerClass, 5, defaultMinSize, defaultMaxSize);
        });
    }
    
    /**
     * Monitors all pools and adjusts their sizes based on usage patterns.
     */
    private void monitorPools() {
        pools.forEach((handlerClass, pool) -> {
            int totalSize = pool.getTotalSize();
            int activeHandlers = pool.getActiveHandlers();
            
            // If we're consistently using more than 80% of the pool, increase max size
            if (activeHandlers > totalSize * 0.8) {
                int newMaxSize = Math.min(totalSize * 2, 1000); // Cap at 1000 handlers
                pool.updatePoolSizeConstraints(defaultMinSize, newMaxSize);
                PrettyLogger.withEmoji("Increased pool max size for " + handlerClass.getSimpleName() + 
                        " to " + newMaxSize + " (active: " + activeHandlers + ", total: " + totalSize + ")", "📈");
            }
            
            // If we're consistently using less than 20% of the pool, decrease max size
            if (activeHandlers < totalSize * 0.2 && totalSize > defaultMinSize * 2) {
                int newMaxSize = Math.max(totalSize / 2, defaultMinSize * 2);
                pool.updatePoolSizeConstraints(defaultMinSize, newMaxSize);
                PrettyLogger.withEmoji("Decreased pool max size for " + handlerClass.getSimpleName() + 
                        " to " + newMaxSize + " (active: " + activeHandlers + ", total: " + totalSize + ")", "📉");
            }
        });
    }
    
    /**
     * Shuts down the pool manager and all associated resources.
     */
    public void shutdown() {
        monitoringService.shutdown();
    }
}
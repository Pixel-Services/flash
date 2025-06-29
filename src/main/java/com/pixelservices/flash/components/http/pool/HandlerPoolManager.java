package com.pixelservices.flash.components.http.pool;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.components.http.RequestHandler;
import com.pixelservices.flash.components.http.HandlerType;
import com.pixelservices.flash.components.http.HttpMethod;
import com.pixelservices.flash.components.http.routing.models.SimpleHandlerWrapper;
import com.pixelservices.flash.utils.PrettyLogger;
import org.json.JSONArray;
import org.json.JSONObject;

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
    // Change the map to use String keys (handler names)
    private final Map<String, HandlerPool<? extends RequestHandler>> pools = new ConcurrentHashMap<>();
    private final ScheduledExecutorService monitoringService;
    
    private final FlashServer server;
    private final int defaultInitialSize;
    private final int defaultMinSize;
    private final int defaultMaxSize;
    
    /**
     * Creates a new HandlerPoolManager with default pool sizes.
     */
    public HandlerPoolManager(FlashServer server, int defaultInitialSize, int defaultMinSize, int defaultMaxSize) {
        this.server = server;
        this.defaultInitialSize = defaultInitialSize;
        this.defaultMinSize = defaultMinSize;
        this.defaultMaxSize = defaultMaxSize;
        
        this.monitoringService = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "HandlerPoolMonitor");
            t.setDaemon(true);
            return t;
        });
        
        monitoringService.scheduleAtFixedRate(this::monitorPools, 30, 30, TimeUnit.SECONDS);
        
        registerHandlerPoolApiEndpoint();
    }
    
    /**
     * Registers an API endpoint that provides information about all handler pools.
     */
    private void registerHandlerPoolApiEndpoint() {
        server.registerRoute(HttpMethod.GET, "/_flash/devtools/api/handlerpool", (req, res) -> {
            res.type("application/json");
            return getPoolsInfoAsJson();
        }, HandlerType.INTERNAL);
        PrettyLogger.withEmoji("Handler Pool API endpoint registered at /_flash/devtools/api/handlerpool", "🔍");
    }
    
    /**
     * Gets or creates a handler pool for the specified handler class.
     * For SimpleHandlerWrapper, uses the handler ID to create separate pools.
     */
    @SuppressWarnings("unchecked")
    public <T extends RequestHandler> HandlerPool<T> getOrCreatePool(Class<T> handlerClass) {
        // For SimpleHandlerWrapper, we need to get the handler ID from the instance
        if (SimpleHandlerWrapper.class.isAssignableFrom(handlerClass)) {
            try {
                // Create a temporary instance to get its ID
                T instance = handlerClass.getDeclaredConstructor().newInstance();
                String handlerId = ((SimpleHandlerWrapper) instance).getHandlerId();
                return (HandlerPool<T>) pools.computeIfAbsent(handlerId, k -> {
                    HandlerPool<T> pool = new HandlerPool<>(handlerClass, defaultInitialSize, defaultMinSize, defaultMaxSize);
                    return pool;
                });
            } catch (Exception e) {
                // Fallback to class name if we can't get the handler ID
                String handlerName = handlerClass.getSimpleName();
                return (HandlerPool<T>) pools.computeIfAbsent(handlerName, k -> {
                    HandlerPool<T> pool = new HandlerPool<>(handlerClass, defaultInitialSize, defaultMinSize, defaultMaxSize);
                    return pool;
                });
            }
        }
        
        // For other handlers, use the class name as the key
        String handlerName = handlerClass.getSimpleName();
        return (HandlerPool<T>) pools.computeIfAbsent(handlerName, k -> {
            HandlerPool<T> pool = new HandlerPool<>(handlerClass, defaultInitialSize, defaultMinSize, defaultMaxSize);
            return pool;
        });
    }
    
    // Update getPoolsInfoAsJson to work with the new map structure
    private String getPoolsInfoAsJson() {
        JSONObject result = new JSONObject();
        JSONArray poolsArray = new JSONArray();
        
        pools.forEach((handlerName, pool) -> {
            JSONObject poolInfo = new JSONObject();
            poolInfo.put("handlerName", handlerName);
            poolInfo.put("handlerClass", pool.getHandlerClass().getName());
            poolInfo.put("totalSize", pool.getTotalSize());
            poolInfo.put("activeHandlers", pool.getActiveHandlers());
            poolInfo.put("availableHandlers", pool.getAvailableHandlers());
            poolInfo.put("hitRatio", String.format("%.2f", pool.getHitRatio()));
            
            // Add special handling for SimpleHandlerWrapper classes
            if (handlerName.contains("SimpleHandlerWrapper")) {
                poolInfo.put("isSimpleHandler", true);
            }
            
            poolsArray.put(poolInfo);
        });
        
        result.put("pools", poolsArray);
        result.put("defaultInitialSize", defaultInitialSize);
        result.put("defaultMinSize", defaultMinSize);
        result.put("defaultMaxSize", defaultMaxSize);
        result.put("totalPools", pools.size());
        
        return result.toString(2);
    }
    
    /**
     * Monitors all pools and adjusts their sizes based on usage patterns.
     */
    private void monitorPools() {
        pools.forEach((handlerName, pool) -> {
            int totalSize = pool.getTotalSize();
            int activeHandlers = pool.getActiveHandlers();
            
            // If we're consistently using more than 80% of the pool, increase max size
            if (activeHandlers > totalSize * 0.8) {
                int newMaxSize = Math.min(totalSize * 2, 1000);
                pool.updatePoolSizeConstraints(defaultMinSize, newMaxSize);
                PrettyLogger.withEmoji("Increased pool max size for " + handlerName + 
                        " to " + newMaxSize + " (active: " + activeHandlers + ", total: " + totalSize + ")", "📈");
            }
            
            // If we're consistently using less than 20% of the pool, decrease max size
            if (activeHandlers < totalSize * 0.2 && totalSize > defaultMinSize * 2) {
                int newMaxSize = Math.max(totalSize / 2, defaultMinSize * 2);
                pool.updatePoolSizeConstraints(defaultMinSize, newMaxSize);
                PrettyLogger.withEmoji("Decreased pool max size for " + handlerName + 
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
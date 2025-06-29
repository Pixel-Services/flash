package com.pixelservices.flash.utils;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * FlashLogger is a professional, self-contained logging utility for the Flash framework.
 * It provides a clean, structured logging interface without external dependencies.
 */
public class FlashLogger {
    
    // Log levels
    public enum Level {
        TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4);
        
        private final int value;
        
        Level(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    // Configuration
    private static volatile Level currentLevel = Level.INFO;
    private static volatile boolean timestampsEnabled = true;
    private static volatile boolean threadNamesEnabled = true;
    private static volatile PrintStream outputStream = System.out;
    private static volatile PrintStream errorStream = System.err;
    
    // Logger instances cache
    private static final ConcurrentMap<String, FlashLogger> loggers = new ConcurrentHashMap<>();
    
    // Instance fields
    private final String name;
    private final String displayName;
    
    /**
     * Creates a new FlashLogger instance.
     * 
     * @param name the logger name
     */
    private FlashLogger(String name) {
        this.name = name;
        this.displayName = name.isEmpty() ? "Flash" : "Flash." + name;
    }
    
    /**
     * Gets a logger instance for the specified name.
     * 
     * @param name the logger name
     * @return the logger instance
     */
    public static FlashLogger getLogger(String name) {
        return loggers.computeIfAbsent(name, FlashLogger::new);
    }
    
    /**
     * Gets a logger instance for the specified class.
     * 
     * @param clazz the class
     * @return the logger instance
     */
    public static FlashLogger getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }
    
    /**
     * Gets the default logger instance.
     * 
     * @return the default logger instance
     */
    public static FlashLogger getLogger() {
        return getLogger("");
    }
    
    // Configuration methods
    
    /**
     * Sets the current log level.
     * 
     * @param level the new log level
     */
    public static void setLevel(Level level) {
        currentLevel = level;
    }
    
    /**
     * Gets the current log level.
     * 
     * @return the current log level
     */
    public static Level getLevel() {
        return currentLevel;
    }
    
    /**
     * Enables or disables timestamps in log messages.
     * 
     * @param enabled true to enable timestamps, false to disable
     */
    public static void setTimestampsEnabled(boolean enabled) {
        timestampsEnabled = enabled;
    }
    
    /**
     * Enables or disables thread names in log messages.
     * 
     * @param enabled true to enable thread names, false to disable
     */
    public static void setThreadNamesEnabled(boolean enabled) {
        threadNamesEnabled = enabled;
    }
    
    /**
     * Sets the output stream for non-error messages.
     * 
     * @param stream the output stream
     */
    public static void setOutputStream(PrintStream stream) {
        outputStream = stream;
    }
    
    /**
     * Sets the output stream for error messages.
     * 
     * @param stream the error stream
     */
    public static void setErrorStream(PrintStream stream) {
        errorStream = stream;
    }
    
    // Logging methods
    
    /**
     * Logs a TRACE level message.
     * 
     * @param message the message to log
     */
    public void trace(String message) {
        log(Level.TRACE, message);
    }
    
    /**
     * Logs a TRACE level message with a throwable.
     * 
     * @param message the message to log
     * @param throwable the throwable to log
     */
    public void trace(String message, Throwable throwable) {
        log(Level.TRACE, message, throwable);
    }
    
    /**
     * Logs a DEBUG level message.
     * 
     * @param message the message to log
     */
    public void debug(String message) {
        log(Level.DEBUG, message);
    }
    
    /**
     * Logs a DEBUG level message with a throwable.
     * 
     * @param message the message to log
     * @param throwable the throwable to log
     */
    public void debug(String message, Throwable throwable) {
        log(Level.DEBUG, message, throwable);
    }
    
    /**
     * Logs an INFO level message.
     * 
     * @param message the message to log
     */
    public void info(String message) {
        log(Level.INFO, message);
    }
    
    /**
     * Logs an INFO level message with a throwable.
     * 
     * @param message the message to log
     * @param throwable the throwable to log
     */
    public void info(String message, Throwable throwable) {
        log(Level.INFO, message, throwable);
    }
    
    /**
     * Logs a WARN level message.
     * 
     * @param message the message to log
     */
    public void warn(String message) {
        log(Level.WARN, message);
    }
    
    /**
     * Logs a WARN level message with a throwable.
     * 
     * @param message the message to log
     * @param throwable the throwable to log
     */
    public void warn(String message, Throwable throwable) {
        log(Level.WARN, message, throwable);
    }
    
    /**
     * Logs an ERROR level message.
     * 
     * @param message the message to log
     */
    public void error(String message) {
        log(Level.ERROR, message);
    }
    
    /**
     * Logs an ERROR level message with a throwable.
     * 
     * @param message the message to log
     * @param throwable the throwable to log
     */
    public void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }
    
    /**
     * Checks if TRACE level is enabled.
     * 
     * @return true if TRACE level is enabled
     */
    public boolean isTraceEnabled() {
        return currentLevel.getValue() <= Level.TRACE.getValue();
    }
    
    /**
     * Checks if DEBUG level is enabled.
     * 
     * @return true if DEBUG level is enabled
     */
    public boolean isDebugEnabled() {
        return currentLevel.getValue() <= Level.DEBUG.getValue();
    }
    
    /**
     * Checks if INFO level is enabled.
     * 
     * @return true if INFO level is enabled
     */
    public boolean isInfoEnabled() {
        return currentLevel.getValue() <= Level.INFO.getValue();
    }
    
    /**
     * Checks if WARN level is enabled.
     * 
     * @return true if WARN level is enabled
     */
    public boolean isWarnEnabled() {
        return currentLevel.getValue() <= Level.WARN.getValue();
    }
    
    /**
     * Checks if ERROR level is enabled.
     * 
     * @return true if ERROR level is enabled
     */
    public boolean isErrorEnabled() {
        return currentLevel.getValue() <= Level.ERROR.getValue();
    }
    
    // Internal logging method
    
    /**
     * Internal method to log a message at the specified level.
     * 
     * @param level the log level
     * @param message the message to log
     */
    private void log(Level level, String message) {
        log(level, message, null);
    }
    
    /**
     * Internal method to log a message at the specified level with a throwable.
     * 
     * @param level the log level
     * @param message the message to log
     * @param throwable the throwable to log
     */
    private void log(Level level, String message, Throwable throwable) {
        if (level.getValue() < currentLevel.getValue()) {
            return;
        }
        
        String formattedMessage = formatMessage(level, message);
        PrintStream stream = (level == Level.ERROR) ? errorStream : outputStream;
        
        stream.println(formattedMessage);
        
        if (throwable != null) {
            throwable.printStackTrace(stream);
        }
    }
    
    /**
     * Formats a log message with timestamp, level, logger name, and thread name.
     * 
     * @param level the log level
     * @param message the message
     * @return the formatted message
     */
    private String formatMessage(Level level, String message) {
        StringBuilder formatted = new StringBuilder();
        
        // Add timestamp if enabled
        if (timestampsEnabled) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            formatted.append("[").append(timestamp).append("] ");
        }
        
        // Add level
        formatted.append("[").append(level.name()).append("] ");
        
        // Add logger name
        formatted.append("[").append(displayName).append("] ");
        
        // Add thread name if enabled
        if (threadNamesEnabled) {
            String threadName = Thread.currentThread().getName();
            formatted.append("[").append(threadName).append("] ");
        }
        
        // Add the actual message
        formatted.append(message);
        
        return formatted.toString();
    }
}



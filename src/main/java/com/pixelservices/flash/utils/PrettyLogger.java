package com.pixelservices.flash.utils;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrettyLogger is a self-contained utility class for logging messages with support for hex colors and emojis.
 * This implementation doesn't interfere with the host application's logging system.
 */
public class PrettyLogger {

    private static final PrintStream OUT = System.out;
    private static final PrintStream ERR = System.err;
    
    // ANSI escape codes for colored output
    private static final String RESET = "\u001B[0m";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6}|reset)(.*?)(?=&#[A-Fa-f0-9]{6}|&#reset|$)");
    
    // Log level enum
    public enum Level {
        INFO, WARN, ERROR
    }
    
    // Configuration
    private static boolean colorsEnabled = true;
    private static boolean timestampsEnabled = true;
    private static String prefix = "Flash";

    /**
     * Enables or disables ANSI color output.
     * 
     * @param enabled true to enable colors, false to disable
     */
    public static void setColorsEnabled(boolean enabled) {
        colorsEnabled = enabled;
    }

    /**
     * Enables or disables timestamp output.
     * 
     * @param enabled true to enable timestamps, false to disable
     */
    public static void setTimestampsEnabled(boolean enabled) {
        timestampsEnabled = enabled;
    }

    /**
     * Sets the logger prefix.
     * 
     * @param newPrefix the new prefix to use
     */
    public static void setPrefix(String newPrefix) {
        prefix = newPrefix;
    }

    /**
     * Logs a message with INFO level.
     *
     * @param message the message to log, with optional {@code "&#RRGGBB"} for colors
     */
    public static void log(String message) {
        log(message, Level.INFO);
    }

    /**
     * Logs a message with WARN level.
     *
     * @param message the message to log, with optional {@code "&#RRGGBB"} for colors
     */
    public static void warn(String message) {
        log(message, Level.WARN);
    }

    /**
     * Logs a message with ERROR level.
     *
     * @param message the message to log, with optional {@code "&#RRGGBB"} for colors
     */
    public static void error(String message) {
        log(message, Level.ERROR);
    }

    /**
     * Logs a message with an emoji and INFO level.
     *
     * @param message the message to log
     * @param emoji the emoji to prepend to the message
     */
    public static void withEmoji(String message, String emoji) {
        withEmoji(message, emoji, Level.INFO);
    }

    /**
     * Logs a message with an emoji and specified level.
     *
     * @param message the message to log
     * @param emoji the emoji to prepend to the message
     * @param level the log level
     */
    public static void withEmoji(String message, String emoji, Level level) {
        String fullMessage = emoji + " " + message;
        log(fullMessage, level);
    }

    /**
     * Internal logging method.
     *
     * @param message the message to log
     * @param level the log level
     */
    private static void log(String message, Level level) {
        String formattedMessage = formatMessage(message, level);
        
        PrintStream stream = (level == Level.ERROR) ? ERR : OUT;
        stream.println(formattedMessage);
    }

    /**
     * Formats a message with timestamp, prefix, level, and colors.
     *
     * @param message the original message
     * @param level the log level
     * @return the formatted message
     */
    private static String formatMessage(String message, Level level) {
        StringBuilder formatted = new StringBuilder();
        
        // Add timestamp if enabled
        if (timestampsEnabled) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            formatted.append("[").append(timestamp).append("] ");
        }
        
        // Add prefix
        formatted.append(prefix).append(" ");
        
        // Add thread name
        String threadName = Thread.currentThread().getName();
        formatted.append("[").append(threadName).append("] ");
        
        // Add level
        String levelStr = String.format("%-5s", level.name());
        formatted.append(levelStr).append(": ");
        
        // Add the actual message with colors if enabled
        if (colorsEnabled) {
            formatted.append(applyHexColors(message));
        } else {
            // Strip colors if disabled
            formatted.append(stripColors(message));
        }
        
        return formatted.toString();
    }

    /**
     * Applies hex color codes to the message using ANSI escape codes.
     *
     * @param message the original message
     * @return the message with ANSI color codes applied
     */
    static String applyHexColors(String message) {
        Matcher matcher = HEX_COLOR_PATTERN.matcher(message);
        StringBuilder formattedMessage = new StringBuilder(RESET); // Always start with RESET

        int lastEnd = 0;
        while (matcher.find()) {
            formattedMessage.append(message, lastEnd, matcher.start());

            String colorOrReset = matcher.group(1); // "A4D8D8" or "reset"
            if ("reset".equalsIgnoreCase(colorOrReset)) {
                formattedMessage.append(RESET); // Insert ANSI reset code
            } else {
                formattedMessage.append(hexToAnsi(colorOrReset)); // Apply the color
            }

            formattedMessage.append(matcher.group(2)); // Append the associated text
            lastEnd = matcher.end();
        }
        formattedMessage.append(message.substring(lastEnd)); // Append any remaining text
        return formattedMessage.toString();
    }

    /**
     * Strips color codes from a message.
     *
     * @param message the message with color codes
     * @return the message without color codes
     */
    private static String stripColors(String message) {
        return message.replaceAll("&#[A-Fa-f0-9]{6}|&#reset", "");
    }

    /**
     * Converts a hex color code to an ANSI escape code.
     *
     * @param hexColor the hex color code (e.g., "FF00FF")
     * @return the ANSI escape code for the color
     */
    private static String hexToAnsi(String hexColor) {
        int red = Integer.parseInt(hexColor.substring(0, 2), 16);
        int green = Integer.parseInt(hexColor.substring(2, 4), 16);
        int blue = Integer.parseInt(hexColor.substring(4, 6), 16);

        return String.format("\u001B[38;2;%d;%d;%dm", red, green, blue);
    }
}



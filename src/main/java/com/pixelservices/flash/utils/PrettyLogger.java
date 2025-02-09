package com.pixelservices.utils;

import com.pixelservices.logger.Logger;
import com.pixelservices.logger.LoggerFactory;
import com.pixelservices.logger.events.LogEvent;
import com.pixelservices.logger.formatter.LogFormatter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrettyLogger is a utility class for logging messages with support for hex colors and emojis.
 */
public class PrettyLogger {

    static {
        LoggerFactory.setFormatter(new CustomLogFormatter());
    }

    public static Logger logger = LoggerFactory.getLogger("Flash");

    // ANSI escape codes for colored output
    private static final String RESET = "\u001B[0m";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6}|reset)(.*?)(?=&#[A-Fa-f0-9]{6}|&#reset|$)");

    /**
     * Logs a message with optional hex color codes and emojis.
     *
     * @param message the message to log, with optional {@code "<#RRGGBB>"} for colors and emojis
     */
    public static void log(String message) {
        String coloredMessage = applyHexColors(message+"&#reset");
        logger.info(coloredMessage);
    }

    /**
     * Logs a message with an emoji prefix.
     *
     * @param message the message to log
     * @param emoji   the emoji to prepend
     */
    public static void logWithEmoji(String message, String emoji) {
        log(emoji + "  " + message);
    }

    /**
     * Applies hex color codes to the message using ANSI escape codes.
     *
     * @param message the original message
     * @return the message with ANSI color codes applied
     */
    private static String applyHexColors(String message) {
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

class CustomLogFormatter implements LogFormatter {
    @Override
    public String format(LogEvent logEvent) {
        long timestamp = logEvent.getTimestamp();
        String level = logEvent.getLevel().name();
        String message = logEvent.getMessage();
        String time = String.format("[%02d:%02d:%02d]", (timestamp / 3600) % 24, (timestamp / 60) % 60, timestamp % 60);
        String loggerName = logEvent.getLoggerName();
        return String.format("%s %s : %s", time, level, message);
    }
}


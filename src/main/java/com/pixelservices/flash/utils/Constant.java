package com.pixelservices.flash.utils;

import java.util.Map;

public class Constant {
    public static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry(".html", "text/html"),
            Map.entry(".htm", "text/html"),
            Map.entry(".js", "application/javascript"),
            Map.entry(".mjs", "application/javascript"),
            Map.entry(".ts", "application/typescript"),
            Map.entry(".css", "text/css"),
            Map.entry(".json", "application/json"),
            Map.entry(".xml", "application/xml"),

            // Images
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".bmp", "image/bmp"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".svg", "image/svg+xml"),

            // Fonts
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"),
            Map.entry(".ttf", "font/ttf"),
            Map.entry(".otf", "font/otf"),
            Map.entry(".eot", "application/vnd.ms-fontobject"),

            // Audio/Video
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".webm", "video/webm"),
            Map.entry(".ogg", "audio/ogg"),
            Map.entry(".mp3", "audio/mpeg"),
            Map.entry(".wav", "audio/wav"),

            // Others
            Map.entry(".wasm", "application/wasm"),
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".zip", "application/zip"),
            Map.entry(".gz", "application/gzip"),
            Map.entry(".br", "application/brotli")
    );
}

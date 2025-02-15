package com.pixelservices.flash.components.fileserver;

import java.nio.file.Path;

public class DynamicFileServerConfiguration {
    private final boolean enableFileWatcher;
    private final Path destinationPath;
    private final String dynamicEntrypoint;

    public DynamicFileServerConfiguration(boolean enableFileWatcher, Path destinationPath, String dynamicEntrypoint) {
        this.enableFileWatcher = enableFileWatcher;
        this.destinationPath = destinationPath;
        this.dynamicEntrypoint = dynamicEntrypoint;
    }

    public boolean isEnableFileWatcher() {
        return enableFileWatcher;
    }

    public Path getDestinationPath() {
        return destinationPath;
    }

    public String getDynamicEntrypoint() {
        return dynamicEntrypoint;
    }
}


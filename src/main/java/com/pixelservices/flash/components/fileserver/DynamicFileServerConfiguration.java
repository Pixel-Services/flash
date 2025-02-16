package com.pixelservices.flash.components.fileserver;

import java.nio.file.Path;

public class DynamicFileServerConfiguration {
    private final boolean enableFileWatcher;
    private final Path destinationPath;
    private final String dynamicEntrypoint;
    private final SourceType sourceType;

    public DynamicFileServerConfiguration(boolean enableFileWatcher, Path destinationPath, String dynamicEntrypoint, SourceType sourceType) {
        this.enableFileWatcher = enableFileWatcher;
        this.destinationPath = destinationPath;
        this.dynamicEntrypoint = dynamicEntrypoint;
        this.sourceType = sourceType;
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

    public SourceType getSourceType() {
        return sourceType;
    }
}

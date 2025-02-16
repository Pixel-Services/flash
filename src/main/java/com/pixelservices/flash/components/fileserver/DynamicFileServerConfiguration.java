package com.pixelservices.flash.components.fileserver;

public class DynamicFileServerConfiguration {
    private final boolean enableFileWatcher;
    private final String destinationPath;
    private final String dynamicEntrypoint;
    private final SourceType sourceType;

    public DynamicFileServerConfiguration(boolean enableFileWatcher, String destinationPath, String dynamicEntrypoint, SourceType sourceType) {
        this.enableFileWatcher = enableFileWatcher;
        this.destinationPath = destinationPath;
        this.dynamicEntrypoint = dynamicEntrypoint;
        this.sourceType = sourceType;
    }

    public boolean isEnableFileWatcher() {
        return enableFileWatcher;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public String getDynamicEntrypoint() {
        return dynamicEntrypoint;
    }

    public SourceType getSourceType() {
        return sourceType;
    }
}

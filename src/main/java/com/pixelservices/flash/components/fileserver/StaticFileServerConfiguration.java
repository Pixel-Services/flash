package com.pixelservices.flash.components.fileserver;

public class StaticFileServerConfiguration {
    private final boolean enableFileWatcher;
    private final boolean enableIndexRedirect;
    private final String destinationPath;
    private final SourceType sourceType;

    public StaticFileServerConfiguration(boolean enableFileWatcher, boolean enableIndexRedirect, String destinationPath, SourceType sourceType) {
        this.enableFileWatcher = enableFileWatcher;
        this.enableIndexRedirect = enableIndexRedirect;
        this.destinationPath = destinationPath;
        this.sourceType = sourceType;
    }

    public boolean isEnableFileWatcher() {
        return enableFileWatcher;
    }

    public boolean isEnableIndexRedirect() {
        return enableIndexRedirect;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

}
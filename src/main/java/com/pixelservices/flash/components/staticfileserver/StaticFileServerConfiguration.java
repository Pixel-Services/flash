package com.pixelservices.flash.components.staticfileserver;

import java.nio.file.Path;

public class StaticFileServerConfiguration {
    private final boolean enableFileWatcher;
    private final boolean enableIndexRedirect;
    private final Path destinationPath;
    private final SourceType sourceType;

    public StaticFileServerConfiguration(boolean enableFileWatcher, boolean enableIndexRedirect, Path destinationPath, SourceType sourceType) {
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

    public Path getDestinationPath() {
        return destinationPath;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

}

package com.pixelservices.flash.plugins;

import com.pixelservices.flashapi.FlashPlugin;
import com.pixelservices.flashapi.PluginWrapper;

class InternalPluginWrapper extends PluginWrapper {
    private final PluginManager parent;

    public InternalPluginWrapper(Class<? extends FlashPlugin> flashPlugin, FlashPlugin[] dependencies, PluginManager parent) {
        super(flashPlugin, dependencies);
        this.parent = parent;
    }

    @Override
    public void enable() {
        super.enable();
    }

    @Override
    public void disable() {
        super.disable();
    }

    @Override
    public Status getStatus() {
        return super.getStatus();
    }
}

package com.pixelervices.flash.plugins;

import com.pixelservices.flashapi.FlashPlugin;

public class TestPluginDependency extends FlashPlugin {
    @Override
    public void onEnable() {
        getLogger().info("TestPluginDependency enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("TestPluginDependency disabled");
    }
}

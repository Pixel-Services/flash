package com.pixelervices.flash.plugins;

import com.pixelservices.flashapi.FlashPlugin;
import com.pixelservices.flashapi.annotations.DependsOn;

@DependsOn(TestPluginDependency.class)
public class TestPlugin extends FlashPlugin {
    @Override
    void onEnable() {

    }

    @Override
    void onDisable() {

    }
}

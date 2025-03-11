package com.pixelervices.flash.plugins;

import com.pixelservices.flashapi.FlashPlugin;

public class TestPluginDependency extends FlashPlugin {
    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    public String testMethod(){
        return "TEST";
    }
}

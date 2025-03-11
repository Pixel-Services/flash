package com.pixelervices.flash.plugins;

import com.pixelservices.flashapi.FlashPlugin;
import com.pixelservices.flashapi.annotations.DependsOn;
import com.pixelservices.logger.Logger;
import com.pixelservices.logger.LoggerFactory;


@DependsOn(TestPluginDependency.class)
public class TestPlugin extends FlashPlugin {
    private final Logger logger = LoggerFactory.getLogger("TestLogger");

    @Override
    public void onEnable() {
        logger.info("TestPlugin enabled");

        TestPluginDependency dependency = (TestPluginDependency) getDependency(TestPluginDependency.class);

        if (dependency.testMethod().equals("TEST")) {
            logger.info("Dependency method works");
        }
    }

    @Override
    public void onDisable() {

    }

    public void testMethod(){
        logger.info("Test method called");
    }
}

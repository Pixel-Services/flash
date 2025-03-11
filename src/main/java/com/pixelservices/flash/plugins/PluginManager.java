package com.pixelservices.flash.plugins;

import com.pixelservices.flash.components.FlashServer;
import com.pixelservices.flash.utils.PrettyLogger;
import com.pixelservices.flashapi.FlashPlugin;
import com.pixelservices.flashapi.PluginWrapper;
import com.pixelservices.flashapi.annotations.DependsOn;
import com.pixelservices.flashapi.annotations.PreServer;
import com.pixelservices.flashapi.exception.PluginLoadException;
import com.pixelservices.logger.Logger;
import com.pixelservices.logger.LoggerFactory;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginManager {
    private final List<Class<? extends FlashPlugin>> registeredPlugins = new ArrayList<>();
    private final List<InternalPluginWrapper> loadedPlugins = new ArrayList<>();
    private final FlashServer flashServer;
    private boolean enabled = false;
    private PluginLoadingStrategy loadingStrategy;

    public PluginManager(FlashServer flashServer) {
        this.flashServer = flashServer;
        loadingStrategy = PluginLoadingStrategy.EAGER;
    }

    public void setLoadingStrategy(PluginLoadingStrategy loadingStrategy) {
        this.loadingStrategy = loadingStrategy;
    }

    public void registerPlugin(Class<? extends FlashPlugin> plugin) {
        if (enabled) {
            PrettyLogger.warn("Plugin " + plugin.getSimpleName() + " was registered after the starting the server. This may cause issues.");
        }
        if (registeredPlugins.contains(plugin)) {
            PrettyLogger.warn("Plugin " + plugin.getSimpleName() + " was registered multiple times.");
            return;
        }
        for (Class<? extends FlashPlugin> dependency : getDependencies(plugin)) {
            if (!registeredPlugins.contains(dependency)) {
                registerPlugin(dependency);
            }
        }
        registeredPlugins.add(plugin);
        if (loadingStrategy == PluginLoadingStrategy.EAGER) {
            loadPlugin(plugin);
        }
        PrettyLogger.withEmoji("Registered plugin: " + plugin.getSimpleName(), "🔌");
        if (plugin.isAnnotationPresent(PreServer.class)) {
            PrettyLogger.warn("Plugin " + plugin.getSimpleName() + " is configured to be enabled before the server starts. This feature is experimental and may cause issues.");
            enablePlugin((InternalPluginWrapper) getPluginWrapper(plugin));
        }
    }

    public FlashPlugin getPlugin(Class<? extends FlashPlugin> pluginClass) {
        InternalPluginWrapper wrapper = (InternalPluginWrapper) getPluginWrapper(pluginClass);
        if (wrapper.getStatus() != PluginWrapper.Status.ENABLED) {
            enablePlugin(wrapper);
        }
        return wrapper.getPlugin();
    }

    public void enablePlugins() {
        enabled = true;
        for (InternalPluginWrapper wrapper : loadedPlugins) {
            enablePlugin(wrapper);
        }
    }

    public void disablePlugins() {
        enabled = false;
        for (InternalPluginWrapper wrapper : loadedPlugins) {
            wrapper.disable();
        }
    }

    private PluginWrapper getPluginWrapper(Class<? extends FlashPlugin> plugin) {
        if (!registeredPlugins.contains(plugin)) {
            PrettyLogger.warn("Plugin " + plugin.getSimpleName() + " was retrieved without being registered first. Flash handled this automatically, but it is recommended to register plugins before using them.");
            registerPlugin(plugin);
        }
        for (PluginWrapper wrapper : loadedPlugins) {
            if (wrapper.getPlugin().getClass().equals(plugin)) {
                return wrapper;
            }
        }
        return loadPlugin(plugin);
    }

    private void enablePlugin(InternalPluginWrapper plugin) {
        if (plugin.getStatus() == PluginWrapper.Status.ENABLED) {
            return;
        }
        for (Class<? extends FlashPlugin> dependency : getDependencies(plugin.getPlugin().getClass())) {
            InternalPluginWrapper dependencyWrapper = (InternalPluginWrapper) getPluginWrapper(dependency);
            if (dependencyWrapper.getStatus() != PluginWrapper.Status.ENABLED) {
                enablePlugin((dependencyWrapper));
            }
        }
        try {
            plugin.enable();
        } catch (Throwable e) {
            PrettyLogger.error("Failed to enable plugin: " + plugin.getPlugin().getClass().getSimpleName(), e);
        }
    }

    private PluginWrapper loadPlugin(Class<? extends FlashPlugin> plugin) {
        try {
            List<FlashPlugin> dependencies = new ArrayList<>();
            for (Class<? extends FlashPlugin> dependency : getDependencies(plugin)) {
                dependencies.add(getPluginWrapper(dependency).getPlugin());
            }
            InternalPluginWrapper wrapper = new InternalPluginWrapper(plugin, dependencies.toArray(new FlashPlugin[0]), this);
            loadedPlugins.add(wrapper);
            return wrapper;
        } catch (PluginLoadException e) {
            PrettyLogger.error("Failed to load plugin " + plugin.getSimpleName(), e);
            return null;
        }
    }

    private List<Class<? extends FlashPlugin>> getDependencies(Class<? extends FlashPlugin> pluginClass) {
        List<Class<? extends FlashPlugin>> dependencies = new ArrayList<>();
        if (pluginClass.isAnnotationPresent(DependsOn.class)) {
            DependsOn dependsOn = pluginClass.getAnnotation(DependsOn.class);
            Collections.addAll(dependencies, dependsOn.value());
        }
        return dependencies;
    }
}
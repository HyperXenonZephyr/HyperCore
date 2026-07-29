package dev.hypercore.plugin;

public interface HyperPlugin {
    default void onLoad(PluginContext context) {
    }

    default void onEnable(PluginContext context) {
    }

    default void onDisable(PluginContext context) {
    }
}

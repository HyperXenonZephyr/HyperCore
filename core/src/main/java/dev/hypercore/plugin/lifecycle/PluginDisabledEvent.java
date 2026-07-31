package dev.hypercore.plugin.lifecycle;

import dev.hypercore.plugin.PluginDescriptor;
import dev.hypercore.plugin.PluginEventBus;

import java.util.Objects;

/**
 * Internal HyperCore event fired when a plugin is about to be disabled.
 */
public record PluginDisabledEvent(PluginDescriptor descriptor) implements PluginEventBus.PluginEvent {

    public PluginDisabledEvent {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}

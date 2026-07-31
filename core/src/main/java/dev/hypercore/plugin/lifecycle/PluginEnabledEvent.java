package dev.hypercore.plugin.lifecycle;

import dev.hypercore.plugin.PluginDescriptor;
import dev.hypercore.plugin.PluginEventBus;

import java.util.Objects;

/**
 * Internal HyperCore event fired after a plugin has been enabled.
 */
public record PluginEnabledEvent(PluginDescriptor descriptor) implements PluginEventBus.PluginEvent {

    public PluginEnabledEvent {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}

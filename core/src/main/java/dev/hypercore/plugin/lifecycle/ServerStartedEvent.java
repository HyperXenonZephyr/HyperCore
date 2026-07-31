package dev.hypercore.plugin.lifecycle;

import dev.hypercore.plugin.PluginEventBus;

/**
 * Internal HyperCore event fired once the dedicated server has finished
 * starting up.
 */
public record ServerStartedEvent() implements PluginEventBus.PluginEvent {
}

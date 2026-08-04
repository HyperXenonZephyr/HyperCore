package dev.hypercore.world;

import dev.hypercore.bridge.world.WorldDelta;

/**
 * Receives world mutations produced by the {@link RegionExecutionService} so
 * they can be mirrored to the remote host through the bridge.
 *
 * <p>In bridge mode the loader adapter installs a sink that batches deltas and
 * ships them to the orchestrator; in standalone mode no sink is installed and
 * this hook is inert.
 */
@FunctionalInterface
public interface DeltaSink {

    /**
     * Records a successful world mutation.
     */
    void publish(WorldDelta delta);

    /**
     * A no-op sink used as the default when bridging is disabled.
     */
    DeltaSink NOOP = delta -> {
    };
}

package dev.hypercore.bridge.world;

import dev.hypercore.orchestrator.HyperCoreRole;

import java.util.List;

/**
 * Applies ordered world deltas received from the remote host.
 *
 * <p>Implementations live in the loader adapters and translate deltas into
 * native Minecraft operations on the server thread. Only deltas whose source
 * differs from the applying host's role reach the applier; locally-originated
 * deltas were already applied at production time.
 */
public interface WorldDeltaApplier {

    /**
     * Applies the given deltas to the local world.
     *
     * @param source the host that produced the deltas
     * @param deltas the ordered deltas to apply
     */
    void apply(HyperCoreRole source, List<WorldDelta> deltas);
}

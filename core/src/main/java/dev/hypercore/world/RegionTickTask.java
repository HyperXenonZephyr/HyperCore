package dev.hypercore.world;

import dev.hypercore.region.RegionKey;

/**
 * Work executed once per region during a parallel world tick.
 *
 * <p>The task is invoked while holding the region's write lock, so it may read
 * and mutate world state safely. Cross-region mutations must still be sent as
 * messages through {@link RegionExecutionService}.
 */
@FunctionalInterface
public interface RegionTickTask {

    /**
     * Runs one tick for the given region.
     *
     * @param execution the region execution service
     * @param region the region being ticked
     * @param tickId the monotonically increasing tick identifier
     */
    void tick(RegionExecutionService execution, RegionKey region, long tickId);
}

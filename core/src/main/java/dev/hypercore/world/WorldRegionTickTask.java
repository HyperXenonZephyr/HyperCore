package dev.hypercore.world;

import dev.hypercore.region.RegionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default per-region tick task used by the Forge and Fabric adapters.
 *
 * <p>The current implementation is intentionally minimal: it demonstrates that
 * regions are ticked under their write locks across worker lanes. Future work
 * can add entity AI, scheduled block updates, and random ticks here.
 */
public final class WorldRegionTickTask implements RegionTickTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldRegionTickTask.class);

    @Override
    public void tick(RegionExecutionService execution, RegionKey region, long tickId) {
        // Placeholder for region-scoped tick work. The task runs under the
        // region write lock, so it is safe to read or mutate the region.
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Ticking region {} for tick {}", region, tickId);
        }
    }
}

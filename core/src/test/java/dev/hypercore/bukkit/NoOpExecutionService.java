package dev.hypercore.bukkit;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.NoOpWorldAccessFactory;
import dev.hypercore.world.RegionExecutionService;

/**
 * Test factory that creates a {@link RegionExecutionService} backed by a no-op
 * world factory and a single-threaded region coordinator. Safe for unit tests
 * that only exercise the Bukkit adapter surface.
 */
final class NoOpExecutionService {
    private NoOpExecutionService() {
    }

    static RegionExecutionService create() {
        HyperCoreExecutor executor = HyperCoreExecutor.create(1, 16);
        try {
            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, 1);
            return new RegionExecutionService(new NoOpWorldAccessFactory(), coordinator, new PluginEventBus());
        } finally {
            executor.close();
        }
    }
}

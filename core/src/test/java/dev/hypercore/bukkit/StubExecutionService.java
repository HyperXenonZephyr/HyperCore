package dev.hypercore.bukkit;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.StubWorldAccessFactory;

/**
 * Test factory that creates a {@link RegionExecutionService} backed by a
 * {@link StubWorldAccessFactory}. Tests call {@link #stub(String)} to configure
 * return values and inspect recorded mutations on the underlying
 * {@link dev.hypercore.world.StubWorldAccess}.
 */
final class StubExecutionService {
    private StubExecutionService() {
    }

    /**
     * Creates an execution service with stub world access for the given worlds.
     *
     * @return a holder containing the service and the factory
     */
    static Holder create(String... worldNames) {
        HyperCoreExecutor executor = HyperCoreExecutor.create(1, 16);
        try {
            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, 1);
            StubWorldAccessFactory factory = new StubWorldAccessFactory(worldNames);
            RegionExecutionService service = new RegionExecutionService(factory, coordinator, new PluginEventBus());
            return new Holder(service, factory, executor);
        } catch (RuntimeException error) {
            executor.close();
            throw error;
        }
    }

    /**
     * Holds the created service, factory, and executor so tests can access the
     * stub and the executor is properly closed afterwards.
     */
    static final class Holder implements AutoCloseable {
        final RegionExecutionService service;
        final StubWorldAccessFactory factory;
        private final HyperCoreExecutor executor;

        Holder(RegionExecutionService service, StubWorldAccessFactory factory, HyperCoreExecutor executor) {
            this.service = service;
            this.factory = factory;
            this.executor = executor;
        }

        dev.hypercore.world.StubWorldAccess stub(String worldName) {
            return factory.stub(worldName);
        }

        @Override
        public void close() {
            executor.close();
        }
    }
}

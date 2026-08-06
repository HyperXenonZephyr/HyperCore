package dev.hypercore.orchestrator;

import dev.hypercore.config.HyperCoreSettings;
import dev.hypercore.orchestrator.process.ProcessLauncher;
import dev.hypercore.orchestrator.process.ServerProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Owns the two host processes of an orchestrated deployment.
 *
 * <p>Launches the Forge and Fabric hosts as child JVMs, waits for their
 * readiness markers, and monitors their health. Listeners can be attached to
 * observe readiness and death; the IPC bridge server registers itself through
 * these callbacks once both hosts are up.
 *
 * <p>Aside from per-host process state the orchestrator is stateless: sequence
 * numbers and world deltas live in the bridge components, so a crashed
 * orchestrator can be restarted and the hosts resynchronized from a saved
 * world snapshot.
 */
public final class OrchestratorRuntime implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorRuntime.class);

    private final HyperCoreSettings.OrchestratorSettings settings;
    private final Path workingDirectory;
    private final HostLauncher hostLauncher;
    private final Map<HyperCoreRole, ServerProcess> hosts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<HyperCoreRole>> readyListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<HyperCoreRole>> deathListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile Thread shutdownHook;
    private volatile ScheduledExecutorService healthMonitor;

    /**
     * @param settings orchestrator settings describing both hosts
     * @param workingDirectory directory the host sub-directories are created in
     */
    public OrchestratorRuntime(HyperCoreSettings.OrchestratorSettings settings, Path workingDirectory) {
        this(settings, workingDirectory, null);
    }

    /**
     * Package-private constructor that accepts a custom host launcher so tests
     * can substitute mock host processes.
     */
    OrchestratorRuntime(HyperCoreSettings.OrchestratorSettings settings, Path workingDirectory, HostLauncher hostLauncher) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
            .toAbsolutePath()
            .normalize();
        this.hostLauncher = hostLauncher != null ? hostLauncher : this::launchHostProcess;
    }

    /**
     * Launches both hosts and starts health monitoring.
     *
     * @throws IllegalStateException if this runtime has already been started
     */
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Orchestrator runtime is already started");
        }
        launchHost(HyperCoreRole.FORGE_HOST);
        launchHost(HyperCoreRole.FABRIC_HOST);
        shutdownHook = new Thread(this::stop, "hypercore-orchestrator-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        healthMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hypercore-orchestrator-health");
            thread.setDaemon(true);
            return thread;
        });
        healthMonitor.scheduleWithFixedDelay(
            this::monitorHealth,
            settings.bridgeTickMillis(),
            settings.bridgeTickMillis(),
            TimeUnit.MILLISECONDS
        );
        LOGGER.info(
            "Orchestrator started; Forge host on port {}, Fabric host on port {}",
            settings.orchestratorPort(),
            settings.orchestratorPort() + 1
        );
    }

    /**
     * Registers a listener invoked when a host emits its readiness marker.
     */
    public void onHostReady(Consumer<HyperCoreRole> listener) {
        readyListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Registers a listener invoked when a host process dies unexpectedly.
     */
    public void onHostDeath(Consumer<HyperCoreRole> listener) {
        deathListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Returns the live status of every host role.
     */
    public Map<HyperCoreRole, HostStatus> status() {
        Map<HyperCoreRole, HostStatus> result = new ConcurrentHashMap<>();
        for (HyperCoreRole role : List.of(HyperCoreRole.FORGE_HOST, HyperCoreRole.FABRIC_HOST)) {
            ServerProcess process = hosts.get(role);
            result.put(role, process == null
                ? new HostStatus(false, false, -1, List.of(), "never launched")
                : new HostStatus(
                    process.isAlive(),
                    process.isReady(),
                    process.exitValue(),
                    process.recentOutput(4),
                    process.isReady() ? "ready" : (process.isAlive() ? "starting" : "exited")
                ));
        }
        return Map.copyOf(result);
    }

    /**
     * Stops both hosts and shuts down health monitoring. Safe to call multiple
     * times.
     */
    public synchronized void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down; the hook is running now.
            }
            shutdownHook = null;
        }
        ScheduledExecutorService monitor = healthMonitor;
        healthMonitor = null;
        if (monitor != null) {
            monitor.shutdownNow();
            try {
                if (!monitor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Health monitor did not terminate promptly");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
        // Stop the processes but keep their handles so status() can report
        // exit codes after termination.
        for (ServerProcess process : hosts.values()) {
            process.stop(Duration.ofSeconds(10));
        }
        started.set(false);
        LOGGER.info("Orchestrator stopped");
    }

    @Override
    public void close() {
        stop();
    }

    private void launchHost(HyperCoreRole role) {
        try {
            ServerProcess process = hostLauncher.launch(role);
            hosts.put(role, process);
            Thread watcher = new Thread(
                () -> watchHost(role, process),
                "host-watcher-" + role.displayName()
            );
            watcher.setDaemon(true);
            watcher.start();
        } catch (java.io.IOException error) {
            LOGGER.error("Failed to launch {} host", role.displayName(), error);
            hosts.remove(role);
        }
    }

    private ServerProcess launchHostProcess(HyperCoreRole role) throws java.io.IOException {
        Path hostDirectory = ProcessLauncher.workingDirectory(settings, role, workingDirectory);
        List<String> command = ProcessLauncher.command(settings, role, hostDirectory);
        return ServerProcess.launch(role.displayName(), command, settings.readyMarker());
    }

    /**
     * Launches a host process. Defaults to {@link ProcessLauncher} plus
     * {@link ServerProcess}; tests substitute a mock implementation.
     */
    @FunctionalInterface
    public interface HostLauncher {
        ServerProcess launch(HyperCoreRole role) throws java.io.IOException;
    }

    private void watchHost(HyperCoreRole role, ServerProcess process) {
        boolean ready = process.awaitReady(Duration.ofMillis(settings.hostStartupTimeoutMillis()));
        if (ready) {
            for (Consumer<HyperCoreRole> listener : readyListeners) {
                try {
                    listener.accept(role);
                } catch (RuntimeException error) {
                    LOGGER.error("Host-ready listener failed for {}", role.displayName(), error);
                }
            }
        }
        // Keep watching until the process exits so death listeners fire.
        while (process.isAlive()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (!stopped.get()) {
            LOGGER.warn("Host {} exited unexpectedly with code {}", role.displayName(), process.exitValue());
            for (Consumer<HyperCoreRole> listener : deathListeners) {
                try {
                    listener.accept(role);
                } catch (RuntimeException error) {
                    LOGGER.error("Host-death listener failed for {}", role.displayName(), error);
                }
            }
        }
    }

    private void monitorHealth() {
        for (HyperCoreRole role : List.of(HyperCoreRole.FORGE_HOST, HyperCoreRole.FABRIC_HOST)) {
            ServerProcess process = hosts.get(role);
            if (process == null || process.isAlive()) {
                continue;
            }
            if (!stopped.get()) {
                LOGGER.warn(
                    "Health monitor detected {} host is down (code {}); last output: {}",
                    role.displayName(),
                    process.exitValue(),
                    process.recentOutput(6)
                );
            }
        }
    }

    /**
     * Snapshot of a host process used by status commands and diagnostics.
     */
    public record HostStatus(
        boolean alive,
        boolean ready,
        int exitCode,
        List<String> recentOutput,
        String state
    ) {
    }
}

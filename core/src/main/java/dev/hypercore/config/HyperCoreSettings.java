package dev.hypercore.config;

import java.util.List;
import java.util.Objects;

/**
 * Loader-agnostic runtime settings shared by the Forge and Fabric adapters.
 *
 * <p>Each loader builds this record from its own configuration mechanism
 * (ForgeConfigSpec on Forge, a properties file on Fabric) and passes it to
 * {@code HyperCoreRuntime.start}. Keeping the record in core lets every
 * loader-agnostic component depend on a single settings type without pulling in
 * a loader-specific config API.
 *
 * <p>The {@link OrchestratorSettings} component is only meaningful when the
 * process runs in {@code ORCHESTRATOR} role; host processes receive their own
 * configuration through system properties injected by the orchestrator.
 */
public record HyperCoreSettings(
    int workerThreads,
    int queueCapacity,
    int tickSampleWindow,
    boolean probeGpu,
    boolean enableGpu,
    int gpuMinimumBatchSize,
    String cpuBackend,
    OrchestratorSettings orchestrator
) {
    public static final List<String> CPU_BACKENDS = List.of("auto", "scalar", "vector");

    /**
     * Backward-compatible constructor that applies default orchestrator settings.
     * Used by existing loader config adapters and unit tests.
     */
    public HyperCoreSettings(
        int workerThreads,
        int queueCapacity,
        int tickSampleWindow,
        boolean probeGpu,
        boolean enableGpu,
        int gpuMinimumBatchSize,
        String cpuBackend
    ) {
        this(
            workerThreads,
            queueCapacity,
            tickSampleWindow,
            probeGpu,
            enableGpu,
            gpuMinimumBatchSize,
            cpuBackend,
            OrchestratorSettings.defaults()
        );
    }

    public HyperCoreSettings {
        if (workerThreads < 0) {
            throw new IllegalArgumentException("workerThreads cannot be negative");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity cannot be negative");
        }
        if (tickSampleWindow < 1) {
            throw new IllegalArgumentException("tickSampleWindow must be positive");
        }
        if (gpuMinimumBatchSize < 1) {
            throw new IllegalArgumentException("gpuMinimumBatchSize must be positive");
        }
        if (cpuBackend == null || !CPU_BACKENDS.contains(cpuBackend)) {
            throw new IllegalArgumentException("cpuBackend must be auto, scalar, or vector");
        }
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator settings cannot be null");
        }
    }

    public int resolveWorkerThreads(int logicalProcessors) {
        if (logicalProcessors < 1) {
            throw new IllegalArgumentException("logicalProcessors must be positive");
        }
        return workerThreads == 0 ? Math.max(1, logicalProcessors - 1) : workerThreads;
    }

    public int resolveQueueCapacity(int resolvedWorkerThreads) {
        if (resolvedWorkerThreads < 1) {
            throw new IllegalArgumentException("resolvedWorkerThreads must be positive");
        }
        return queueCapacity == 0 ? Math.max(256, resolvedWorkerThreads * 64) : queueCapacity;
    }

    /**
     * Configuration for the dual-server orchestrated deployment. Only consumed
     * by the process running in {@code ORCHESTRATOR} role; host roles derive
     * their own connection parameters from system properties injected by the
     * orchestrator's {@link dev.hypercore.orchestrator.process.ProcessLauncher}.
     */
    public record OrchestratorSettings(
        String javaExecutable,
        List<String> forgeJvmArgs,
        List<String> fabricJvmArgs,
        int forgeMemoryMb,
        int fabricMemoryMb,
        int orchestratorPort,
        long hostStartupTimeoutMillis,
        long bridgeTickMillis,
        String forgeWorkingDirectory,
        String fabricWorkingDirectory,
        String forgeMainClass,
        String fabricMainClass,
        List<String> forgeLaunchArgs,
        List<String> fabricLaunchArgs,
        String readyMarker
    ) {
        public static final int DEFAULT_ORCHESTRATOR_PORT = 34177;

        public static OrchestratorSettings defaults() {
            return new OrchestratorSettings(
                "",
                List.of(),
                List.of(),
                0,
                0,
                DEFAULT_ORCHESTRATOR_PORT,
                120_000,
                50,
                "forge-host",
                "fabric-host",
                "net.minecraftforge.server.ServerMain",
                "net.fabricmc.loader.impl.launch.knot.KnotServer",
                List.of("--nogui"),
                List.of("nogui"),
                "[hypercore] BRIDGE READY"
            );
        }

        public OrchestratorSettings {
            javaExecutable = Objects.requireNonNullElse(javaExecutable, "");
            forgeJvmArgs = List.copyOf(forgeJvmArgs == null ? List.of() : forgeJvmArgs);
            fabricJvmArgs = List.copyOf(fabricJvmArgs == null ? List.of() : fabricJvmArgs);
            forgeWorkingDirectory = Objects.requireNonNullElse(forgeWorkingDirectory, "forge-host");
            fabricWorkingDirectory = Objects.requireNonNullElse(fabricWorkingDirectory, "fabric-host");
            forgeMainClass = Objects.requireNonNull(forgeMainClass, "forgeMainClass");
            fabricMainClass = Objects.requireNonNull(fabricMainClass, "fabricMainClass");
            forgeLaunchArgs = List.copyOf(forgeLaunchArgs == null ? List.of() : forgeLaunchArgs);
            fabricLaunchArgs = List.copyOf(fabricLaunchArgs == null ? List.of() : fabricLaunchArgs);
            readyMarker = Objects.requireNonNull(readyMarker, "readyMarker");
            if (forgeMemoryMb < 0) {
                throw new IllegalArgumentException("forgeMemoryMb cannot be negative");
            }
            if (fabricMemoryMb < 0) {
                throw new IllegalArgumentException("fabricMemoryMb cannot be negative");
            }
            if (orchestratorPort < 1 || orchestratorPort > 65_535) {
                throw new IllegalArgumentException("orchestratorPort must be within the valid port range");
            }
            if (hostStartupTimeoutMillis < 1) {
                throw new IllegalArgumentException("hostStartupTimeoutMillis must be positive");
            }
            if (bridgeTickMillis < 1) {
                throw new IllegalArgumentException("bridgeTickMillis must be positive");
            }
        }

        /**
         * Returns the TCP port a host of the given role connects to. The Forge
         * host uses the base port and the Fabric host the next port.
         */
        public int hostPort(dev.hypercore.orchestrator.HyperCoreRole role) {
            return orchestratorPort + (role == dev.hypercore.orchestrator.HyperCoreRole.FABRIC_HOST ? 1 : 0);
        }
    }
}

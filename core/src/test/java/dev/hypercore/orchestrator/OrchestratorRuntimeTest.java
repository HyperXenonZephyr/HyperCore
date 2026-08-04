package dev.hypercore.orchestrator;

import dev.hypercore.config.HyperCoreSettings;
import dev.hypercore.orchestrator.process.ProcessLauncher;
import dev.hypercore.orchestrator.process.ServerProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the orchestrator launches two host processes, detects readiness
 * from stdout markers, and shuts them down cleanly. Hosts are substituted with
 * {@link dev.hypercore.orchestrator.process.MockHost} so no Minecraft runtime
 * is required.
 */
class OrchestratorRuntimeTest {
    private static final String MARKER = "MOCK READY";

    @TempDir
    Path temporaryDirectory;

    private HyperCoreSettings.OrchestratorSettings settings() {
        String mockHost = dev.hypercore.orchestrator.process.MockHost.class.getName();
        return new HyperCoreSettings.OrchestratorSettings(
            "",
            List.of(),
            List.of(),
            0,
            0,
            34_777,
            20_000,
            25,
            "forge-host",
            "fabric-host",
            mockHost,
            mockHost,
            List.of(MARKER),
            List.of(MARKER),
            MARKER
        );
    }

    private OrchestratorRuntime newRuntime() {
        HyperCoreSettings.OrchestratorSettings settings = settings();
        return new OrchestratorRuntime(settings, temporaryDirectory, role -> launchMockHost(settings, role, temporaryDirectory));
    }

    private ServerProcess launchMockHost(
        HyperCoreSettings.OrchestratorSettings settings,
        HyperCoreRole role,
        Path root
    ) throws java.io.IOException {
        Path hostDirectory = ProcessLauncher.workingDirectory(settings, role, root);
        List<String> command = new ArrayList<>(ProcessLauncher.command(settings, role, hostDirectory));
        // The mock host lives on the current JVM's classpath, which
        // ProcessLauncher intentionally does not propagate in production.
        command.add(1, "-cp");
        command.add(2, System.getProperty("java.class.path"));
        return ServerProcess.launch(role.displayName(), command, settings.readyMarker());
    }

    private void awaitBothReady(OrchestratorRuntime runtime) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            Map<HyperCoreRole, OrchestratorRuntime.HostStatus> status = runtime.status();
            boolean bothReady = status.get(HyperCoreRole.FORGE_HOST).ready()
                && status.get(HyperCoreRole.FABRIC_HOST).ready();
            if (bothReady) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Hosts did not become ready: " + runtime.status());
    }

    @Test
    void launchesTwoHostsAndDetectsReadinessFromStdoutMarkers() throws Exception {
        OrchestratorRuntime runtime = newRuntime();
        runtime.start();
        try {
            awaitBothReady(runtime);

            Map<HyperCoreRole, OrchestratorRuntime.HostStatus> status = runtime.status();
            assertEquals(2, status.size());
            for (HyperCoreRole role : List.of(HyperCoreRole.FORGE_HOST, HyperCoreRole.FABRIC_HOST)) {
                OrchestratorRuntime.HostStatus host = status.get(role);
                assertTrue(host.alive(), role + " should be alive");
                assertTrue(host.ready(), role + " should report ready");
                assertEquals("ready", host.state());
            }
        } finally {
            runtime.stop();
        }
    }

    @Test
    void notifiesReadyListenersForEachHost() throws Exception {
        OrchestratorRuntime runtime = newRuntime();
        CountDownLatch latch = new CountDownLatch(2);
        runtime.onHostReady(role -> latch.countDown());
        runtime.start();
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "ready listeners should fire for both hosts");
        } finally {
            runtime.stop();
        }
    }

    @Test
    void shutsDownHostsCleanly() throws Exception {
        OrchestratorRuntime runtime = newRuntime();
        runtime.start();
        awaitBothReady(runtime);
        runtime.stop();
        // Both hosts must be terminated and the health monitor stopped.
        Map<HyperCoreRole, OrchestratorRuntime.HostStatus> status = runtime.status();
        for (HyperCoreRole role : List.of(HyperCoreRole.FORGE_HOST, HyperCoreRole.FABRIC_HOST)) {
            assertFalse(status.get(role).alive(), role + " should be terminated");
            assertTrue(status.get(role).exitCode() >= 0, role + " should have an exit code");
        }
        // A second stop must be a no-op.
        runtime.stop();
    }

    @Test
    void reportsExitedHostAsNotReady() throws Exception {
        HyperCoreSettings.OrchestratorSettings settings = settings();
        // Launch a host that exits immediately without ever printing the marker.
        OrchestratorRuntime runtime = new OrchestratorRuntime(settings, temporaryDirectory, role -> {
            List<String> command = new ArrayList<>();
            command.add(ProcessLauncher.resolveJavaExecutable(""));
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(dev.hypercore.orchestrator.process.ExitingHost.class.getName());
            return ServerProcess.launch(role.displayName(), command, settings.readyMarker());
        });
        runtime.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (System.nanoTime() < deadline) {
                Map<HyperCoreRole, OrchestratorRuntime.HostStatus> status = runtime.status();
                if (!status.get(HyperCoreRole.FORGE_HOST).alive()) {
                    assertFalse(status.get(HyperCoreRole.FORGE_HOST).ready());
                    return;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("Host should have exited");
        } finally {
            runtime.stop();
        }
    }
}

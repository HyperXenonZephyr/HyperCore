package dev.hypercore.orchestrator.process;

import dev.hypercore.config.HyperCoreSettings;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the command line for each host role.
 *
 * <p>Production hosts are started through the Forge/Fabric launcher scripts
 * ({@code run.bat} / {@code run.sh}) that the server operator installs in the
 * host directory. HyperCore wraps those scripts with a tiny generated launcher
 * that injects the role, orchestrator address, and IPC port through the
 * {@code JAVA_TOOL_OPTIONS} environment variable, so no manual JVM flag editing
 * is required. If no launcher script is present the orchestrator falls back to
 * a direct JVM command for testing and embedded mock hosts.
 */
public final class ProcessLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessLauncher.class);
    private static final String WRAPPER_SCRIPT = "hypercore-run-wrapper";

    private ProcessLauncher() {
    }

    /**
     * Builds the full command line used to launch a host of the given role.
     *
     * @param settings orchestrator settings describing both hosts
     * @param role the host role to launch
     * @param orchestratedRoot the directory that contains the host sub-directories
     * @return the command line, including the java executable and main class
     */
    public static List<String> command(
        HyperCoreSettings.OrchestratorSettings settings,
        HyperCoreRole role,
        Path orchestratedRoot
    ) {
        if (role != HyperCoreRole.FORGE_HOST && role != HyperCoreRole.FABRIC_HOST) {
            throw new IllegalArgumentException("Only host roles can be launched: " + role);
        }
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(orchestratedRoot, "orchestratedRoot");

        Path workingDirectory = workingDirectory(settings, role, orchestratedRoot);
        Path launcherScript = launcherScript(workingDirectory);
        if (launcherScript != null) {
            Path wrapper = writeWrapperScript(workingDirectory, launcherScript, role, settings);
            return List.of(wrapper.toAbsolutePath().toString());
        }

        LOGGER.warn(
            "No run.bat/run.sh found in {}. Falling back to a direct JVM launch; "
                + "this is intended for tests and mock hosts, not production servers.",
            workingDirectory
        );
        return directJvmCommand(settings, role);
    }

    /**
     * Returns the working directory a host of the given role runs in, creating
     * it if necessary.
     */
    public static Path workingDirectory(
        HyperCoreSettings.OrchestratorSettings settings,
        HyperCoreRole role,
        Path orchestratedRoot
    ) {
        String relative = role == HyperCoreRole.FORGE_HOST
            ? settings.forgeWorkingDirectory()
            : settings.fabricWorkingDirectory();
        Path directory = orchestratedRoot.resolve(relative).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Failed to create host working directory " + directory, error);
        }
        return directory;
    }

    /**
     * Resolves the java executable, defaulting to the JVM that runs the
     * orchestrator.
     */
    public static String resolveJavaExecutable(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe"
            : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().toString();
    }

    /**
     * Returns the orchestrator host name hosts connect to. Localhost is the
     * default; the value can be overridden through a system property for
     * remote orchestration setups.
     */
    public static String orchestratorHost() {
        return System.getProperty("hypercore.orchestrator.host", "127.0.0.1");
    }

    public static final String ROLE_PROPERTY = "hypercore.role";
    public static final String ORCHESTRATOR_HOST_PROPERTY = "hypercore.orchestrator.host";
    public static final String ORCHESTRATOR_PORT_PROPERTY = "hypercore.orchestrator.port";

    private static Path launcherScript(Path workingDirectory) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path bat = workingDirectory.resolve("run.bat");
        Path sh = workingDirectory.resolve("run.sh");
        if (windows && Files.isRegularFile(bat)) {
            return bat;
        }
        if (Files.isRegularFile(sh)) {
            // On Windows this relies on the operator using WSL or Git Bash.
            return sh;
        }
        return null;
    }

    private static Path writeWrapperScript(
        Path workingDirectory,
        Path launcherScript,
        HyperCoreRole role,
        HyperCoreSettings.OrchestratorSettings settings
    ) {
        boolean windows = launcherScript.getFileName().toString().endsWith(".bat");
        String extension = windows ? ".bat" : ".sh";
        Path wrapper = workingDirectory.resolve(WRAPPER_SCRIPT + extension).toAbsolutePath().normalize();
        String toolOptions = javaToolOptions(role, settings);
        String launcherName = launcherScript.getFileName().toString();

        List<String> lines = new ArrayList<>();
        if (windows) {
            lines.add("@echo off");
            lines.add("setlocal");
            lines.add("set JAVA_TOOL_OPTIONS=" + toolOptions);
            lines.add("call " + launcherName);
            lines.add("endlocal");
        } else {
            lines.add("#!/bin/sh");
            lines.add("export JAVA_TOOL_OPTIONS='" + toolOptions.replace("'", "'\\''") + "'");
            lines.add("exec ./" + launcherName);
        }
        try {
            Files.writeString(wrapper, String.join(windows ? "\r\n" : "\n", lines) + (windows ? "\r\n" : "\n"), StandardCharsets.UTF_8);
            if (!windows) {
                wrapper.toFile().setExecutable(true, false);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write host wrapper script " + wrapper, error);
        }
        return wrapper;
    }

    private static String javaToolOptions(HyperCoreRole role, HyperCoreSettings.OrchestratorSettings settings) {
        StringBuilder options = new StringBuilder();
        options.append("-D").append(ROLE_PROPERTY).append("=").append(role.name()).append(" ");
        options.append("-D").append(ORCHESTRATOR_HOST_PROPERTY).append("=").append(orchestratorHost()).append(" ");
        options.append("-D").append(ORCHESTRATOR_PORT_PROPERTY).append("=").append(settings.hostPort(role)).append(" ");
        options.append("-Dhypercore.bridge.tickMillis=").append(settings.bridgeTickMillis()).append(" ");
        options.append("-Dhypercore.orchestrator.readyMarker=").append(settings.readyMarker()).append(" ");
        options.append("--add-modules jdk.incubator.vector");
        int memoryMb = role == HyperCoreRole.FORGE_HOST ? settings.forgeMemoryMb() : settings.fabricMemoryMb();
        if (memoryMb > 0) {
            options.append(" -Xmx").append(memoryMb).append("M");
        }
        List<String> extraArgs = role == HyperCoreRole.FORGE_HOST ? settings.forgeJvmArgs() : settings.fabricJvmArgs();
        for (String arg : extraArgs) {
            options.append(" ").append(arg);
        }
        return options.toString();
    }

    private static List<String> directJvmCommand(HyperCoreSettings.OrchestratorSettings settings, HyperCoreRole role) {
        boolean forge = role == HyperCoreRole.FORGE_HOST;
        List<String> command = new ArrayList<>();
        command.add(resolveJavaExecutable(settings.javaExecutable()));
        command.add("-D" + ROLE_PROPERTY + "=" + role.name());
        command.add("-D" + ORCHESTRATOR_HOST_PROPERTY + "=" + orchestratorHost());
        command.add("-D" + ORCHESTRATOR_PORT_PROPERTY + "=" + settings.hostPort(role));
        command.add("-Dhypercore.bridge.tickMillis=" + settings.bridgeTickMillis());
        command.add("-Dhypercore.orchestrator.readyMarker=" + settings.readyMarker());
        int memoryMb = forge ? settings.forgeMemoryMb() : settings.fabricMemoryMb();
        if (memoryMb > 0) {
            command.add("-Xmx" + memoryMb + "M");
        }
        command.add("--add-modules");
        command.add("jdk.incubator.vector");
        command.addAll(forge ? settings.forgeJvmArgs() : settings.fabricJvmArgs());
        command.add(forge ? settings.forgeMainClass() : settings.fabricMainClass());
        command.addAll(forge ? settings.forgeLaunchArgs() : settings.fabricLaunchArgs());
        return List.copyOf(command);
    }
}

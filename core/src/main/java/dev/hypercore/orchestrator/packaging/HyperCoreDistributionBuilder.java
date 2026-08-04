package dev.hypercore.orchestrator.packaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the unified HyperCore distribution directory.
 *
 * <p>Produces:
 * <pre>
 * hypercore-&lt;version&gt;/
 *   README.txt
 *   start-orchestrator.bat / start-orchestrator.sh
 *   orchestrator/
 *     hypercore-core.jar
 *   forge-host/
 *     mods/hypercore-forge.jar
 *     plugins/hypercore-gametest-bukkit-plugin.jar
 *   fabric-host/
 *     mods/hypercore-fabric.jar
 *     plugins/hypercore-gametest-bukkit-plugin.jar
 * </pre>
 *
 * <p>The host directories are templates: operators drop their own Forge/Fabric
 * dedicated server into them. The orchestrator launches each host with the
 * server main class listed in the README and the vector module flag supplied
 * automatically.
 *
 * <p>Invoked by the root {@code assembleDistribution} Gradle task through its
 * {@code main} method; the arguments are file paths in a fixed order.
 */
public final class HyperCoreDistributionBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(HyperCoreDistributionBuilder.class);

    private HyperCoreDistributionBuilder() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                "Usage: HyperCoreDistributionBuilder <outputDir> <orchestratorJar> <forgeModJar> <fabricModJar> <gametestPluginJar>"
            );
        }
        build(
            Path.of(args[0]),
            Path.of(args[1]),
            Path.of(args[2]),
            Path.of(args[3]),
            Path.of(args[4])
        );
    }

    /**
     * Assembles the distribution directory.
     */
    public static void build(
        Path outputDirectory,
        Path orchestratorJar,
        Path forgeModJar,
        Path fabricModJar,
        Path gametestPluginJar
    ) throws IOException {
        Files.createDirectories(outputDirectory);
        Path orchestratorDir = outputDirectory.resolve("orchestrator");
        Path forgeDir = outputDirectory.resolve("forge-host");
        Path fabricDir = outputDirectory.resolve("fabric-host");
        Files.createDirectories(orchestratorDir);
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("plugins"));
        Files.createDirectories(fabricDir.resolve("mods"));
        Files.createDirectories(fabricDir.resolve("plugins"));

        copy(orchestratorJar, orchestratorDir.resolve("hypercore-core.jar"));
        copy(forgeModJar, forgeDir.resolve("mods").resolve("hypercore-forge.jar"));
        copy(fabricModJar, fabricDir.resolve("mods").resolve("hypercore-fabric.jar"));
        copy(gametestPluginJar, forgeDir.resolve("plugins").resolve("hypercore-gametest-bukkit-plugin.jar"));
        copy(gametestPluginJar, fabricDir.resolve("plugins").resolve("hypercore-gametest-bukkit-plugin.jar"));

        writeLaunchScripts(outputDirectory, "hypercore-core.jar");
        writeReadme(outputDirectory);
        LOGGER.info("Distribution assembled at {}", outputDirectory.toAbsolutePath());
    }

    private static void copy(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeLaunchScripts(Path root, String orchestratorJarName) throws IOException {
        List<String> bat = new ArrayList<>();
        bat.add("@echo off");
        bat.add("setlocal");
        bat.add("set DIR=%~dp0");
        bat.add("set JAVA=java");
        bat.add("if defined HYPERCORE_JAVA_HOME set JAVA=%HYPERCORE_JAVA_HOME%\\bin\\java");
        bat.add("\"%JAVA%\" -Dhypercore.role=ORCHESTRATOR");
        bat.add("  -Dhypercore.orchestrator.basePort=%HYPERCORE_ORCHESTRATOR_PORT%");
        bat.add("  -Dhypercore.bridge.tickMillis=%HYPERCORE_BRIDGE_TICK_MILLIS%");
        bat.add("  -Dhypercore.orchestrator.root=%DIR%");
        bat.add("  --add-modules jdk.incubator.vector");
        bat.add("  -cp \"%DIR%orchestrator\\" + orchestratorJarName + "\"");
        bat.add("  dev.hypercore.orchestrator.launcher.OrchestratorMain %*");
        bat.add("endlocal");
        Files.writeString(root.resolve("start-orchestrator.bat"), String.join("\r\n", bat) + "\r\n", StandardCharsets.UTF_8);

        List<String> sh = new ArrayList<>();
        sh.add("#!/bin/sh");
        sh.add("DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"");
        sh.add("JAVA=${HYPERCORE_JAVA_HOME:+$HYPERCORE_JAVA_HOME/bin/java}");
        sh.add("JAVA=${JAVA:-java}");
        sh.add("exec \"$JAVA\" -Dhypercore.role=ORCHESTRATOR \\");
        sh.add("  -Dhypercore.orchestrator.basePort=\"${HYPERCORE_ORCHESTRATOR_PORT:-34177}\" \\");
        sh.add("  -Dhypercore.bridge.tickMillis=\"${HYPERCORE_BRIDGE_TICK_MILLIS:-50}\" \\");
        sh.add("  -Dhypercore.orchestrator.root=\"$DIR\" \\");
        sh.add("  --add-modules jdk.incubator.vector \\");
        sh.add("  -cp \"$DIR/orchestrator/" + orchestratorJarName + "\" \\");
        sh.add("  dev.hypercore.orchestrator.launcher.OrchestratorMain \"$@\"");
        Files.writeString(root.resolve("start-orchestrator.sh"), String.join("\n", sh) + "\n", StandardCharsets.UTF_8);
    }

    private static void writeReadme(Path root) throws IOException {
        String readme = """
            HyperCore Orchestrated Distribution
            ===================================

            This directory contains the HyperCore Orchestrator and host templates
            for running one logical world where both Forge and Fabric mods are
            active simultaneously.

            Layout
            ------
              orchestrator/  the orchestrator JAR (hypercore-core.jar)
              forge-host/    template Forge dedicated server directory
                             - mods/hypercore-forge.jar
                             - plugins/hypercore-gametest-bukkit-plugin.jar
              fabric-host/   template Fabric dedicated server directory
                             - mods/hypercore-fabric.jar
                             - plugins/hypercore-gametest-bukkit-plugin.jar

            Setup
            -----
            1. Install a vanilla Forge %s dedicated server into forge-host/ and run
               the Forge installer so that forge-host/run.bat (Windows) or
               forge-host/run.sh (POSIX) exists, along with libraries/, eula.txt and
               server.properties.
            2. Install a vanilla Fabric %s dedicated server into fabric-host/ so that
               fabric-host/run.bat (Windows) or fabric-host/run.sh (POSIX) exists,
               along with fabric-loader jars, libraries/, eula.txt and server.properties.
            3. Keep the two hosts on the same world seed and dimension list so the
               logical worlds coincide.

            Launch
            ------
            Windows:  start-orchestrator.bat
            POSIX:    ./start-orchestrator.sh

            Optional environment variables:
              HYPERCORE_JAVA_HOME          JVM to run the orchestrator
              HYPERCORE_ORCHESTRATOR_PORT  base IPC port (default 34177; fabric uses +1)
              HYPERCORE_BRIDGE_TICK_MILLIS bridge tick interval (default 50)

            The orchestrator detects run.bat/run.sh in each host directory and wraps
            it with a generated hypercore-run-wrapper script. The wrapper injects
            -Dhypercore.role, -Dhypercore.orchestrator.host/port,
            -Dhypercore.bridge.tickMillis, -Dhypercore.orchestrator.readyMarker and
            --add-modules jdk.incubator.vector through the JAVA_TOOL_OPTIONS
            environment variable, so no manual JVM flag editing is required. If no
            launcher script is present the orchestrator falls back to a direct JVM
            command, which is intended for tests and mock hosts only.

            Diagnostics
            -----------
            On either host: /hypercore bridge status and /hypercore bridge peers.
            """.formatted(minecraftVersion(), minecraftVersion());
        Files.writeString(root.resolve("README.txt"), readme, StandardCharsets.UTF_8);
    }

    private static String minecraftVersion() {
        return System.getProperty("hypercore.minecraftVersion", "1.21.1");
    }
}

<p align="center">
  <img src="./Logo.png" width="180" alt="HyperCore logo">
</p>

<h1 align="center">HyperCore</h1>

<p align="center">
  <a href="https://www.java.com/"><img src="https://img.shields.io/badge/Java-25-ED8B00?logo=java&logoColor=white" alt="Java 25"></a>
  <a href="https://gradle.org/"><img src="https://img.shields.io/badge/Gradle-9.3-02303A?logo=gradle&logoColor=white" alt="Gradle 9.3"></a>
  <a href="https://www.minecraft.net/"><img src="https://img.shields.io/badge/Minecraft-26.1-62B47A" alt="Minecraft 26.1"></a>
  <a href="https://www.vulkan.org/"><img src="https://img.shields.io/badge/Vulkan-optional-AC162C?logo=vulkan&logoColor=white" alt="Optional Vulkan compute"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="Apache License 2.0"></a>
</p>

HyperCore is an experimental independent Minecraft server core. It starts from
Mojang's official Minecraft `26.1` server bundle and applies HyperCore patches
through a reproducible paperweight pipeline. It is not a fork of Paper.

## Architecture

- `server-paperweight`: the standalone server build. It creates
  `hypercore-server-bundler-0.1.0-SNAPSHOT.jar` with
  `net.minecraft.server.Main` as its entry point.
- `paper-server`: paperweight's required patch-layout directory. It contains
  HyperCore patches and a local Mojang-derived patch baseline; it has no Paper
  remote and no Paper server source. Its generated `com/` and `net/` worktree
  directories are build material and must never be committed.
- Region execution: immutable region snapshots are planned on bounded worker
  lanes. Workers return value-only mutations, which the server thread validates
  and applies at a later tick boundary. Vanilla level, entity, block, and chunk
  ticks remain server-thread work until a full region-ownership migration exists.
- Plugins: a narrow `plugin.yml` discovery runtime provides child-first class
  loading, dependency order, lifecycle callbacks, commands, next-tick tasks,
  and server-thread events. It is not Bukkit or Paper binary compatibility.
- Loader compatibility: the standalone core owns a neutral provider SPI.
  Optional Fabric and Forge adapters must remain outside the server JAR because
  their launchers cannot coexist inside one Minecraft server class loader.
- Compute: CPU execution is always available. An optional Vulkan provider is
  loaded from `compute-providers/` through `ServiceLoader` for explicitly f32
  batch operations. It never accelerates arbitrary Minecraft simulation.

The legacy `core`, `fabric`, and `forge` projects are not runtime dependencies
of the standalone server. They remain in this repository only while reusable
contracts are being migrated and must not be treated as the production server
deployment.

## Build

Install JDK 25, then run the standalone project:

```powershell
Set-Location server-paperweight
./gradlew.bat :hypercore-server:setupMacheSources `
  :hypercore-server:applyPatches `
  :hypercore-server:rebuildPatches `
  :hypercore-server:createBundlerJar `
  --offline --no-daemon
```

The output is:

```text
server-paperweight/hypercore-server/build/libs/hypercore-server-bundler-0.1.0-SNAPSHOT.jar
```

`--offline` works after the Mojang bundle, Mache, and Gradle dependencies are
cached. The repository configures `127.0.0.1:7892` for required downloads; run
the initial dependency resolution only while that proxy is available.

## Optional Vulkan Provider

Build the native provider separately:

```powershell
Set-Location server-paperweight
./gradlew.bat :hypercore-vulkan-provider:assembleProviderDirectory --offline --no-daemon
```

Copy the contents of
`hypercore-vulkan-provider/build/compute-providers` into the server's
`compute-providers` directory, or pass
`-Dhypercore.compute.providers=<directory>`. Missing drivers, unsupported
devices, and native failures fall back to the deterministic CPU implementation.

## Current Verification Boundary

The patch pipeline and bundler generation are tested from cached dependencies.
The Vulkan provider has unit and local-dispatch tests. A full server startup,
EULA acceptance, world load, and gameplay tick smoke test are still required
before the standalone core can be called runnable. No claim is made that
ordinary Bukkit/Paper plugins or Fabric/Forge mods run directly on this server.

See [server-paperweight/README.md](server-paperweight/README.md) for the
paperweight worktree model, patch lifecycle, and provider installation details.

## License

Apache License 2.0. Minecraft is a trademark of Microsoft. HyperCore is not
affiliated with Mojang Studios or Microsoft.

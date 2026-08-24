# HyperCore Independent Server Core

This project builds an independent HyperCore server core from Mojang's official
Minecraft server bundle. `paper-server` is a paperweight source-layout name;
it is not a fork of Paper.

## Target

- Minecraft: `26.1`
- Java: JDK 25 with preview APIs enabled
- Patcher: locally published `paperweight-core 2.0.0-beta.21-hypercore.1`
- Output: `hypercore-server` bundler jar with `net.minecraft.server.Main`

## Current Status

The source setup and patch lifecycle are operational:

- `setupMacheSources` completes with Mache `26.1+build.1`.
- The Java and resource worktrees each have a local Git history with
  `ROOT`, `Vanilla`, `Mache`, `ATs`, `Imports`, and patch-base tags. These are
  paperweight patch baselines, not forks or clones of Paper. Their `upstream`
  remotes point only at local Mache cache directories.
- `applyPatches` and `rebuildPatches` apply the HyperCore source patch set
  from the official-server baseline.
- The server resolves `paperweight-core 2.0.0-beta.21-hypercore.1` from Maven
  Local, which contains the Windows local-path Git remote fix.
- The compile classpath includes all libraries extracted from Mojang's server
  bundler plus Mache's annotation-only compile dependencies.

The source patches currently add:

- **Region scheduler** (`dev.hypercore.server.region`): a bounded region
  mailbox scheduler with immutable snapshots, deterministic owner lanes, and
  lifecycle-safe shutdown. Workers return value-only `RegionMutation` records;
  `ServerMutationQueue` applies them on the server thread at a tick boundary
  with ownership, world-bounds, chunk-loaded, and registry validation. Worker
  tasks may only mutate their own region. Vanilla level ticks and world
  mutation stay on the server thread.
- **Plugin runtime** (`dev.hypercore.server.plugin`): server-thread-only
  runtime with `plugin.yml` discovery, child-first JAR isolation, dependency
  ordering, commands, next-tick tasks, and typed events. Failed activations
  roll back their registrations; external cancellation defers to a tick
  boundary.
- **Compute service** (`dev.hypercore.server.compute`): deterministic CPU
  backend (`identity`, f64 `squared-length-3d`, explicit-f32
  `squared-length-3d-f32`) with optional ServiceLoader providers loaded from
  `compute-providers/` through an isolated class loader. Native failures fall
  back to CPU without stopping the server.
- **Loader compatibility registry** (`dev.hypercore.server.compat`):
  lifecycle hooks for future Fabric/Forge adapters with per-provider failure
  isolation.
- **Bukkit compatibility bridge** (`dev.hypercore.server.bukkit`): a narrow
  server-thread facade over the plugin runtime; no Paper binary-compatibility
  claim.
- **Server integration** (`net.minecraft.server.*`): `MinecraftServer` wires
  the runtimes into startup, tick (`hypercoreRegions` profiler section), and
  shutdown; `ServerChunkCache.snapshotFullChunkPositions()` provides a
  thread-guarded full-chunk snapshot for region planning.

Run `createBundlerJar` to generate these artifacts:

- `hypercore-server/build/libs/hypercore-server-0.1.0-SNAPSHOT.jar`
- `hypercore-server/build/libs/hypercore-server-bundler-0.1.0-SNAPSHOT.jar`

The bundler bootstraps through Paperclip and stores
`net.minecraft.server.Main` in `META-INF/main-class`. A real server start, EULA
acceptance, world load, and tick smoke test have not yet been performed.

## Optional Vulkan Compute Provider

The base server has no LWJGL or Vulkan runtime dependency. Build the optional
provider separately when a host needs GPU spatial compute:

```powershell
./gradlew :hypercore-vulkan-provider:assembleProviderDirectory --offline
```

Copy the contents of `hypercore-vulkan-provider/build/compute-providers` to the
server's `compute-providers` directory, or set
`-Dhypercore.compute.providers=<directory>`. The provider is discovered through
`ServiceLoader`; failed native initialization leaves the deterministic CPU
backend active. `squared-length-3d` remains f64 CPU work. The Vulkan provider
implements the explicitly f32 operation `squared-length-3d-f32` and loads its
own LWJGL, shaderc, and platform-native jars only from that directory.

## Local Paperweight Publication

The local paperweight checkout is `../paperweight-2.0.0-beta.21`. Publish the
patched plugin after changing it with:

```powershell
& 'C:\Users\11395\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' `
  ':paperweight-core:publishToMavenLocal' `
  '-Pversion=2.0.0-beta.21-hypercore.1' `
  '--no-daemon'
```

`gradle.properties` selects this distinct local version and
`settings.gradle.kts` places `mavenLocal()` before remote plugin repositories.
This prevents Gradle from silently substituting the upstream release.

## Verification Commands

Run the full baseline toolchain without network access after dependencies have
been cached:

```powershell
./gradlew :hypercore-server:setupMacheSources `
  :hypercore-server:applyPatches `
  :hypercore-server:rebuildPatches `
  :hypercore-server:createBundlerJar `
  --offline
```

Run the unit tests (runtime boundaries, region mailbox scheduler, Vulkan
provider):

```powershell
./gradlew :hypercore-server:test `
  :hypercore-vulkan-provider:test --offline --no-daemon
```

The task graph explicitly orders source, resource, and feature patch application
before patch rebuilding and Java compilation. This is required by Gradle 9's
strict implicit-dependency validation.

## Project Layout

- `hypercore-server/build.gradle.kts`: server bundle, source patch lifecycle,
  extracted-library compile classpath, and artifact metadata.
- `hypercore-server/src/minecraft/java`: generated and patched Mojang Java
  source worktree with local patch-baseline commits.
- `hypercore-server/src/minecraft/resources`: generated and patched Mojang
  resource worktree with local patch-baseline commits.
- `hypercore-server/src/test/java`: unit tests that run against the patched
  worktree classes (`RuntimeBoundaryTest`, `RegionMailboxSchedulerTest`).
- `hypercore-vulkan-provider/`: optional compute provider subproject; builds a
  self-contained provider directory through `assembleProviderDirectory`.
- `paper-server/patches`: HyperCore source, resource, and feature patches.

`paper-server` is retained only because paperweight's default patch layout uses
that directory name. It contains no Paper source. Future HyperCore server
changes belong in `paper-server/patches`, not directly in generated worktrees.
The patch subdirectories are created when the first real patch is rebuilt; do
not add placeholder files there because paperweight treats every file as a
patch.

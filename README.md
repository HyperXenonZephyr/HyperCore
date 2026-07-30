# HyperCore

HyperCore is an experimental high-performance Minecraft Java server project targeting multi-loader mod execution. Its long-term goals are simultaneous Forge mod and Fabric mod execution alongside Bukkit/Paper plugin interoperability, safe multi-core execution, optional GPU compute acceleration, and measurable server-side optimization.

> [!IMPORTANT]
> HyperCore is at an early prototype stage. It is not currently a Bukkit/Paper-compatible production server, and it does not yet move Minecraft world simulation onto the GPU.
>
> The runtime is split into a loader-agnostic `:core` consumed by separate `:forge` and `:fabric` adapter subprojects, both of which build self-contained server-side mod JARs. HyperCore loads under either loader; running Forge and Fabric mods simultaneously in one process is a future objective and is not yet implemented.

## Current status

The project is a multi-loader Gradle build: a loader-agnostic `:core` runtime consumed by `:forge` and `:fabric` adapter subprojects, both producing self-contained server-side mod JARs. The established foundation:

- Minecraft 1.21.1 and Java 21 are pinned; Forge 52.1.16 and Fabric Loader 0.16.9 (Fabric API 0.115.1+1.21.1) are the current adapter targets.
- HyperCore loads as a server-side component under Forge or under Fabric, each as a separate build (see the note above on simultaneous execution).
- A bounded worker pool reserves one logical CPU for the main server thread and rejects excess work instead of growing an unbounded queue.
- A 200-tick latency window reports average, p95, and maximum tick duration.
- Operator diagnostics are available through `/hypercore status`, `/hypercore timings`, `/hypercore capabilities`, and `/hypercore regions`.
- A Forge GameTest verifies that HyperCore loads in a real dedicated-server environment; Forge remains the primary test bed.
- Configuration controls worker count, queue capacity, tick sampling, GPU probing, the default-on GPU compute path, and CPU backend selection; Forge reads `config/hypercore-common.toml` and Fabric reads `config/hypercore.properties` with the same keys.
- OS, JVM, logical CPU, and graphics adapter capabilities are reported; Vulkan compute initializes by default on a dedicated daemon thread and falls back safely when unavailable.
- A scalar CPU spatial batch backend and a Vulkan compute backend implement the same squared-distance operation for correctness comparisons.
- A logical region-owner model provides deterministic ownership, per-target FIFO mailboxes, tick-boundary dispatch, and cross-region message accounting.
- A controlled plugin bridge kernel provides lifecycle callbacks, plugin-owned commands, permissions, cancellable prioritized events, and plugin-owned sync/async tick scheduling. External HyperCore SPI plugin JARs are discovered from `plugins/` with isolated class loaders and deterministic dependency ordering. A prototype Bukkit/Paper compatibility layer additionally discovers JARs using `plugin.yml`, wraps `JavaPlugin` main classes, and bridges their lifecycle, commands, and sync scheduling through the same SPI. The Forge command bridge exposes registered commands and `/hypercore plugins` reports bridge and scheduler health.
- GPU support now includes Vulkan loader/API detection, device selection, two compiled SPIR-V compute pipelines, device-local position buffers with host-visible staging, direct, resident-snapshot, and 33-query chunking correctness self-tests, and a configurable batch-size offload policy. Runtime execution uses `cpu-scalar` while Vulkan initializes, switches atomically to `adaptive-vulkan` when ready, and falls back to CPU after a later dispatch failure.
- An immutable structure-of-arrays position snapshot and radius-query service now uses a GPU-generated packed match mask instead of reading every distance back to CPU. Repeated queries over the same `PositionBatch` retain one Vulkan position upload, while `withinRadii` records up to 32 radius dispatches in one command buffer and one fence wait before transparently chunking larger groups. Query, candidate, match, snapshot, multi-query batch, mask, readback-byte, initialization, and CPU/GPU counters are exposed through `/hypercore capabilities`.
- A deterministic `:core:benchmarkCompute` Gradle task measures complete scalar/vector/Vulkan calls, resident snapshot calls, and eight-query individual-versus-batched submission. The current RTX 4060 report is recorded in [BENCHMARKS.md](BENCHMARKS.md); batching improved p50 between `1.33x` and `7.56x` from 4K through 1M candidates in the latest run, while the 4M compute-dominated case measured `1.19x`.

## Build

HyperCore is a multi-loader Gradle build with three subprojects:

- **`:core`** — the loader-agnostic runtime (compute backends, region model, plugin bridge, configuration). A plain Java 21 library with no Minecraft references.
- **`:forge`** — the Forge 1.21.1 adapter. Produces the deployable mod JAR.
- **`:fabric`** — the Fabric 1.21.1 adapter. Produces a self-contained mod JAR.

Requirements:

- A Java 21 JDK
- Network access for the first Gradle dependency download

Build every subproject:

```powershell
./gradlew.bat build
```

Deployable artifacts:

| Loader | Artifact | Notes |
| --- | --- | --- |
| Forge | `forge/build/libs/hypercore-forge-0.1.0-SNAPSHOT-all.jar` | Carries the Vulkan binding through Forge Jar-in-Jar. |
| Fabric | `fabric/build/libs/hypercore-fabric-0.1.0-SNAPSHOT.jar` | Bundles `:core` main + vector output and `lwjgl-vulkan` directly. |

The Forge `-all.jar` is the production artifact; the plain `hypercore-forge-...jar` is for development and dependency-aware tooling. `:core` builds a library JAR (`hypercore-core-...jar`) that is not meant to be deployed on its own.

### Forge development server

```powershell
./gradlew.bat :forge:runServer
```

On first launch, set `eula=true` in `forge/run/eula.txt` only after reviewing the Minecraft EULA. Automated dedicated-server validation does not require accepting the normal server EULA:

```powershell
./gradlew.bat :forge:runGameTestServer
```

### Fabric development server

```powershell
./gradlew.bat :fabric:runServer
```

### Compute benchmark

```powershell
./gradlew.bat :core:benchmarkCompute
```

The generated report is written to `core/build/reports/hypercore/compute-benchmark.md`. It is a microbenchmark of the spatial mask backend, not an MSPT or world-simulation benchmark.

The Forge development run tasks automatically stage `:core` and `:forge` classes and resources — including the compiled `.spv` shaders and the vector source-set output — into a single mod directory under `forge/build/dev-mod` via the `prepareDevMod` task. This keeps normal Gradle build outputs reproducible while giving Forge one complete exploded mod root.

## Configuration

Configuration keys are shared across loaders. Forge reads `config/hypercore-common.toml`; the Fabric adapter reads the same keys from `config/hypercore.properties` (a plain Java properties file). Missing keys fall back to the defaults below.

| Key | Default | Purpose |
| --- | ---: | --- |
| `execution.workerThreads` | `0` | Automatic mode reserves one logical processor for the server thread. |
| `execution.queueCapacity` | `0` | Automatic mode allocates 64 queued tasks per worker, with a minimum of 256. |
| `metrics.tickSampleWindow` | `200` | Controls the rolling tick latency sample count. |
| `compute.probeGpu` | `true` | Enables best-effort graphics adapter enumeration during startup. |
| `compute.enableGpu` | `true` | Enables Vulkan compute initialization and the CPU fallback router. |
| `compute.gpuMinimumBatchSize` | `16384` | Minimum batch size eligible for Vulkan offload. |
| `compute.cpuBackend` | `auto` | Selects the CPU backend: `auto` uses the Vector API backend when available and falls back to `scalar`. |

Invalid values are rejected by Forge's config specification, or fall back to the default with a logged warning under Fabric. GPU loader, device, allocation, and dispatch failures are reported and fall back to CPU-only operation without stopping the server.

## Compute backends

The scalar backend is the deterministic correctness baseline for structure-of-arrays squared-distance batches. A Java Vector API (`jdk.incubator.vector`) CPU backend vectorizes the same operation with bit-identical results and is 1.24x–2.04x faster than scalar after JIT priming. `compute.cpuBackend` (default `auto`) selects the vector backend at runtime when the incubator module is available and falls back to scalar otherwise; scalar is also the permanent fallback if the vector backend ever fails to load. Vulkan device creation and the 1,024-element GPU-vs-CPU self-test run on a dedicated daemon thread, so server startup and compute callers continue on the CPU backend while initialization is in progress. The router switches atomically to `adaptive-vulkan` when ready, sends batches below `compute.gpuMinimumBatchSize` to CPU, and permanently falls back after a runtime GPU failure.

`SpatialQueryEngine` accepts an immutable copy of structure-of-arrays positions and returns the ordered indices whose squared distance is within an inclusive radius. Its scalar path packs matches directly, while its Vulkan path processes 32 candidates per invocation and returns one 32-bit mask word. The engine weakly caches one prepared backend snapshot per `PositionBatch`; repeated queries reuse resident XYZ data, while snapshot switching or direct buffer use is detected by a Vulkan data generation and triggers a correct re-upload. `withinRadii` returns query-major results and batches up to 32 Vulkan dispatches into one submission and fence wait. Larger groups are chunked without changing result order. Runtime shutdown closes query snapshots before the compute backend.

The packed mask, resident snapshot, and multi-query submission are exact transfer and synchronization reductions, not end-to-end server performance claims. In the latest RTX 4060 run, eight batched queries were `7.56x`, `4.94x`, `3.10x`, `1.70x`, and `1.33x` faster than eight individual GPU submissions from 4K through 1M candidates. At 4M, compute cost dominated and the batch measured `1.19x`, so batching is not treated as universally faster. With positions held in device-local memory, the resident-snapshot path no longer crosses PCIe on every dispatch and now reaches a repeatable CPU crossover (65,536–262,144 candidates across runs); the full-call path (which still includes the staging→device-local copy) does not cross over. Entity, chunk, and block simulation remain outside the GPU path, and the default offload threshold stays unchanged until repeatable end-to-end query or tick gains are established.

## Region execution model

The region prototype divides each dimension into 8 by 8 chunk regions. Every region maps deterministically to one logical owner lane. Messages are addressed from a source region to a target region and are only dispatched at a tick boundary.

Within a dispatched tick:

- Messages for the same target region retain FIFO order.
- Regions assigned to the same owner execute serially.
- Different owners may execute concurrently on the bounded HyperCore worker pool.
- Messages submitted while a tick is in flight are deferred to the next tick.
- Executor backpressure requeues an owner batch instead of dropping its messages.

This is an ownership and messaging foundation, not parallel Minecraft world ticking. Vanilla, Forge, and Fabric entity, block entity, and chunk mutations remain on their existing threads until explicit isolation and compatibility tests prove that a workload can move safely.

## Architecture direction

1. **Forge foundation**: preserve native Forge lifecycle, registries, events, and mod compatibility.
2. **Mod-loader interoperability**: target simultaneous Forge mod and Fabric mod execution on one server. The runtime is split into a loader-agnostic `:core` with separate `:forge` and `:fabric` adapters, both buildable. Running both loaders in one process requires reconciling their incompatible transform and mapping pipelines; this is the open work behind the future-objective roadmap entry.
3. **Compatibility bridge**: implement a controlled Bukkit-compatible API and event bridge rather than merging unrelated patched server jars.
4. **Parallel execution**: establish region ownership and tick-boundary message passing before parallel world mutation.
5. **Compute backends**: benchmark CPU scalar, Java Vector API, and GPU implementations for batch-friendly workloads.
6. **Validation**: require behavior tests and end-to-end MSPT results for every optimization.

## GPU policy

GPU support is enabled by default and always has a CPU fallback. HyperCore enumerates graphics adapters and VRAM through OSHI, probes the Vulkan loader and API version through JNA, selects a compute-capable physical device, and submits compiled squared-distance and packed-radius-mask SPIR-V kernels against device-local position buffers (fed by host-visible staging with a transfer→compute pipeline barrier) plus a host-visible packed-mask output buffer. Prepared position snapshots retain uploaded XYZ data across repeated queries and report upload/reuse counts. Vulkan creation and both correctness self-tests are asynchronous; shutdown interrupts and bounded-joins initialization, and a backend that finishes after shutdown closes its native resources instead of becoming active. GPU use is gated by batch size and correctness verification; device limits or dispatch failures disable only the GPU path. Candidate workloads include batch terrain density generation, spatial broad-phase queries, and other data-oriented jobs. A GPU path will only be retained for a workload when it improves complete server tick or generation latency after upload, synchronization, and readback costs.

## Plugin bridge kernel

The current plugin layer is a HyperCore SPI designed to establish compatibility boundaries without claiming Bukkit or Paper binary compatibility. A plugin can declare a descriptor, receive `onLoad`/`onEnable`/`onDisable` lifecycle callbacks, register commands and aliases, define permission defaults and wildcard overrides, subscribe to prioritized cancellable events, and schedule sync or async next-tick, delayed, and repeating tasks. Sync callbacks execute from the server tick caller. Async callbacks use the bounded worker pool and must not mutate server-owned world state. Failed or disabled plugins have their pending tasks and other owned registrations removed during cleanup.

External HyperCore plugins are loaded from JARs in the server's `plugins/` directory. Each participating JAR must contain a root-level `hypercore-plugin.json`:

```json
{
  "id": "example_plugin",
  "name": "Example Plugin",
  "version": "1.0.0",
  "apiVersion": 1,
  "main": "com.example.ExamplePlugin",
  "depends": ["required_plugin"],
  "softDepends": ["optional_plugin"]
}
```

The main class must implement `dev.hypercore.plugin.HyperPlugin` and have an accessible no-argument constructor. Hard dependencies determine lifecycle order and block dependents when unavailable. Soft dependencies affect order only when present and when doing so does not create a cycle. Every plugin receives a child-first class loader with server, Minecraft, logging, Gson, and HyperCore API namespaces delegated to the parent. Callback context class loaders are installed for lifecycle, command, event, and scheduled execution. Class sharing between plugin class loaders is not implemented, so dependencies currently express lifecycle order rather than a Java linkage contract.

Forge command registration is bridged into this SPI. A prototype Bukkit/Paper compatibility layer now discovers JARs using `plugin.yml`, translates the descriptor into the HyperCore SPI, wraps `JavaPlugin` main classes with a lifecycle/command/scheduler adapter, and bridges `plugin.yml`-defined commands and sync scheduling through the HyperCore registry. The adapter ships minimal `org.bukkit.*` API stubs (`JavaPlugin`, `Server`, `Bukkit`, `PluginCommand`, `CommandSender`, `BukkitScheduler`) sufficient for plugins that only touch those surfaces; it is not binary-compatible with the full Bukkit/Paper API and does not map the Bukkit event catalog, tab completion, `plugin.yml` permissions, or cross-plugin lookups. See [COMPATIBILITY.md](COMPATIBILITY.md) for the current behavior matrix and explicit unsupported areas.

## Roadmap

- [x] Forge 1.21.1 project foundation
- [x] Fabric loader adapter subproject (buildable; not simultaneous with Forge)
- [ ] Simultaneous Forge mod and Fabric mod execution in one runtime (future objective)
- [x] Safe background executor and basic tick diagnostics
- [x] Unit tests for metrics, isolated worker execution, and queue backpressure
- [x] Automated Forge dedicated-server GameTest
- [x] Configuration and capability detection
- [x] Scalar CPU spatial compute baseline
- [x] Region ownership and cross-region task model prototype
- [x] Controlled plugin bridge kernel for commands, permissions, lifecycle, and events
- [x] CPU vector compute baseline (runtime CPU backend via `compute.cpuBackend`; 1.24x–2.04x faster than scalar)
- [x] Vulkan compute prototype with SPIR-V shader, self-test, and CPU fallback
- [x] Asynchronous Vulkan lifecycle and immutable spatial-radius query service
- [x] Packed GPU radius-mask pipeline with compressed result readback
- [x] Reproducible scalar-vs-Vulkan packed-mask benchmark harness
- [x] Persistent mapped host-coherent Vulkan buffers
- [x] Resident snapshot reuse across multiple spatial queries
- [x] Multi-query Vulkan command batching with bounded chunking
- [x] Device-local snapshot storage with a repeatable resident GPU crossover (65,536–262,144 candidates)
- [x] Plugin-owned tick scheduler and initial compatibility matrix
- [x] External HyperCore SPI plugin discovery, isolation, and dependency ordering
- [x] Bukkit/Paper `plugin.yml` descriptor translation and `JavaPlugin` lifecycle/command/scheduler bridge prototype (minimal `org.bukkit.*` stubs; not binary-compatible)
- [ ] Full Bukkit/Paper API conformance, event catalog, tab completion, and `plugin.yml` permissions

See [CHANGELOG.md](CHANGELOG.md) for completed changes.

## License

Apache License 2.0. Minecraft and Minecraft Forge are trademarks of their respective owners. HyperCore is not affiliated with Mojang Studios or Microsoft.

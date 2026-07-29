# Changelog

All notable changes to HyperCore are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project intends to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Initial Forge 1.21.1 project using Forge 52.1.16 and Java 21.
- Dedicated-server HyperCore entry point and lifecycle logging.
- Managed background executor sized from the available logical processors.
- Rolling 200-tick latency metrics with average, p95, and maximum values.
- `/hypercore status` command for worker, task, and heap diagnostics.
- `/hypercore timings` command for recent tick latency diagnostics.
- Initial architecture, GPU policy, build instructions, and roadmap documentation.
- JUnit coverage for tick metrics and isolated worker execution.
- Verified Forge userdev server-side loading on Minecraft 1.21.1.
- Replaced the unbounded executor queue with bounded backpressure, rejection metrics, and pending-task cancellation on shutdown.
- Expanded `/hypercore status` with active worker, queue capacity, and rejection diagnostics.
- Added an automated Forge GameTest that verifies HyperCore in the dedicated-server environment.
- Added Forge common configuration for worker threads, task queue capacity, tick sampling, and optional GPU probing.
- Delayed worker-pool creation until the server lifecycle has loaded configuration.
- Added structured OS, JVM, CPU, graphics adapter, device ID, and VRAM capability detection through OSHI.
- Added `/hypercore capabilities` and exposed the active compute backend in `/hypercore status`.
- Added a `cpu-scalar` structure-of-arrays squared-distance backend as the correctness baseline for future vector and GPU implementations.
- Added tests for configuration resolution, runtime lifecycle, capability reporting, compute correctness, and custom tick windows.
- Added deterministic 8 by 8 chunk region keys and logical owner-lane assignment.
- Added tick-boundary region mailboxes with per-target FIFO ordering and cross-region message accounting.
- Added owner-batch backpressure recovery, message-failure isolation, and deferred submission while a region tick is in flight.
- Added `/hypercore regions` diagnostics for queued messages, owner lanes, failures, and partial ticks.
- Added concurrency tests for negative chunk mapping, ownership stability, mailbox ordering, tick isolation, and rejected-batch requeueing.
- Added the controlled plugin bridge kernel with plugin lifecycle management, command aliases, permission defaults and overrides, prioritized cancellable events, cleanup on failure, and a Forge Brigadier command bridge.
- Added `/hypercore plugins` diagnostics and plugin-owned command dispatch while explicitly keeping Bukkit/Paper namespace and external JAR compatibility out of scope for this milestone.
- Added the Vulkan loader/API probe foundation through JNA, a configurable `compute.gpuMinimumBatchSize` threshold, and explicit GPU offload decisions with a CPU fallback.
- Added unit coverage for plugin lifecycle, command dispatch, permissions, event ordering, Vulkan version parsing, and GPU offload policy decisions.
- Added a default-on LWJGL Vulkan compute backend with Jar-in-Jar packaging, build-time Shaderc compilation, device and queue selection, host-visible storage buffers, a compiled squared-distance SPIR-V kernel, and a 1,024-element CPU/GPU correctness self-test.
- Added adaptive GPU routing: batches above the configured threshold use Vulkan, smaller batches use `cpu-scalar`, and initialization, allocation, limit, or dispatch failures fall back without stopping the server.
- Added `/hypercore capabilities` reporting for the selected Vulkan device, CPU/GPU batch counts, GPU failures, and fallback reason.
- Documented the deployable `-all.jar` artifact that carries the Vulkan binding through Forge Jar-in-Jar.
- Added an explicit asynchronous Vulkan lifecycle with `INITIALIZING`, `READY`, `UNAVAILABLE`, and `CLOSED` states; compute requests remain on CPU until the verified GPU backend is installed atomically.
- Added race-safe Vulkan shutdown that interrupts and bounded-joins pending initialization, prevents dispatch/cleanup overlap, and closes a backend that finishes after shutdown.
- Added `SpatialQueryEngine` with immutable structure-of-arrays snapshots, inclusive radius matching, defensive result copies, and query, candidate, and match counters.
- Expanded `/hypercore capabilities` with Vulkan initialization state and duration plus spatial-query counters.
- Added unit coverage for asynchronous CPU-to-GPU switching, initialization cancellation, immutable query inputs and outputs, inclusive radius boundaries, validation, and query metrics.
- Added a second build-time Shaderc pipeline that evaluates 32 radius candidates per Vulkan invocation and returns a packed 32-bit match mask.
- Routed `SpatialQueryEngine` through the packed mask contract on both scalar and adaptive backends, reducing Vulkan result readback from four bytes per candidate to four bytes per 32 candidates.
- Extended Vulkan startup verification to compare packed GPU radius masks against the scalar baseline before the backend becomes ready.
- Added CPU/GPU radius-mask batch counts and GPU mask-readback bytes to `/hypercore capabilities`.
- Added unit coverage for packed-word boundaries, undersized mask rejection, adaptive GPU routing, and mask diagnostics.
- Added the `benchmarkCompute` Gradle task and durable `BENCHMARKS.md` report with warmed CPU/Vulkan p50 and p95 measurements, complete GPU transfer/dispatch/readback scope, and conservative crossover detection.
- Recorded that the current RTX 4060 run has no sustained GPU p50 crossover from 4,096 through 4,194,304 candidates; the configured offload threshold remains unchanged pending further transfer optimization.
- Changed Vulkan storage buffers to persistent mapped host-coherent allocations, removing per-dispatch map/unmap operations while preserving fence synchronization and deterministic CPU fallback.
- Added transfer-mode diagnostics and refreshed `BENCHMARKS.md` with the persistent-mapping run and directional before/after GPU p50 comparisons. No threshold change is made because the measured crossover is still absent.
- Added immutable prepared position snapshots to the compute contract and weak per-`PositionBatch` caching in `SpatialQueryEngine`, allowing uninterrupted repeated radius queries to reuse resident Vulkan XYZ data without three new host uploads.
- Added Vulkan data-generation invalidation so switching snapshots, resizing buffers, or using a direct compute call forces the next resident query to upload the correct positions before dispatch.
- Extended Vulkan startup verification with an original-shifted-original snapshot sequence that exercises resident reuse and re-upload after shared-buffer invalidation.
- Added adaptive CPU fallback for snapshots created before Vulkan is ready, snapshot upload/reuse diagnostics in `/hypercore capabilities`, and query-snapshot cleanup before compute-backend shutdown.
- Extended `benchmarkCompute` to measure full-transfer and resident-snapshot GPU calls independently. Four RTX 4060 runs confirmed lower resident GPU cost at large batches but did not establish a repeatable conservative crossover, so the default threshold remains unchanged.
- Added `RadiusMaskQuery` and `SpatialQueryEngine.withinRadii(...)` for query-major multi-radius results over one immutable position snapshot.
- Added Vulkan multi-query command batching with one fence wait per bounded group of up to 32 radius queries; larger groups are chunked while preserving result order.
- Extended Vulkan startup verification with a 33-query radius-mask self-test that crosses the 32-query submission boundary.
- Extended `benchmarkCompute` and `BENCHMARKS.md` with eight-query individual-versus-batched submission measurements. The latest RTX 4060 run measured `2.56x` to `5.91x` faster from 4K through 1M candidates, but `0.95x` at 4M where compute cost dominated; batching is therefore not treated as universally faster.
- Added a plugin-owned tick scheduler with sync and bounded-async next-tick, delayed, and repeating tasks exposed through `PluginContext`.
- Added automatic cancellation of pending scheduled tasks during plugin failure or disable cleanup; repeating tasks that fail are cancelled to prevent repeated error loops.
- Expanded `/hypercore plugins` with scheduled-task and task-failure diagnostics.
- Added `COMPATIBILITY.md` to distinguish implemented HyperCore SPI behavior from unsupported Bukkit/Paper namespaces, external JAR loading, and world-threading claims.
- Added unit coverage for one-time snapshot preparation, repeated GPU routing, snapshot diagnostics, and independent resident-crossover reporting.
- Added external HyperCore SPI plugin discovery from `plugins/` using a versioned `hypercore-plugin.json` descriptor.
- Added one child-first class loader per external plugin, protected parent-first server/API namespaces, and callback context-class-loader propagation across lifecycle, commands, events, and scheduled tasks.
- Added deterministic hard and soft dependency ordering, missing-dependency and cycle rejection, failure isolation, reverse-order unload, and class-loader cleanup.
- Expanded `/hypercore plugins` with the external plugin count and documented that Bukkit/Paper `plugin.yml`, `org.bukkit.*`, and cross-plugin class sharing remain unsupported.
- Added a Java Vector API (`jdk.incubator.vector`) CPU spatial compute backend (`cpu-vector`) that vectorizes the squared-distance and packed radius-mask operations with bit-identical results to the scalar baseline.
- Added a `vector` Gradle source set compiled against the live JDK 21 toolchain with the incubator module added explicitly, preserving `--release 21` on the main source set; main code loads the backend reflectively so the incubator module is only required when the vector path is exercised.
- Extended `SpatialComputeBenchmark` and the `benchmarkCompute` task with an optional CPU vector tier and report columns. The vector backend is a benchmark baseline only and is not yet wired into the adaptive router; the scalar backend remains the runtime CPU fallback pending repeatable measurements.
- Added unit coverage asserting the vector backend matches the scalar baseline across vector-loop, word-boundary, and tail sizes, plus validation, inclusive-boundary, and snapshot paths.

### Fixed

- Removed Vulkan creation and self-test work from the server startup event so GPU setup no longer executes on the server thread.
- Disabled Gradle configuration cache because ForgeGradle 7 could restore an incomplete merged source set after `clean`, producing a JAR with missing classes.
- Disabled ForgeGradle's merged source-set output, restoring standard Gradle class directories and preventing clean builds from omitting unchanged classes.
- Staged development classes and resources into one exploded mod directory so Forge server and GameTest runs load HyperCore correctly with standard Gradle source-set outputs.
- Fixed executor completion metrics so a completed future cannot become visible before `completedTasks` is incremented.

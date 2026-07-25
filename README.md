# HyperCore

HyperCore is an experimental high-performance Minecraft Java server project built on Forge. Its long-term goals are Forge mod and Bukkit/Paper plugin interoperability, safe multi-core execution, optional GPU compute acceleration, and measurable server-side optimization.

> [!IMPORTANT]
> HyperCore is at an early prototype stage. It is not currently a Bukkit/Paper-compatible production server, and it does not yet move Minecraft world simulation onto the GPU.

## Current status

Milestones 0 through 3 establish a buildable, dedicated-server-only Forge foundation:

- Minecraft 1.21.1, Forge 52.1.16, and Java 21 are pinned.
- HyperCore loads as a server-side Forge component.
- A bounded worker pool reserves one logical CPU for the main server thread and rejects excess work instead of growing an unbounded queue.
- A 200-tick latency window reports average, p95, and maximum tick duration.
- Operator diagnostics are available through `/hypercore status`, `/hypercore timings`, `/hypercore capabilities`, and `/hypercore regions`.
- A Forge GameTest verifies that HyperCore loads in a real dedicated-server environment.
- Forge configuration controls worker count, queue capacity, tick sampling, and GPU probing.
- OS, JVM, logical CPU, and graphics adapter capabilities are reported without making GPU acceleration a startup requirement.
- A scalar CPU spatial batch backend provides the correctness baseline for later Vector API and GPU implementations.
- A logical region-owner model provides deterministic ownership, per-target FIFO mailboxes, tick-boundary dispatch, and cross-region message accounting.
- A controlled plugin bridge kernel provides lifecycle callbacks, plugin-owned commands, permissions, and cancellable prioritized events. The Forge command bridge exposes registered commands and `/hypercore plugins` reports bridge health.
- GPU probing now includes Vulkan loader/API detection and a configurable batch-size offload policy. Runtime execution remains `cpu-scalar` until a Vulkan compute backend passes correctness and end-to-end latency gates.

## Build

Requirements:

- A Java 21 JDK
- Network access for the first Gradle dependency download

On Windows:

```powershell
./gradlew.bat build
```

The development server can be launched with:

```powershell
./gradlew.bat runServer
```

On first launch, set `eula=true` in `run/eula.txt` only after reviewing the Minecraft EULA.

Automated dedicated-server validation does not require accepting the normal server EULA:

```powershell
./gradlew.bat runGameTestServer
```

The development run tasks automatically stage compiled classes and resources into a single mod directory under `build/dev-mod`. This keeps normal Gradle build outputs reproducible while giving Forge one complete exploded mod root.

## Configuration

Forge creates `config/hypercore-common.toml` on first launch.

| Key | Default | Purpose |
| --- | ---: | --- |
| `execution.workerThreads` | `0` | Automatic mode reserves one logical processor for the server thread. |
| `execution.queueCapacity` | `0` | Automatic mode allocates 64 queued tasks per worker, with a minimum of 256. |
| `metrics.tickSampleWindow` | `200` | Controls the rolling tick latency sample count. |
| `compute.probeGpu` | `true` | Enables best-effort graphics adapter enumeration during startup. |
| `compute.gpuMinimumBatchSize` | `16384` | Minimum batch size eligible for a future GPU backend. |

Invalid values are rejected by Forge's config specification. GPU probe failures are reported and fall back to CPU-only operation.

## Compute backends

The first backend is `cpu-scalar`. It implements a structure-of-arrays squared-distance batch used as a deterministic correctness baseline for spatial broad-phase experiments. It is not wired into Minecraft entity simulation yet. Future CPU Vector API and Vulkan implementations must produce equivalent results before performance comparisons are accepted.

## Region execution model

The region prototype divides each dimension into 8 by 8 chunk regions. Every region maps deterministically to one logical owner lane. Messages are addressed from a source region to a target region and are only dispatched at a tick boundary.

Within a dispatched tick:

- Messages for the same target region retain FIFO order.
- Regions assigned to the same owner execute serially.
- Different owners may execute concurrently on the bounded HyperCore worker pool.
- Messages submitted while a tick is in flight are deferred to the next tick.
- Executor backpressure requeues an owner batch instead of dropping its messages.

This is an ownership and messaging foundation, not parallel Minecraft world ticking. Vanilla and Forge entity, block entity, chunk, and capability mutations remain on their existing threads until explicit isolation and compatibility tests prove that a workload can move safely.

## Architecture direction

1. **Forge foundation**: preserve native Forge lifecycle, registries, events, and mod compatibility.
2. **Compatibility bridge**: implement a controlled Bukkit-compatible API and event bridge rather than merging unrelated patched server jars.
3. **Parallel execution**: establish region ownership and tick-boundary message passing before parallel world mutation.
4. **Compute backends**: benchmark CPU scalar, Java Vector API, and optional GPU implementations for batch-friendly workloads.
5. **Validation**: require behavior tests and end-to-end MSPT results for every optimization.

## GPU policy

GPU support will be optional and will always have a CPU fallback. HyperCore currently enumerates graphics adapters and VRAM through OSHI, probes the Vulkan loader and supported API version through JNA, and evaluates whether a batch reaches the configured offload threshold. It still does not submit GPU work. Candidate workloads include batch terrain density generation, spatial broad-phase queries, and other data-oriented jobs. A GPU path will only be retained when it improves complete server tick or generation latency after upload, synchronization, and readback costs.

## Plugin bridge kernel

The current plugin layer is an internal HyperCore SPI designed to establish compatibility boundaries without claiming Bukkit or Paper binary compatibility. A plugin can declare a descriptor, receive `onLoad`/`onEnable`/`onDisable` lifecycle callbacks, register commands and aliases, define permission defaults and wildcard overrides, and subscribe to prioritized cancellable events. Failed plugins are isolated and their owned registrations are removed during cleanup.

Forge command registration is bridged into this SPI, but external plugin JAR discovery, the `org.bukkit.*` namespace, Bukkit scheduler semantics, and complete Bukkit/Paper event coverage are not implemented yet. Those require a separately versioned adapter and a compatibility matrix.

## Roadmap

- [x] Forge 1.21.1 project foundation
- [x] Safe background executor and basic tick diagnostics
- [x] Unit tests for metrics, isolated worker execution, and queue backpressure
- [x] Automated Forge dedicated-server GameTest
- [x] Configuration and capability detection
- [x] Scalar CPU spatial compute baseline
- [x] Region ownership and cross-region task model prototype
- [x] Controlled plugin bridge kernel for commands, permissions, lifecycle, and events
- [ ] CPU vector compute baseline
- [ ] Optional Vulkan compute prototype
- [ ] Bukkit/Paper namespace adapter and mod/plugin compatibility matrix

See [CHANGELOG.md](CHANGELOG.md) for completed changes.

## License

Apache License 2.0. Minecraft and Minecraft Forge are trademarks of their respective owners. HyperCore is not affiliated with Mojang Studios or Microsoft.

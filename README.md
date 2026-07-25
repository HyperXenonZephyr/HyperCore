# HyperCore

HyperCore is an experimental high-performance Minecraft Java server project built on Forge. Its long-term goals are Forge mod and Bukkit/Paper plugin interoperability, safe multi-core execution, optional GPU compute acceleration, and measurable server-side optimization.

> [!IMPORTANT]
> HyperCore is at an early prototype stage. It is not currently a Bukkit/Paper-compatible production server, and it does not yet move Minecraft world simulation onto the GPU.

## Current status

Milestone 0 establishes a buildable, dedicated-server-only Forge foundation:

- Minecraft 1.21.1, Forge 52.1.16, and Java 21 are pinned.
- HyperCore loads as a server-side Forge component.
- A bounded worker pool reserves one logical CPU for the main server thread.
- A 200-tick latency window reports average, p95, and maximum tick duration.
- Operator diagnostics are available through `/hypercore status` and `/hypercore timings`.
- Forge userdev startup has been smoke-tested through the dedicated GameTest launch target; HyperCore initializes on the server environment.
- GPU acceleration and plugin compatibility remain planned work, not claimed features.

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

## Architecture direction

1. **Forge foundation**: preserve native Forge lifecycle, registries, events, and mod compatibility.
2. **Compatibility bridge**: implement a controlled Bukkit-compatible API and event bridge rather than merging unrelated patched server jars.
3. **Parallel execution**: establish region ownership and tick-boundary message passing before parallel world mutation.
4. **Compute backends**: benchmark CPU scalar, Java Vector API, and optional GPU implementations for batch-friendly workloads.
5. **Validation**: require behavior tests and end-to-end MSPT results for every optimization.

## GPU policy

GPU support will be optional and will always have a CPU fallback. Candidate workloads include batch terrain density generation, spatial broad-phase queries, and other data-oriented jobs. A GPU path will only be retained when it improves complete server tick or generation latency after upload, synchronization, and readback costs.

## Roadmap

- [x] Forge 1.21.1 project foundation
- [x] Safe background executor and basic tick diagnostics
- [x] Unit tests for metrics and isolated worker execution
- [x] Forge userdev server-side loading smoke test
- [ ] Automated dedicated-server smoke test
- [ ] Configuration and capability detection
- [ ] Region ownership and cross-region task model
- [ ] Minimal Bukkit command, permission, and event API
- [ ] CPU vector compute baseline
- [ ] Optional Vulkan compute prototype
- [ ] Mod/plugin compatibility matrix and performance suite

See [CHANGELOG.md](CHANGELOG.md) for completed changes.

## License

Apache License 2.0. Minecraft and Minecraft Forge are trademarks of their respective owners. HyperCore is not affiliated with Mojang Studios or Microsoft.

# HyperCore Compatibility Matrix

This matrix describes tested behavior, not aspirational API coverage. `HyperCore SPI` means the internal `dev.hypercore.plugin` contract. `Prototype` means a Bukkit/Paper JAR path exists and is unit-tested but is not binary-compatible with the full Bukkit/Paper API and has not been validated against production plugins.

## Plugin API

| Capability | HyperCore SPI | Bukkit/Paper plugin JARs | Notes |
| --- | --- | --- | --- |
| Load, enable, disable lifecycle | Implemented | Prototype | `JavaPlugin` lifecycle is bridged through `BukkitPluginAdapter`; lifecycle failures are isolated and owned registrations are cleaned up. |
| Commands and aliases | Implemented | Prototype | `plugin.yml` commands are translated into HyperCore `CommandDefinition`s and dispatched to the plugin's `CommandExecutor`. Aliases and permission nodes are read from the yml. |
| Permission defaults, children, and wildcard overrides | Implemented | Prototype | The HyperCore permission model is used; the `plugin.yml` `permissions` block is parsed and registered recursively, including child permission inheritance. |
| Prioritized cancellable events | Implemented | Prototype | A full `org.bukkit.event.*` skeleton is generated; Bukkit events can be fired/listened and are bridged bidirectionally with the HyperCore event bus for selected types. |
| Sync next-tick tasks | Implemented | Prototype | `Bukkit.getScheduler().runTask(...)` delegates to the HyperCore tick scheduler. Tasks execute on the server tick caller. |
| Sync delayed and repeating tasks | Implemented | Prototype | Delay and period use server ticks. A zero delay means the next tick. |
| Async immediate, delayed, and repeating tasks | Implemented | Prototype | Delegated to the bounded HyperCore worker pool; must not mutate server-owned world state. |
| Task ownership and disable cleanup | Implemented | Implemented | Pending tasks are cancelled when their plugin fails or disables. Already-running async work is not interrupted. |
| External JAR discovery | Implemented | Prototype | Both `hypercore-plugin.json` and `plugin.yml` JARs are scanned from `plugins/`; `hypercore-plugin.json` takes precedence when both are present. |
| Per-plugin class-loader isolation | Implemented | Implemented | Child-first loaders with protected server/API namespaces delegated to the parent. `org.bukkit.*` resolves from the parent. |
| Hard and soft dependency ordering | Implemented | Prototype | `plugin.yml` `depend`/`softdepend` are translated and ordered alongside HyperCore SPI dependencies. |
| `plugin.yml` discovery | Implemented | Prototype | Parsed by `BukkitPluginYmlParser` into an `ExternalPluginDescriptor` plus a commands map. |
| `org.bukkit.*` namespace | N/A | Prototype | Minimal stubs (`JavaPlugin`, `Server`, `Bukkit`, `PluginCommand`, `CommandSender`, `BukkitScheduler`, `FileConfiguration`) are shipped in `:core`. Not binary-compatible with real Bukkit/Paper. |
| Tab completion | Implemented | Prototype | `TabCompleter` is wired through `CommandDefinition` and integrated with Brigadier suggestions on Forge/Fabric. |
| Cross-plugin lookups | N/A | Prototype | `PluginManager` indexes plugins by display name; `HyperCoreBukkitPluginManager.getPlugin(name)` returns the wrapped `JavaPlugin`. |
| Paper-only API and Folia scheduler API | Not supported | Not supported | No compatibility claim is made. |

## Forge and Runtime

| Capability | Status | Notes |
| --- | --- | --- |
| Forge 1.21.1 dedicated-server loading | Verified | Automated with `runGameTestServer`. |
| Fabric 1.21.1 dedicated-server loading | Verified | Automated with `runGameTestServer`. |
| Forge mod registry and event ownership | Preserved | HyperCore is a Forge mod and does not replace Forge lifecycle ownership. |
| Fabric mod registry and event ownership | Preserved | HyperCore is a Fabric mod and does not replace Fabric lifecycle ownership. |
| Vanilla/Forge world simulation on multiple threads | Not supported | Region ownership remains a message-passing prototype; world mutation stays on its existing threads. |
| GPU spatial query backend | Implemented | Vulkan is enabled by default with correctness self-tests and CPU fallback. |
| GPU entity, chunk, block, or world simulation | Not supported | No such performance or compatibility claim is made. |

## Adapter Gates

The Bukkit/Paper adapter is currently a `Prototype`. It will not be marked `Compatible` until it has:

1. ~~A versioned Bukkit-facing API namespace and `plugin.yml` descriptor translator.~~ Done — `BukkitPluginYmlParser` translates `plugin.yml` into the HyperCore SPI descriptor.
2. ~~Bukkit-aware class loading and deterministic dependency ordering beyond the implemented HyperCore SPI loader.~~ Done — `plugin.yml` JARs use the same child-first loader and dependency ordering as HyperCore SPI plugins.
3. Scheduler, command, permission, and event conformance tests against selected reference plugins. Partial — scheduler, command, lifecycle, tab completion, permission children, and cross-plugin lookup paths are unit-tested with an `ExampleBukkitPlugin` fixture; event and permission conformance against real reference plugins is pending.
4. A per-plugin test matrix covering startup, reload rejection, shutdown cleanup, and Forge/Fabric mod coexistence. Partial — startup, command dispatch, scheduling, shutdown cleanup, and cross-plugin lookup are covered; reload rejection and Forge/Fabric mod coexistence are pending.
5. Dedicated-server GameTests and end-to-end behavior checks without unsupported thread access. Done — both Forge and Fabric `runGameTestServer` load a dedicated test Bukkit plugin from `run/plugins` and verify its command executes in a real server.

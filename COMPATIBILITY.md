# HyperCore Compatibility Matrix

This matrix describes tested behavior, not aspirational API coverage. `HyperCore SPI` means the internal `dev.hypercore.plugin` contract. It does not imply Bukkit or Paper binary compatibility.

## Plugin API

| Capability | HyperCore SPI | Bukkit/Paper plugin JARs | Notes |
| --- | --- | --- | --- |
| Load, enable, disable lifecycle | Implemented | Not supported | Lifecycle failures are isolated and owned registrations are cleaned up. |
| Commands and aliases | Implemented | Not supported | Commands are bridged into Forge Brigadier registration. |
| Permission defaults and wildcard overrides | Implemented | Not supported | The permission model is deliberately smaller than Bukkit's graph. |
| Prioritized cancellable events | Implemented | Not supported | Only HyperCore event types are available. Forge and Bukkit event catalogs are not mapped. |
| Sync next-tick tasks | Implemented | Not supported | Tasks execute on the server tick caller and are included in tick timing. |
| Sync delayed and repeating tasks | Implemented | Not supported | Delay and period use server ticks. A zero delay means the next tick. |
| Async immediate, delayed, and repeating tasks | Implemented | Not supported | Async work uses the bounded HyperCore worker pool and must not mutate world state. |
| Task ownership and disable cleanup | Implemented | Not supported | Pending tasks are cancelled when their plugin fails or disables. Already-running async work is not interrupted. |
| `plugin.yml` discovery | Not supported | Not supported | External plugin JAR scanning and class loading are not implemented. |
| `org.bukkit.*` namespace | Not supported | Not supported | A separately versioned adapter is required. |
| Paper-only API and Folia scheduler API | Not supported | Not supported | No compatibility claim is made. |

## Forge and Runtime

| Capability | Status | Notes |
| --- | --- | --- |
| Forge 1.21.1 dedicated-server loading | Verified | Automated with `runGameTestServer`. |
| Forge mod registry and event ownership | Preserved | HyperCore is a Forge mod and does not replace Forge lifecycle ownership. |
| Vanilla/Forge world simulation on multiple threads | Not supported | Region ownership remains a message-passing prototype; world mutation stays on its existing threads. |
| GPU spatial query backend | Implemented | Vulkan is enabled by default with correctness self-tests and CPU fallback. |
| GPU entity, chunk, block, or world simulation | Not supported | No such performance or compatibility claim is made. |

## Adapter Gates

A Bukkit/Paper adapter will not be marked compatible until it has:

1. A versioned API namespace and descriptor loader.
2. Class-loader isolation and deterministic dependency ordering.
3. Scheduler, command, permission, and event conformance tests against selected reference plugins.
4. A per-plugin test matrix covering startup, reload rejection, shutdown cleanup, and Forge mod coexistence.
5. Dedicated-server GameTests and end-to-end behavior checks without unsupported thread access.

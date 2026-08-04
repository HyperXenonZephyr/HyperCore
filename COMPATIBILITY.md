# HyperCore Compatibility Matrix

This matrix describes tested behavior, not aspirational API coverage. `HyperCore SPI` means the internal `dev.hypercore.plugin` contract. `Beta` means a Bukkit/Paper JAR path is implemented and validated in dedicated-server GameTests under both Forge and Fabric, but full binary compatibility with the production Bukkit/Paper API is not yet claimed. `Prototype` means a path exists and is unit-tested but has not yet been validated end-to-end in dedicated-server GameTests.

## Plugin API

| Capability | HyperCore SPI | Bukkit/Paper plugin JARs | Notes |
| --- | --- | --- | --- |
| Load, enable, disable lifecycle | Implemented | Beta | `JavaPlugin` lifecycle is bridged through `BukkitPluginAdapter`; lifecycle failures are isolated and owned registrations are cleaned up. |
| Commands and aliases | Implemented | Beta | `plugin.yml` commands are translated into HyperCore `CommandDefinition`s and dispatched to the plugin's `CommandExecutor`. Aliases and permission nodes are read from the yml. |
| Permission defaults, children, and wildcard overrides | Implemented | Beta | The HyperCore permission model is used; the `plugin.yml` `permissions` block is parsed and registered recursively, including child permission inheritance. |
| Prioritized cancellable events | Implemented | Beta | A full `org.bukkit.event.*` skeleton is generated; Bukkit events can be fired/listened and are bridged bidirectionally with the HyperCore event bus for selected types (`BlockPlaceEvent`, `BlockBreakEvent`, `EntitySpawnEvent`, `PlayerMoveEvent`, `PlayerJoinEvent`, `PlayerInteractEvent`, `EntityDamageEvent`, `PlayerLoginEvent`, `PlayerQuitEvent`). |
| Sync next-tick tasks | Implemented | Beta | `Bukkit.getScheduler().runTask(...)` delegates to the HyperCore tick scheduler. Tasks execute on the server tick caller. |
| Sync delayed and repeating tasks | Implemented | Beta | Delay and period use server ticks. A zero delay means the next tick. |
| Async immediate, delayed, and repeating tasks | Implemented | Beta | Delegated to the bounded HyperCore worker pool; must not mutate server-owned world state. |
| Task ownership and disable cleanup | Implemented | Implemented | Pending tasks are cancelled when their plugin fails or disables. Already-running async work is not interrupted. |
| External JAR discovery | Implemented | Beta | Both `hypercore-plugin.json` and `plugin.yml` JARs are scanned from `plugins/`; `hypercore-plugin.json` takes precedence when both are present. |
| Per-plugin class-loader isolation | Implemented | Implemented | Child-first loaders with protected server/API namespaces delegated to the parent. `org.bukkit.*` resolves from the parent. |
| Hard and soft dependency ordering | Implemented | Beta | `plugin.yml` `depend`/`softdepend` are translated and ordered alongside HyperCore SPI dependencies. |
| `plugin.yml` discovery | Implemented | Beta | Parsed by `BukkitPluginYmlParser` into an `ExternalPluginDescriptor` plus a commands map. |
| `org.bukkit.*` namespace | N/A | Beta | Core Bukkit classes (`JavaPlugin`, `Server`, `Bukkit`, `World`, `Block`, `BlockState`, `Entity`, `Player`, `PluginCommand`, `CommandSender`, `BukkitScheduler`, `FileConfiguration`) are implemented in `:core`. Not all Bukkit/Paper types are present; full binary compatibility is not claimed. |
| Tab completion | Implemented | Beta | `TabCompleter` is wired through `CommandDefinition` and integrated with Brigadier suggestions on Forge/Fabric. |
| Cross-plugin lookups | N/A | Beta | `PluginManager` indexes plugins by display name; `HyperCoreBukkitPluginManager.getPlugin(name)` returns the wrapped `JavaPlugin`. |
| `World.getBlockAt`, `Block.getType`, `Block.setType` | N/A | Beta | Backed by `RegionExecutionService` and `WorldAccess`; mutations run under the region lock. Verified in Forge/Fabric GameTests. |
| `Block.getState`, `BlockState.setType`, `BlockState.update` | N/A | Beta | Snapshot writes back through the originating `HyperCoreBlock`; verified in Forge/Fabric GameTests. |
| `World.spawnEntity`, `World.getEntities`, `Entity.teleport`, `Entity.setCustomName` | N/A | Beta | Spawn, same-region teleport, and custom-name changes run under the region lock; `getEntities` delegates to the loaded `ServerLevel`. Verified in Forge/Fabric GameTests with zombies. |
| `LivingEntity.damage` and entity death | N/A | Beta | Damage events are fired through the region lock; cancellation aborts the damage. Verified for `EntityDamageEvent` bridging. |
| Player display name, game mode, and teleport | N/A | Beta | `Player.setDisplayName`, `Player.getGameMode`/`setGameMode`, and `Player.teleport` delegate to `RegionExecutionService`/`WorldAccess`. Verified in Forge/Fabric GameTests. |
| Block-entity inventory (`BlockState.getInventory`) and `Inventory` set/read | N/A | Beta | Backed by loader-specific container wrappers (`ForgeContainerInventory`, `FabricContainerInventory`); verified with chests in Forge/Fabric GameTests. |
| `WorldCreator` / `Server.createWorld` | N/A | Beta | Maps Bukkit world creation requests to already-loaded vanilla dimensions; HyperCore core does not implement custom terrain generation. Verified in Forge/Fabric GameTests. |
| `ItemStack` amount, durability, and material mapping | N/A | Beta | `ForgeItemStack`/`FabricItemStack` wrap native `ItemStack` and preserve NBT; not all item meta APIs are implemented. |
| `ItemStack` item meta (display name, lore, enchantments) | N/A | Beta | `HyperCoreItemMeta` stores display name, lore, and enchantments; `ForgeItemStack`/`FabricItemStack` synchronize display name and lore with native `DataComponents` and map Bukkit enchantments to native enchantment holders. Verified in Forge/Fabric GameTests. |
| Player inventory, armor contents, and held slot | N/A | Beta | `PlayerInventory` delegates to loader-specific player inventory wrappers; armor contents, off-hand, and held slot are wired through `WorldAccess`. Verified in Forge/Fabric GameTests. |
| Entity properties (velocity, fall distance, fire ticks, passengers) | N/A | Beta | Backed by `RegionExecutionService` and `WorldAccess`; mutations run under the region lock. Verified in Forge/Fabric GameTests. |
| Living entity properties (health, max health, AI, collidable) | N/A | Beta | `HyperCoreLivingEntity` delegates to `RegionExecutionService`/`WorldAccess`; health and max-health modify `LivingEntity` health and the `MAX_HEALTH` attribute, AI toggles `Mob.setNoAi`, and collidable reads `LivingEntity.isPushable`. Verified in Forge/Fabric GameTests. |
| Player exclusive API (kick, title, sprint/sneak, resource pack, command, inventory update) | N/A | Beta | `Player.kickPlayer`, `sendTitle`/`resetTitle`, `performCommand`, `updateInventory`, `openInventory`, `setResourcePack`, `isSneaking`/`setSneaking`, and `isSprinting`/`setSprinting` delegate to `RegionExecutionService`/`WorldAccess`. Forge/Fabric adapters dispatch network packets and update native player state. Verified in Forge/Fabric GameTests. |
| Paper-only API and Folia scheduler API | Not supported | Not supported | No compatibility claim is made. |

## Forge and Runtime

| Capability | Status | Notes |
| --- | --- | --- |
| Forge 1.21.1 dedicated-server loading | Verified | Automated with `runGameTestServer`. |
| Fabric 1.21.1 dedicated-server loading | Verified | Automated with `runGameTestServer`. |
| Forge mod registry and event ownership | Preserved | HyperCore is a Forge mod and does not replace Forge lifecycle ownership. |
| Fabric mod registry and event ownership | Preserved | HyperCore is a Fabric mod and does not replace Fabric lifecycle ownership. |
| Vanilla/Forge world simulation on multiple threads | Prototype | `RegionTaskCoordinator` dispatches per-owner region ticks across the HyperCore worker pool while each region is serialized under its write lock; direct Minecraft world mutation from worker threads is not supported. |
| GPU spatial query backend | Implemented | Vulkan is enabled by default with correctness self-tests and CPU fallback. |
| GPU entity, chunk, block, or world simulation | Not supported | No such performance or compatibility claim is made. |

## Orchestrated coexistence (simultaneous Forge and Fabric)

Simultaneous Forge and Fabric mod execution is implemented as an orchestrated dual-server deployment: a `:core`-based Orchestrator launches one Forge host and one Fabric host as child JVMs and keeps their worlds consistent through a cross-process world-state bridge.

| Capability | Status | Notes |
| --- | --- | --- |
| Orchestrator process launch and host monitoring | Implemented | `OrchestratorRuntime` starts both hosts, detects readiness from stdout markers, and shuts hosts down cleanly. `ProcessLauncher` detects the Forge/Fabric `run.bat`/`run.sh` scripts in each host directory and generates a wrapper that injects HyperCore role/IPC port/vector flags through `JAVA_TOOL_OPTIONS`; it falls back to a direct JVM command only for tests and mock hosts. |
| Versioned IPC bridge (handshake, heartbeat, ack) | Implemented | Length-prefixed binary frames over TCP; `HandshakePacket` negotiates protocol and Minecraft version; heartbeat echo measures latency. `BridgeEndpoint.close()` joins the reader thread and `OrchestratorBridgeServer.start()` is idempotent. Verified by `BridgeIntegrationTest`. |
| World-state dual-write with conflict arbitration | Implemented | Host mutations captured by `RegionExecutionService` are shipped as binary deltas; the orchestrator orders them on a logical timeline and resolves conflicts (Forge wins on equal block targets; spawn owner is authoritative for entities and only its moves/removes are accepted; connected host is authoritative for players and only its state/inventory updates are accepted). `BridgeCoordinator.broadcast()` sends each ordered batch only to the peer host. |
| Block/entity delta mirroring | Implemented | Block changes, entity spawn/move/remove, and player state/inventory slot updates are mirrored; chunk loading is forced so ungenerated areas stay consistent. |
| Cross-host commands | Implemented | Plugin command registries are snapshotted and mirrored under `xforge_*`/`xfabric_*` prefixes; executions are forwarded and results delivered to the original sender. |
| Cross-host event cancellation | Implemented | `BlockBreakEvent`/`BlockPlaceEvent` cancellations propagate both ways with mirror suppression, so a veto on one host is observed (and reflected) on the other. |
| Player ownership tracking | Implemented | Join/quit announcements update both local `PlayerProxy` state and orchestrator-side `WorldStateBridge` ownership, so conflict resolution and local views agree on which host owns each player. |
| Distribution packaging | Implemented | `:core:assembleDistribution` produces the orchestrator JAR, Forge/Fabric host templates, launch scripts, and README documenting the required server installation step. |
| Coexistence GameTests | Implemented | `crossProcessBlockSync`, `crossProcessEntityMove`, `crossProcessCommandExecution`, and `crossProcessEventPropagation` are wired for both Forge and Fabric hosts and gate on bridge mode; they are intended for the orchestrated CI job and are skipped (succeed immediately) in standalone GameTest runs. |
| Arbitrary-mod guarantees | Limited | Arbitrary mods are supported when they access state through HyperCore/Bukkit APIs or their own host's vanilla simulation; direct cross-host internal state peeking is not guaranteed. |
| Authority-Mirror latency | Beta | The bridge tick budget is configurable (`hypercore.bridge.tickMillis`, default 50 ms); `/hypercore bridge status` reports measured latency, delta counts, and dropped deltas. |

## Adapter Gates

The Bukkit/Paper adapter is currently a `Beta`. It will not be marked `Compatible` until it has:

1. ~~A versioned Bukkit-facing API namespace and `plugin.yml` descriptor translator.~~ Done — `BukkitPluginYmlParser` translates `plugin.yml` into the HyperCore SPI descriptor.
2. ~~Bukkit-aware class loading and deterministic dependency ordering beyond the implemented HyperCore SPI loader.~~ Done — `plugin.yml` JARs use the same child-first loader and dependency ordering as HyperCore SPI plugins.
3. Scheduler, command, permission, and event conformance tests against selected reference plugins. Partial — scheduler, command, lifecycle, tab completion, permission children, and cross-plugin lookup paths are unit-tested with an `ExampleBukkitPlugin` fixture; event and permission conformance against real reference plugins is pending.
4. A per-plugin test matrix covering startup, reload rejection, shutdown cleanup, and Forge/Fabric mod coexistence. Partial — startup, command dispatch, scheduling, shutdown cleanup, cross-plugin lookup, world block read/write, `BlockState` update, entity spawn, entity teleport, custom name, player display name/game mode/teleport, block-entity inventory, Bukkit event bridge, `plugin.yml` permission registration, `WorldCreator`, and region-parallel execution are covered; reload rejection is pending.
5. Dedicated-server GameTests and end-to-end behavior checks without unsupported thread access. Done — both Forge and Fabric `runGameTestServer` load a dedicated test Bukkit plugin from `run/plugins` and verify commands, block/entity/inventory/player APIs, event bridging, permission registration, world creation, multi-owner region ticks, and cross-host bridge wiring in a real server.
6. World, block, block-entity, inventory, entity, and player mutation API conformance. Done — `World.getBlockAt`, `Block.getType`/`setType`, `BlockState.update` (including `getInventory` for chests), `World.spawnEntity`, `World.getEntities`, `Entity.teleport`, `Entity.setCustomName`, `LivingEntity.damage`, `Player.setDisplayName`, `Player.getGameMode`/`setGameMode`, `Player.teleport`, `Inventory` set/read, `ItemStack` amount/durability/item meta (display name, lore, enchantments), player inventory/armor/held slot, `Entity` properties (velocity, fall distance, fire ticks, passengers), `LivingEntity` properties (health, max health, AI, collidable), `Player.kickPlayer`, `Player.sendTitle`/`resetTitle`, `Player.performCommand`, `Player.updateInventory`, `Player.openInventory`, `Player.setResourcePack`, `Player.isSneaking`/`setSneaking`, `Player.isSprinting`/`setSprinting`, `WorldCreator`/`Server.createWorld`, and `plugin.yml` permission/child registration are implemented and verified in Forge/Fabric GameTests; advanced entity APIs such as complex AI goals, attribute modifiers, and physics overrides are not supported.
7. Bidirectional Bukkit event bridge for world/block/entity/player events. Done — `BlockPlaceEvent`, `BlockBreakEvent`, `EntitySpawnEvent`, `PlayerMoveEvent`, `PlayerJoinEvent`, `PlayerInteractEvent`, `EntityDamageEvent`, `PlayerLoginEvent`, and `PlayerQuitEvent` are bridged and cancellation is propagated back to abort HyperCore mutations; verified in Forge/Fabric GameTests.
8. True multi-core region-parallel execution. Done — `RegionTaskCoordinator` groups active regions by logical owner lane and dispatches owner batches across the HyperCore worker pool; each region remains serialized under its write lock. Verified in Forge/Fabric `regionParallelExecution` GameTests.
9. JMH/Gradle benchmark suite for region-parallel execution. Done — `benchmarkRegionParallel` measures region-tick scheduling throughput across 1/2/4/8/16 active regions and writes a Markdown report.

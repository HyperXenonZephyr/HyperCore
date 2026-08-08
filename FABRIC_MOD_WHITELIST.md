# HyperCore Fabric Mod Whitelist

HyperCore runs Forge and Fabric in an orchestrated dual-server deployment. Forge mod support is unrestricted. Fabric mod support is intentionally scoped: only performance-measurement and logic-adjustment mods that **add no content** (items, blocks, entities, or other registry entries) and that have been **verified non-conflicting** by the HyperCore team are permitted on the Fabric host.

## Why a whitelist

Single-process Forge+Fabric coexistence is architecturally infeasible, so the Fabric host runs in its own JVM under the Orchestrator. Because the Fabric host participates in a shared world timeline through the cross-process world-state bridge, an unverified Fabric mod can corrupt that timeline by:

- Registering content (items/blocks/entities) that has no Forge counterpart and cannot be mirrored.
- Applying Mixin transforms that conflict with HyperCore's assumptions or with mirrored state.
- Mutating world state off the server thread or outside the captured `RegionExecutionService` path.

To protect the dual-server world-state bridge, the Fabric host validates loaded mods against this whitelist at startup. Mods not on the whitelist are reported; the host can be configured to refuse startup when non-whitelisted mods are present.

> [!IMPORTANT]
> Fabric mods are loaded by the Fabric Loader **before** HyperCore's entrypoint runs. The whitelist cannot prevent a mod from loading — it detects non-whitelisted mods after the fact and reacts (log + optionally abort). True build-time gating is enforced by the distribution assembler, which only stages whitelisted mod JARs into the Fabric host template's `mods/` directory.

## Inclusion criteria

A Fabric mod is added to the whitelist only after **all** of the following are true:

1. **No content addition** — the mod adds no items, blocks, block entities, entities, effects, enchantments, potions, villagers, or other registry entries. Mods whose purpose is performance telemetry, tick profiling, logic tweaks, or server-side optimization qualify; content mods do not.
2. **Manual compatibility test** — the mod has been run on the Fabric host alongside HyperCore and the dual-server bridge, and no crash, state desync, or conflict was observed.
3. **No harmful Mixins** — the mod's Mixins do not target classes HyperCore depends on for world-state capture or bridge mirroring, and do not alter server-thread affinity assumptions.
4. **Entry recorded below** — the mod id, version range, test date, and tester are recorded in the whitelist table.

## Whitelist

> The whitelist is empty until mods are tested. Do not add entries speculatively.

| Mod id | Name | Version range | Adds content | Tested with | Test date | Tester | Notes |
| --- | --- | --- | :---: | --- | --- | --- | --- |
| _(none yet)_ | | | | | | | |

## Whitelist file format

At runtime the Fabric host reads `config/fabric-mod-whitelist.txt`. Each non-empty, non-comment line is a mod id (lowercase, matching `fabric.mod.json` `id`). Lines starting with `#` are comments.

```
# HyperCore Fabric mod whitelist
# One mod id per line. Only mods listed here are permitted on the Fabric host.
# Example:
# lithium
# smoothboot
```

When the file is absent, the whitelist is empty and the Fabric host reports all non-vanilla mods as non-whitelisted. The core infrastructure mods below are always allowed and do not need to be listed:

- `fabricloader`
- `fabric-api` (and its sub-modules, matched by the `fabric-` prefix)
- `minecraft`
- `java`
- `hypercore`

## Configuration

| Key | Default | Purpose |
| --- | ---: | --- |
| `fabric.modWhitelistFile` | `config/fabric-mod-whitelist.txt` | Path to the whitelist file. |
| `fabric.enforceModWhitelist` | `true` | When `true`, the Fabric host refuses to start if any non-whitelisted mod is loaded. When `false`, non-whitelisted mods are logged as warnings but startup continues. |

## Adding a mod

1. Run the candidate mod on the Fabric host with HyperCore and the dual-server bridge in a development environment.
2. Verify no content is added (check registries/logs), no crashes, and no world-state desync across the bridge.
3. Add the mod id to `config/fabric-mod-whitelist.txt`.
4. Add a row to the table above with the test evidence.
5. Commit both changes together.

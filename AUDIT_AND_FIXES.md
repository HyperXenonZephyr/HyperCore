# HyperCore 代码审查与修复报告

## 区块加载慢的根本原因

打包测试后区块加载慢，主要不是因为代码直接拦截了区块加载，而是 **每 tick 的实体查找成了 O(n²) 瓶颈**：

- `RegionExecutionService.activeRegions()` 每个 server tick 会遍历所有实体。
- 对每个实体又要调用 `WorldAccess.getEntityPosition(uuid)`。
- `FabricWorldAccess.findEntity()` 和 `ForgeWorldAccess.findEntity()` 每次都线性扫描 `level.getAllEntities()`。
- 结果：实体数 n 时，每 tick 产生 O(n²) 次操作；服务器线程被拖住，区块加载/生成随之卡顿。

## 已修复的 Bug 与矛盾

### 1. 实体查找 O(n²) 性能灾难（核心）
**文件**：
- `fabric/src/main/java/dev/hypercore/world/FabricWorldAccess.java`
- `forge/src/main/java/dev/hypercore/world/ForgeWorldAccess.java`

**修复**：新增 UUID → Entity 缓存。
- `findEntity(UUID)` 先查缓存，命中直接返回 O(1)。
- 未命中或缓存实体已被移除时，重建一次缓存。
- `spawnEntity` 同步路径直接加入缓存；异步路径标记缓存失效。
- `removeEntity` 同步路径直接移除；异步路径标记缓存失效。
- `entityIds()` 重建缓存后返回 keySet，使 `activeRegions()` 从 O(n²) 降到 O(n)。

### 2. 未知世界名导致 NullPointerException
**文件**：
- `fabric/src/main/java/dev/hypercore/world/FabricWorldAccessFactory.java`
- `forge/src/main/java/dev/hypercore/world/ForgeWorldAccessFactory.java`

**修复**：`access(String)` 原来用 `HashMap.computeIfAbsent(..., this::createAccess)`，而 `createAccess` 对世界不存在时返回 `null`，会抛 NPE。改为显式判空的安全查找。

### 3. ResourceKey 用 `==` 比较
**文件**：
- `fabric/src/main/java/dev/hypercore/world/FabricWorldAccessFactory.java`
- `forge/src/main/java/dev/hypercore/world/ForgeWorldAccessFactory.java`
- `forge/src/main/java/dev/hypercore/world/ForgeWorldStateApplier.java`

**修复**：将 `level.dimension() == Level.OVERWORLD` 等改为 `Level.OVERWORLD.equals(...)`，避免依赖 ResourceKey 实例被 intern 的假设。

### 4. 远程状态应用器同样线性扫描实体
**文件**：
- `fabric/src/main/java/dev/hypercore/world/FabricWorldStateApplier.java`
- `forge/src/main/java/dev/hypercore/world/ForgeWorldStateApplier.java`

**修复**：在 `apply()` 处理批次时构建一次 UUID → Entity 缓存，批次内所有 delta 复用该缓存，避免每个 delta 都全量扫描。

### 5. 工作树中已存在的两项修复（已验证）
- `core/src/main/java/dev/hypercore/bukkit/BukkitPluginAdapter.java`：`plugin.yml` 的 `NOT_OP` 权限默认值之前被错误映射到 `TRUE`，已修正为 `NOT_OP`。
- `core/src/main/java/dev/hypercore/plugin/PluginContext.java`：注册命令时会保留 tab completer，之前被丢弃。

## 未实现的遗留项

- `WorldAccessFactory.loadedRegions()` 在 Fabric/Forge 工厂中仍返回空列表。当前 region tick 任务是空实现，因此不影响功能；Minecraft 1.21.1 没有稳定的公开 API 可遍历已加载区块（ChunkMap 相关方法为非 public），若后续 region tick 需要覆盖已加载区块，需通过 mixin 或反射实现。

## 验证结果

```bash
./gradlew :core:test :fabric:compileJava :forge:compileJava   # 通过
./gradlew :core:build :forge:build :fabric:build              # 通过
```

## 改动文件清单

```
core/src/main/java/dev/hypercore/bukkit/BukkitPluginAdapter.java     (预存在)
core/src/main/java/dev/hypercore/plugin/PluginContext.java           (预存在)
fabric/src/main/java/dev/hypercore/world/FabricWorldAccess.java
fabric/src/main/java/dev/hypercore/world/FabricWorldAccessFactory.java
fabric/src/main/java/dev/hypercore/world/FabricWorldStateApplier.java
forge/src/main/java/dev/hypercore/world/ForgeWorldAccess.java
forge/src/main/java/dev/hypercore/world/ForgeWorldAccessFactory.java
forge/src/main/java/dev/hypercore/world/ForgeWorldStateApplier.java
```

## 建议后续操作

1. 重新打包并在相同场景下测试区块加载速度。
2. 若仍慢，打开服务端 `/hypercore timings` 观察 tick 时间是否下降。
3. 考虑是否需要为 `loadedRegions()` 添加 mixin 以覆盖已加载区块。

---

# 独立服务端补丁复审记录（server-paperweight，2026-08-24）

本轮针对 `paper-server/patches/sources` 下的独立服务端补丁（region 变更应用边界）进行审查。
三项审查问题已修正，目标 Mojang API 已逐个对照 26.1 Mache 反编译工作树核对存在；
最终完整离线重建通过后，最终复审又发现并修正了第四项缺陷。

## 审查问题与修正

### 1. Worker 输出缺少服务器线程应用边界
**文件**：`dev/hypercore/server/region/ServerRegionScheduler.java.patch`

原补丁只做快照规划与 region pass 观测，worker 产生的 mutation 没有任何落点。修正：
- 新增 `ServerMutationQueue`：并发安全入队（`ConcurrentLinkedQueue`），`applyAtTickBoundary`
  强制服务器线程（线程不匹配抛 `IllegalStateException`），整批取出后顺序应用。
- `ServerRegionScheduler.afterLevelTicks` 在 tick 边界统一排空队列；
  `enqueueComputation` 提供"纯 worker 计算 + 值型输出"入口。

### 2. 不可信 worker 数据未校验
**文件**：`ServerRegionScheduler.java.patch`、`RegionMutation.java.patch`

worker 返回值是不可信数据，不得中断 tick 或破坏世界状态。修正：
- `RegionMutation` record 规范化为不可变副本，operation/键/值强制非空白。
- `applyMutation("set-block")` 依次校验：region 归属回查（`x>>4,z>>4` 必须映射回目标
  region）、世界边界 `isInWorldBounds`、区块已加载 `hasChunk(x>>4,z>>4)`、
  方块 ID 经 `Identifier.tryParse` 判空；全部包裹在捕获 `IllegalArgumentException` 的
  try 块中，畸形数据被静默丢弃而非中止 tick。

### 3. 跨区域 mutation 逃逸
**文件**：`ServerRegionScheduler.java.patch`

`enqueueComputation` 现在拒绝任务对非自身 region 发出 mutation
（"A region task may only emit mutations for its own region"），
保证 lane 所有权模型不被绕过。

### 4. 最终复审新发现：方块注册表判空是死代码（已修正）
**文件**：`ServerRegionScheduler.java.patch`

原代码用 `BuiltInRegistries.BLOCK.getValue(blockId)` 后判 `null`，但
`BuiltInRegistries.BLOCK` 是 `DefaultedRegistry<Block>`（BuiltInRegistries.java:180），
其实现 `DefaultedMappedRegistry.getValue` 对未知 ID **返回默认值（空气）而非 null**
（`net/minecraft/core/DefaultedMappedRegistry.java:44-47`，覆盖方法标注 `@NonNull`）。
后果：非法方块 ID 会静默地把现有方块替换成空气，且 null 检查永远不触发。
修正：改用 `getOptional(blockId).orElse(null)`（未知 ID 返回空 Optional，
`net/minecraft/core/Registry.java:73`），非法 ID 现在被正确拒绝。

## 目标 Mojang API 存在性核对（26.1 Mache 工作树）

| API | 位置 | 备注 |
| --- | --- | --- |
| `Identifier.tryParse(String)` → `@Nullable Identifier` | `net/minecraft/resources/Identifier.java:53` | |
| `Registry.getOptional(Identifier)` → `Optional<T>` | `net/minecraft/core/Registry.java:73`；`DefaultedMappedRegistry.java:50` 覆盖 | 未知 ID 返回空 |
| `BuiltInRegistries.BLOCK` = `DefaultedRegistry<Block>` | `net/minecraft/core/registries/BuiltInRegistries.java:180` | 默认值 air |
| `ServerChunkCache.hasChunk(int, int)` | `net/minecraft/server/level/ServerChunkCache.java:263` | |
| `Level.isInWorldBounds(BlockPos)` | `net/minecraft/world/level/Level.java:168` | |
| `Block.defaultBlockState()` | `net/minecraft/world/level/block/Block.java:463` | |
| `LevelWriter.setBlock(BlockPos, BlockState, int)` | `net/minecraft/world/level/LevelWriter.java:12` | 默认 updateLimit=512 |
| `ServerChunkCache.snapshotFullChunkPositions()` | `net/minecraft/server/level/ServerChunkCache.java:452` | |

## 最终完整离线重建证据

```powershell
# server-paperweight 下执行，两次均 BUILD SUCCESSFUL：
./gradlew.bat :hypercore-server:setupMacheSources `
  :hypercore-server:applyPatches :hypercore-server:rebuildPatches `
  :hypercore-server:createBundlerJar --offline --no-daemon
# 第一次（8m59s）：applied/rebuilt 各 2 补丁；Gradle 输入哈希证明源码与既有产物一致
# 第二次（8m37s，问题 4 修正后）：compileJava/jar/createBundlerJar 真实重跑
```

产物（2026-08-24 20:24）：

```text
hypercore-server/build/libs/hypercore-server-0.1.0-SNAPSHOT.jar
hypercore-server/build/libs/hypercore-server-bundler-0.1.0-SNAPSHOT.jar
```

补丁往返一致：`rebuildPatches` 重写后的补丁文本与工作树补丁完全相同（无漂移）。

## 测试结果

```text
dev.hypercore.server.RuntimeBoundaryTest            tests=6 failures=0 errors=0
dev.hypercore.server.region.RegionMailboxSchedulerTest tests=5 failures=0 errors=0
dev.hypercore.server.vulkan.VulkanComputeBackendProviderTest tests=5 failures=0 errors=0
```

`hypercore-vulkan-provider` 同时产出 `build/compute-providers/` 目录
（provider JAR + LWJGL 3.3.3 及 Windows natives）。

## 遗留边界

真实服务器启动、EULA 接受、世界加载与游戏 tick 冒烟测试仍未执行，
独立核心尚不能称为"可运行"；此为 README 中既有的验证边界，不在本轮范围内。

package dev.hypercore.world;

import dev.hypercore.bridge.event.EventProxy;
import dev.hypercore.bridge.world.BlockDelta;
import dev.hypercore.bridge.world.EntityMoveDelta;
import dev.hypercore.bridge.world.EntityRemoveDelta;
import dev.hypercore.bridge.world.EntitySpawnDelta;
import dev.hypercore.bridge.world.PlayerInventoryDelta;
import dev.hypercore.bridge.world.PlayerStateDelta;
import dev.hypercore.bridge.world.WorldDelta;
import dev.hypercore.bridge.world.WorldDeltaApplier;
import dev.hypercore.orchestrator.HyperCoreRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bukkit.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Forge implementation of {@link WorldDeltaApplier}.
 *
 * <p>Applies ordered deltas received from the remote host to the local
 * {@link ServerLevel}s. All Minecraft mutations are executed on the server
 * thread as required by the Minecraft threading model; the caller blocks until
 * the batch has been applied so ordering is preserved. Block deltas are skipped
 * when the remote host reported the corresponding event cancelled.
 */
public final class ForgeWorldStateApplier implements WorldDeltaApplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeWorldStateApplier.class);

    private final MinecraftServer server;
    private final EventProxy eventProxy;

    public ForgeWorldStateApplier(MinecraftServer server) {
        this(server, null);
    }

    public ForgeWorldStateApplier(MinecraftServer server, EventProxy eventProxy) {
        this.server = server;
        this.eventProxy = eventProxy;
    }

    @Override
    public void apply(HyperCoreRole source, List<WorldDelta> deltas) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        server.execute(() -> {
            try {
                for (WorldDelta delta : deltas) {
                    applyDelta(delta);
                }
                done.complete(null);
            } catch (RuntimeException error) {
                LOGGER.error("Failed to apply remote world delta batch from {}", source.displayName(), error);
                done.completeExceptionally(error);
            }
        });
        try {
            done.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while applying remote world deltas", error);
        } catch (java.util.concurrent.ExecutionException error) {
            throw new RuntimeException("Failed to apply remote world deltas", error.getCause());
        } catch (java.util.concurrent.TimeoutException error) {
            throw new RuntimeException("Timed out applying remote world deltas", error);
        }
    }

    private void applyDelta(WorldDelta delta) {
        switch (delta.typeId()) {
            case BlockDelta.TYPE_ID -> applyBlock((BlockDelta) delta);
            case EntitySpawnDelta.TYPE_ID -> applySpawn((EntitySpawnDelta) delta);
            case EntityMoveDelta.TYPE_ID -> applyMove((EntityMoveDelta) delta);
            case EntityRemoveDelta.TYPE_ID -> applyRemove((EntityRemoveDelta) delta);
            case PlayerStateDelta.TYPE_ID -> applyPlayerState((PlayerStateDelta) delta);
            case PlayerInventoryDelta.TYPE_ID -> applyPlayerInventory((PlayerInventoryDelta) delta);
            default -> LOGGER.warn("Ignoring unknown world delta type {}", delta.typeId());
        }
    }

    private void applyBlock(BlockDelta delta) {
        if (eventProxy != null && eventProxy.consumeBlockSuppression(delta.worldName(), delta.x(), delta.y(), delta.z())) {
            LOGGER.debug("Skipping mirrored block change cancelled by the remote host: {};{};{}", delta.x(), delta.y(), delta.z());
            return;
        }
        ServerLevel level = level(delta.worldName());
        if (level == null) {
            LOGGER.warn("Cannot mirror block change: world not loaded: {}", delta.worldName());
            return;
        }
        Material material = materialOf(delta.blockState());
        if (material == null) {
            LOGGER.warn("Cannot mirror block change: unmapped block state {}", delta.blockState());
            return;
        }
        // Force the chunk to exist so the mirror works in ungenerated areas.
        level.getChunk(delta.x() >> 4, delta.z() >> 4);
        level.setBlock(
            new BlockPos(delta.x(), delta.y(), delta.z()),
            ForgeWorldAccess.toBlock(material).defaultBlockState(),
            3
        );
    }

    private void applySpawn(EntitySpawnDelta delta) {
        ServerLevel level = level(delta.worldName());
        if (level == null) {
            LOGGER.warn("Cannot mirror entity spawn: world not loaded: {}", delta.worldName());
            return;
        }
        if (findEntity(level, delta.entityId()) != null) {
            return;
        }
        org.bukkit.entity.EntityType type = bukkitEntityType(delta.entityType());
        if (type == null) {
            return;
        }
        try {
            EntityType<?> mcType = ForgeWorldAccess.toEntityType(type);
            Entity entity = mcType.create(level);
            if (entity == null) {
                return;
            }
            level.getChunk((int) Math.floor(delta.x()) >> 4, (int) Math.floor(delta.z()) >> 4);
            entity.setPos(delta.x(), delta.y(), delta.z());
            level.addFreshEntity(entity);
        } catch (UnsupportedOperationException error) {
            LOGGER.debug("Cannot mirror entity spawn: {}", error.getMessage());
        }
    }

    private void applyMove(EntityMoveDelta delta) {
        ServerLevel level = level(delta.worldName());
        if (level == null) {
            return;
        }
        Entity entity = findEntity(level, delta.entityId());
        if (entity != null) {
            entity.teleportTo(delta.x(), delta.y(), delta.z());
        }
    }

    private void applyRemove(EntityRemoveDelta delta) {
        ServerLevel level = level(delta.worldName());
        if (level == null) {
            return;
        }
        Entity entity = findEntity(level, delta.entityId());
        if (entity != null) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    private void applyPlayerState(PlayerStateDelta delta) {
        ServerLevel level = level(delta.worldName());
        if (level == null) {
            return;
        }
        Entity entity = findEntity(level, delta.playerId());
        if (entity instanceof ServerPlayer player) {
            player.setHealth((float) delta.health());
            player.teleportTo(delta.x(), delta.y(), delta.z());
        }
    }

    private void applyPlayerInventory(PlayerInventoryDelta delta) {
        ServerLevel level = level(delta.worldName());
        if (level == null) {
            return;
        }
        Entity entity = findEntity(level, delta.playerId());
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = ItemStack.EMPTY;
        Material material = materialOf(delta.itemType());
        if (material != null) {
            try {
                stack = new ItemStack(ForgeWorldAccess.toItem(material), delta.amount());
            } catch (UnsupportedOperationException error) {
                LOGGER.debug("Cannot mirror inventory item: {}", error.getMessage());
                return;
            }
        }
        player.getInventory().setItem(delta.slot(), stack);
    }

    private ServerLevel level(String worldName) {
        for (ServerLevel level : server.getAllLevels()) {
            String dimensionName = level.dimension().location().toString();
            if (dimensionName.equals(worldName)
                || (level.dimension() == Level.OVERWORLD && ("overworld".equals(worldName) || "world".equals(worldName)))) {
                return level;
            }
        }
        return null;
    }

    private static Entity findEntity(ServerLevel level, UUID entityId) {
        for (Entity entity : level.getAllEntities()) {
            if (entity.getUUID().equals(entityId)) {
                return entity;
            }
        }
        return null;
    }

    private static org.bukkit.entity.EntityType bukkitEntityType(String name) {
        try {
            return org.bukkit.entity.EntityType.valueOf(name);
        } catch (IllegalArgumentException error) {
            LOGGER.debug("Unknown entity type name in delta: {}", name);
            return null;
        }
    }

    private static Material materialOf(String blockState) {
        if (blockState == null || blockState.isBlank()) {
            return null;
        }
        String candidate = blockState;
        int bracket = candidate.indexOf('[');
        if (bracket >= 0) {
            candidate = candidate.substring(0, bracket);
        }
        int brace = candidate.indexOf('{');
        if (brace >= 0) {
            candidate = candidate.substring(brace + 1);
        }
        int colon = candidate.indexOf(':');
        if (colon >= 0) {
            candidate = candidate.substring(colon + 1);
        }
        candidate = candidate.toUpperCase(Locale.ROOT).trim();
        try {
            return Material.valueOf(candidate);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}

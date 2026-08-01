package dev.hypercore.world;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * No-op factory used when no loader adapter has registered a real world access
 * implementation. All operations return empty defaults.
 */
public final class NoOpWorldAccessFactory implements WorldAccessFactory {

    @Override
    public WorldAccess access(String worldName) {
        return new NoOpWorldAccess(worldName);
    }

    @Override
    public Collection<String> worldNames() {
        return List.of();
    }

    private record NoOpWorldAccess(String worldName) implements WorldAccess {
        @Override
        public Material getBlockType(int x, int y, int z) {
            return Material.AIR;
        }

        @Override
        public void setBlockType(int x, int y, int z, Material type) {
            // No-op.
        }

        @Override
        public Inventory getBlockInventory(int x, int y, int z) {
            return null;
        }

        @Override
        public UUID spawnEntity(EntityType type, Position position) {
            return null;
        }

        @Override
        public Position getEntityPosition(UUID entityId) {
            return null;
        }

        @Override
        public boolean teleportEntity(UUID entityId, Position position) {
            return false;
        }

        @Override
        public Inventory getPlayerInventory(UUID playerId) {
            return null;
        }

        @Override
        public Collection<UUID> entityIds() {
            return Collections.emptyList();
        }
    }
}

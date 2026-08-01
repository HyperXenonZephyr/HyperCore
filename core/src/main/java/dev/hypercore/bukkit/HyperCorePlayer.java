package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.Permission;

import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit {@link Player} implementation backed by HyperCore's
 * {@link RegionExecutionService}.
 *
 * <p>This is a minimal view over an existing player entity identified by UUID.
 * It does not attempt to create a Minecraft player; callers obtain instances
 * through world or server lookups once the player is already present.
 */
public final class HyperCorePlayer extends HyperCoreEntity implements Player {
    private final String name;

    public HyperCorePlayer(RegionExecutionService execution, UUID playerId, String name) {
        super(execution, playerId);
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUniqueId() {
        return super.getUniqueId();
    }

    @Override
    public World getWorld() {
        Location location = getLocation();
        return location == null ? null : location.getWorld();
    }

    @Override
    public Location getLocation() {
        return super.getLocation();
    }

    @Override
    public PlayerInventory getInventory() {
        org.bukkit.inventory.Inventory inventory = execution.getPlayerInventory(getUniqueId());
        return inventory instanceof PlayerInventory playerInventory ? playerInventory : null;
    }

    @Override
    public void sendMessage(String message) {
        // No-op in this minimal stub: real implementation would route to the
        // player's network connection.
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public boolean hasPermission(String name) {
        return true;
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return true;
    }
}

package org.bukkit;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the expanded Bukkit API stubs introduced in Phase 0.
 *
 * <p>These tests verify default behavior, coordinate math, and container
 * contracts independent of the HyperCore adapter layer.
 */
class BukkitApiStubsTest {

    @Test
    void locationCapturesCoordinates() {
        World world = namedWorld("world");
        Location location = new Location(world, 1.5, 2.7, -3.2);

        assertEquals(world, location.getWorld());
        assertEquals(1, location.getBlockX());
        assertEquals(2, location.getBlockY());
        assertEquals(-4, location.getBlockZ());
        assertEquals(1.5, location.getX());
        assertEquals(2.7, location.getY());
        assertEquals(-3.2, location.getZ());
        assertEquals(0.0f, location.getYaw());
        assertEquals(0.0f, location.getPitch());
    }

    @Test
    void locationComputesBlockCoordinatesForNegativeValues() {
        Location location = new Location(null, -0.1, -1.0, -0.9);

        assertEquals(-1, location.getBlockX());
        assertEquals(-1, location.getBlockY());
        assertEquals(-1, location.getBlockZ());
    }

    @Test
    void itemStackTracksTypeAndAmount() {
        ItemStack stack = new ItemStack(Material.STONE, 16);

        assertEquals(Material.STONE, stack.getType());
        assertEquals(16, stack.getAmount());
        assertFalse(stack.isEmpty());

        stack.setAmount(0);
        assertTrue(stack.isEmpty());
    }

    @Test
    void itemStackCloneIsIndependent() {
        ItemStack original = new ItemStack(Material.DIAMOND, 5);
        ItemStack clone = original.clone();

        clone.setAmount(10);
        assertEquals(5, original.getAmount());
        assertEquals(10, clone.getAmount());
    }

    @Test
    void itemStackSimilarityComparesMaterialOnly() {
        ItemStack a = new ItemStack(Material.STONE, 1);
        ItemStack b = new ItemStack(Material.STONE, 64);
        ItemStack c = new ItemStack(Material.DIRT, 1);

        assertTrue(a.isSimilar(b));
        assertFalse(a.isSimilar(c));
        assertFalse(a.isSimilar(null));
    }

    @Test
    void defaultWorldGetEntitiesIsEmpty() {
        World world = namedWorld("empty");
        List<Entity> entities = world.getEntities();
        assertNotNull(entities);
        assertTrue(entities.isEmpty());
    }

    @Test
    void defaultInventoryAddItemAndRemoveItemAreSymmetric() {
        Inventory inventory = new Inventory() {
            private final ItemStack[] slots = new ItemStack[9];

            @Override
            public int getSize() {
                return slots.length;
            }

            @Override
            public ItemStack getItem(int index) {
                return slots[index];
            }

            @Override
            public void setItem(int index, ItemStack item) {
                slots[index] = item;
            }
        };

        ItemStack toAdd = new ItemStack(Material.STONE, 10);
        assertTrue(inventory.addItem(toAdd).isEmpty());
        assertEquals(Material.STONE, inventory.getItem(0).getType());
        assertEquals(10, inventory.getItem(0).getAmount());

        ItemStack toRemove = new ItemStack(Material.STONE, 3);
        assertTrue(inventory.removeItem(toRemove).isEmpty());
        assertEquals(7, inventory.getItem(0).getAmount());
    }

    @Test
    void blockStateDefaultUpdateForcesWrite() {
        Block block = new Block() {
            private Material type = Material.AIR;

            @Override
            public World getWorld() {
                return namedWorld("world");
            }

            @Override
            public int getX() {
                return 0;
            }

            @Override
            public int getY() {
                return 0;
            }

            @Override
            public int getZ() {
                return 0;
            }

            @Override
            public Material getType() {
                return type;
            }

            @Override
            public void setType(Material type) {
                this.type = type;
            }

            @Override
            public BlockState getState() {
                return null;
            }
        };

        BlockState state = new BlockState() {
            private Material capturedType = Material.AIR;
            private final Block capturedBlock = block;

            @Override
            public Block getBlock() {
                return capturedBlock;
            }

            @Override
            public World getWorld() {
                return capturedBlock.getWorld();
            }

            @Override
            public int getX() {
                return capturedBlock.getX();
            }

            @Override
            public int getY() {
                return capturedBlock.getY();
            }

            @Override
            public int getZ() {
                return capturedBlock.getZ();
            }

            @Override
            public Material getType() {
                return capturedType;
            }

            @Override
            public void setType(Material type) {
                this.capturedType = type;
            }

            @Override
            public boolean update() {
                capturedBlock.setType(capturedType);
                return true;
            }
        };

        state.setType(Material.STONE);
        assertTrue(state.update(true, false));
        assertEquals(Material.STONE, block.getType());
    }

    @Test
    void playerUniqueIdIsUuid() {
        Player player = new Player() {
            @Override
            public String getName() {
                return "tester";
            }

            @Override
            public UUID getUniqueId() {
                return UUID.randomUUID();
            }

            @Override
            public World getWorld() {
                return null;
            }

            @Override
            public Location getLocation() {
                return null;
            }

            @Override
            public boolean teleport(Location location) {
                return false;
            }

            @Override
            public org.bukkit.inventory.PlayerInventory getInventory() {
                return null;
            }

            @Override
            public void sendMessage(String message) {
            }

            @Override
            public void sendMessage(String[] messages) {
            }

            @Override
            public boolean hasPermission(String name) {
                return false;
            }

            @Override
            public boolean hasPermission(Permission perm) {
                return false;
            }
        };

        assertNotNull(player.getUniqueId());
        assertEquals("tester", player.getName());
    }

    private static World namedWorld(String name) {
        return new World() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public org.bukkit.block.Block getBlockAt(int x, int y, int z) {
                return null;
            }

            @Override
            public List<org.bukkit.entity.Entity> getEntities() {
                return List.of();
            }

            @Override
            public org.bukkit.entity.Entity spawnEntity(Location location, org.bukkit.entity.EntityType type) {
                return null;
            }
        };
    }
}

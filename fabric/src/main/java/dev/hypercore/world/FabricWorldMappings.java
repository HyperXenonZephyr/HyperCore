package dev.hypercore.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Material;

/**
 * Centralized bi-directional mappings between the Bukkit enums and the native
 * Minecraft types on the Fabric loader.
 *
 * <p>Both loader adapters expose the same set of recognized types, but the
 * mapping tables reference Minecraft classes that are produced by each loader's
 * own dev environment, so they cannot be shared in {@code :core}. Keeping them
 * here isolates the mapping logic from {@link FabricWorldAccess} so the world
 * access class stays focused on the adapter contract.
 */
public final class FabricWorldMappings {

    private FabricWorldMappings() {
    }

    /** Maps a native Minecraft block to its Bukkit {@link Material}. */
    public static Material toMaterial(Block block) {
        if (block == Blocks.AIR) return Material.AIR;
        if (block == Blocks.STONE) return Material.STONE;
        if (block == Blocks.GRANITE) return Material.GRANITE;
        if (block == Blocks.DIORITE) return Material.DIORITE;
        if (block == Blocks.ANDESITE) return Material.ANDESITE;
        if (block == Blocks.DIRT) return Material.DIRT;
        if (block == Blocks.COARSE_DIRT) return Material.COARSE_DIRT;
        if (block == Blocks.PODZOL) return Material.PODZOL;
        if (block == Blocks.GRASS_BLOCK) return Material.GRASS_BLOCK;
        if (block == Blocks.SAND) return Material.SAND;
        if (block == Blocks.RED_SAND) return Material.RED_SAND;
        if (block == Blocks.GRAVEL) return Material.GRAVEL;
        if (block == Blocks.WATER) return Material.WATER;
        if (block == Blocks.LAVA) return Material.LAVA;
        if (block == Blocks.BEDROCK) return Material.BEDROCK;
        if (block == Blocks.COAL_ORE) return Material.COAL_ORE;
        if (block == Blocks.IRON_ORE) return Material.IRON_ORE;
        if (block == Blocks.GOLD_ORE) return Material.GOLD_ORE;
        if (block == Blocks.DIAMOND_ORE) return Material.DIAMOND_ORE;
        if (block == Blocks.EMERALD_ORE) return Material.EMERALD_ORE;
        if (block == Blocks.REDSTONE_ORE) return Material.REDSTONE_ORE;
        if (block == Blocks.LAPIS_ORE) return Material.LAPIS_ORE;
        if (block == Blocks.COPPER_ORE) return Material.COPPER_ORE;
        if (block == Blocks.COAL_BLOCK) return Material.COAL_BLOCK;
        if (block == Blocks.IRON_BLOCK) return Material.IRON_BLOCK;
        if (block == Blocks.GOLD_BLOCK) return Material.GOLD_BLOCK;
        if (block == Blocks.DIAMOND_BLOCK) return Material.DIAMOND_BLOCK;
        if (block == Blocks.EMERALD_BLOCK) return Material.EMERALD_BLOCK;
        if (block == Blocks.REDSTONE_BLOCK) return Material.REDSTONE_BLOCK;
        if (block == Blocks.LAPIS_BLOCK) return Material.LAPIS_BLOCK;
        if (block == Blocks.COPPER_BLOCK) return Material.COPPER_BLOCK;
        if (block == Blocks.OAK_LOG) return Material.OAK_LOG;
        if (block == Blocks.OAK_PLANKS) return Material.OAK_PLANKS;
        if (block == Blocks.OAK_LEAVES) return Material.OAK_LEAVES;
        if (block == Blocks.GLASS) return Material.GLASS;
        if (block == Blocks.CHEST) return Material.CHEST;
        if (block == Blocks.FURNACE) return Material.FURNACE;
        if (block == Blocks.CRAFTING_TABLE) return Material.CRAFTING_TABLE;
        throw new UnsupportedOperationException("Unmapped block: " + block);
    }

    /** Maps a Bukkit {@link Material} to its native Minecraft block. */
    public static Block toBlock(Material material) {
        return switch (material) {
            case AIR -> Blocks.AIR;
            case STONE -> Blocks.STONE;
            case GRANITE -> Blocks.GRANITE;
            case DIORITE -> Blocks.DIORITE;
            case ANDESITE -> Blocks.ANDESITE;
            case DIRT -> Blocks.DIRT;
            case COARSE_DIRT -> Blocks.COARSE_DIRT;
            case PODZOL -> Blocks.PODZOL;
            case GRASS_BLOCK -> Blocks.GRASS_BLOCK;
            case SAND -> Blocks.SAND;
            case RED_SAND -> Blocks.RED_SAND;
            case GRAVEL -> Blocks.GRAVEL;
            case WATER -> Blocks.WATER;
            case LAVA -> Blocks.LAVA;
            case BEDROCK -> Blocks.BEDROCK;
            case COAL_ORE -> Blocks.COAL_ORE;
            case IRON_ORE -> Blocks.IRON_ORE;
            case GOLD_ORE -> Blocks.GOLD_ORE;
            case DIAMOND_ORE -> Blocks.DIAMOND_ORE;
            case EMERALD_ORE -> Blocks.EMERALD_ORE;
            case REDSTONE_ORE -> Blocks.REDSTONE_ORE;
            case LAPIS_ORE -> Blocks.LAPIS_ORE;
            case COPPER_ORE -> Blocks.COPPER_ORE;
            case COAL_BLOCK -> Blocks.COAL_BLOCK;
            case IRON_BLOCK -> Blocks.IRON_BLOCK;
            case GOLD_BLOCK -> Blocks.GOLD_BLOCK;
            case DIAMOND_BLOCK -> Blocks.DIAMOND_BLOCK;
            case EMERALD_BLOCK -> Blocks.EMERALD_BLOCK;
            case REDSTONE_BLOCK -> Blocks.REDSTONE_BLOCK;
            case LAPIS_BLOCK -> Blocks.LAPIS_BLOCK;
            case COPPER_BLOCK -> Blocks.COPPER_BLOCK;
            case OAK_LOG -> Blocks.OAK_LOG;
            case OAK_PLANKS -> Blocks.OAK_PLANKS;
            case OAK_LEAVES -> Blocks.OAK_LEAVES;
            case GLASS -> Blocks.GLASS;
            case CHEST -> Blocks.CHEST;
            case FURNACE -> Blocks.FURNACE;
            case CRAFTING_TABLE -> Blocks.CRAFTING_TABLE;
            default -> throw new UnsupportedOperationException("Unmapped material: " + material);
        };
    }

    /** Maps a native Minecraft item to its Bukkit {@link Material}. */
    public static Material toMaterial(Item item) {
        if (item == Items.AIR) return Material.AIR;
        if (item == Items.STONE) return Material.STONE;
        if (item == Items.DIRT) return Material.DIRT;
        if (item == Items.GRASS_BLOCK) return Material.GRASS_BLOCK;
        if (item == Items.SAND) return Material.SAND;
        if (item == Items.CHEST) return Material.CHEST;
        if (item == Items.STICK) return Material.STICK;
        if (item == Items.COAL) return Material.COAL;
        if (item == Items.IRON_INGOT) return Material.IRON_INGOT;
        if (item == Items.GOLD_INGOT) return Material.GOLD_INGOT;
        if (item == Items.DIAMOND) return Material.DIAMOND;
        if (item == Items.REDSTONE) return Material.REDSTONE;
        if (item == Items.WHEAT_SEEDS) return Material.WHEAT_SEEDS;
        if (item == Items.WHEAT) return Material.WHEAT;
        if (item == Items.BREAD) return Material.BREAD;
        if (item == Items.APPLE) return Material.APPLE;
        if (item == Items.DIAMOND_SWORD) return Material.DIAMOND_SWORD;
        if (item == Items.IRON_SWORD) return Material.IRON_SWORD;
        if (item == Items.STONE_SWORD) return Material.STONE_SWORD;
        if (item == Items.WOODEN_SWORD) return Material.WOODEN_SWORD;
        if (item == Items.PLAYER_HEAD) return Material.PLAYER_HEAD;
        if (item == Items.ZOMBIE_SPAWN_EGG) return Material.ZOMBIE_SPAWN_EGG;
        if (item == Items.SKELETON_SPAWN_EGG) return Material.SKELETON_SPAWN_EGG;
        if (item == Items.CREEPER_SPAWN_EGG) return Material.CREEPER_SPAWN_EGG;
        throw new UnsupportedOperationException("Unmapped item: " + item);
    }

    /** Maps a Bukkit {@link Material} to its native Minecraft item. */
    public static Item toItem(Material material) {
        return switch (material) {
            case AIR -> Items.AIR;
            case STONE -> Items.STONE;
            case DIRT -> Items.DIRT;
            case GRASS_BLOCK -> Items.GRASS_BLOCK;
            case SAND -> Items.SAND;
            case CHEST -> Items.CHEST;
            case STICK -> Items.STICK;
            case COAL -> Items.COAL;
            case IRON_INGOT -> Items.IRON_INGOT;
            case GOLD_INGOT -> Items.GOLD_INGOT;
            case DIAMOND -> Items.DIAMOND;
            case REDSTONE -> Items.REDSTONE;
            case WHEAT_SEEDS -> Items.WHEAT_SEEDS;
            case WHEAT -> Items.WHEAT;
            case BREAD -> Items.BREAD;
            case APPLE -> Items.APPLE;
            case DIAMOND_SWORD -> Items.DIAMOND_SWORD;
            case IRON_SWORD -> Items.IRON_SWORD;
            case STONE_SWORD -> Items.STONE_SWORD;
            case WOODEN_SWORD -> Items.WOODEN_SWORD;
            case PLAYER_HEAD -> Items.PLAYER_HEAD;
            case ZOMBIE_SPAWN_EGG -> Items.ZOMBIE_SPAWN_EGG;
            case SKELETON_SPAWN_EGG -> Items.SKELETON_SPAWN_EGG;
            case CREEPER_SPAWN_EGG -> Items.CREEPER_SPAWN_EGG;
            default -> throw new UnsupportedOperationException("Unmapped material: " + material);
        };
    }

    /** Maps a Bukkit entity type to its native Minecraft entity type. */
    @SuppressWarnings("unchecked")
    public static EntityType<? extends Entity> toEntityType(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case ZOMBIE -> (EntityType<? extends Entity>) EntityType.ZOMBIE;
            case SKELETON -> (EntityType<? extends Entity>) EntityType.SKELETON;
            case CREEPER -> (EntityType<? extends Entity>) EntityType.CREEPER;
            case SPIDER -> (EntityType<? extends Entity>) EntityType.SPIDER;
            case CAVE_SPIDER -> (EntityType<? extends Entity>) EntityType.CAVE_SPIDER;
            case ENDERMAN -> (EntityType<? extends Entity>) EntityType.ENDERMAN;
            case WITCH -> (EntityType<? extends Entity>) EntityType.WITCH;
            case VILLAGER -> (EntityType<? extends Entity>) EntityType.VILLAGER;
            case PIG -> (EntityType<? extends Entity>) EntityType.PIG;
            case COW -> (EntityType<? extends Entity>) EntityType.COW;
            case SHEEP -> (EntityType<? extends Entity>) EntityType.SHEEP;
            case CHICKEN -> (EntityType<? extends Entity>) EntityType.CHICKEN;
            case HORSE -> (EntityType<? extends Entity>) EntityType.HORSE;
            case ITEM, DROPPED_ITEM -> (EntityType<? extends Entity>) EntityType.ITEM;
            case ARROW -> (EntityType<? extends Entity>) EntityType.ARROW;
            case FIREBALL -> (EntityType<? extends Entity>) EntityType.FIREBALL;
            case PRIMED_TNT -> (EntityType<? extends Entity>) EntityType.TNT;
            case EXPERIENCE_ORB -> (EntityType<? extends Entity>) EntityType.EXPERIENCE_ORB;
            default -> throw new UnsupportedOperationException("Unmapped entity type: " + type);
        };
    }

    /** Maps a native Minecraft entity type to its Bukkit entity type. */
    public static org.bukkit.entity.EntityType toBukkitEntityType(EntityType<?> type) {
        if (type == EntityType.PLAYER) return org.bukkit.entity.EntityType.PLAYER;
        if (type == EntityType.ZOMBIE) return org.bukkit.entity.EntityType.ZOMBIE;
        if (type == EntityType.SKELETON) return org.bukkit.entity.EntityType.SKELETON;
        if (type == EntityType.CREEPER) return org.bukkit.entity.EntityType.CREEPER;
        if (type == EntityType.SPIDER) return org.bukkit.entity.EntityType.SPIDER;
        if (type == EntityType.CAVE_SPIDER) return org.bukkit.entity.EntityType.CAVE_SPIDER;
        if (type == EntityType.ENDERMAN) return org.bukkit.entity.EntityType.ENDERMAN;
        if (type == EntityType.WITCH) return org.bukkit.entity.EntityType.WITCH;
        if (type == EntityType.VILLAGER) return org.bukkit.entity.EntityType.VILLAGER;
        if (type == EntityType.PIG) return org.bukkit.entity.EntityType.PIG;
        if (type == EntityType.COW) return org.bukkit.entity.EntityType.COW;
        if (type == EntityType.SHEEP) return org.bukkit.entity.EntityType.SHEEP;
        if (type == EntityType.CHICKEN) return org.bukkit.entity.EntityType.CHICKEN;
        if (type == EntityType.HORSE) return org.bukkit.entity.EntityType.HORSE;
        if (type == EntityType.ITEM) return org.bukkit.entity.EntityType.DROPPED_ITEM;
        if (type == EntityType.ARROW) return org.bukkit.entity.EntityType.ARROW;
        if (type == EntityType.FIREBALL) return org.bukkit.entity.EntityType.FIREBALL;
        if (type == EntityType.TNT) return org.bukkit.entity.EntityType.PRIMED_TNT;
        if (type == EntityType.EXPERIENCE_ORB) return org.bukkit.entity.EntityType.EXPERIENCE_ORB;
        throw new UnsupportedOperationException("Unmapped Minecraft entity type: " + type);
    }

    /** Maps a native Minecraft game type to its Bukkit {@link org.bukkit.GameMode}. */
    public static org.bukkit.GameMode toBukkitGameMode(net.minecraft.world.level.GameType type) {
        return switch (type) {
            case SURVIVAL -> org.bukkit.GameMode.SURVIVAL;
            case CREATIVE -> org.bukkit.GameMode.CREATIVE;
            case ADVENTURE -> org.bukkit.GameMode.ADVENTURE;
            case SPECTATOR -> org.bukkit.GameMode.SPECTATOR;
            default -> throw new UnsupportedOperationException("Unmapped game mode: " + type);
        };
    }

    /** Maps a Bukkit {@link org.bukkit.GameMode} to its native Minecraft game type. */
    public static net.minecraft.world.level.GameType toMinecraftGameMode(org.bukkit.GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> net.minecraft.world.level.GameType.SURVIVAL;
            case CREATIVE -> net.minecraft.world.level.GameType.CREATIVE;
            case ADVENTURE -> net.minecraft.world.level.GameType.ADVENTURE;
            case SPECTATOR -> net.minecraft.world.level.GameType.SPECTATOR;
        };
    }
}
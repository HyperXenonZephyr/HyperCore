package dev.hypercore.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Forge implementation of {@link WorldAccess} backed by a Minecraft
 * {@link ServerLevel}.
 *
 * <p>Block, item, and entity conversions are intentionally minimal: only the
 * materials and entity types needed for the current Bukkit API conformance
 * phase are mapped. Unknown mappings throw {@link UnsupportedOperationException}.
 */
public final class ForgeWorldAccess implements WorldAccess {
    private final String worldName;
    private final ServerLevel level;

    public ForgeWorldAccess(String worldName, ServerLevel level) {
        this.worldName = worldName;
        this.level = level;
    }

    @Override
    public String worldName() {
        return worldName;
    }

    @Override
    public long getTime() {
        return level.getDayTime();
    }

    @Override
    public void setTime(long time) {
        level.setDayTime(time);
    }

    @Override
    public boolean hasStorm() {
        return level.getLevelData().isRaining();
    }

    @Override
    public void setStorm(boolean storm) {
        level.getLevelData().setRaining(storm);
    }

    @Override
    public boolean isThundering() {
        return level.getLevelData().isThundering();
    }

    @Override
    public void setThundering(boolean thundering) {
        if (level.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData serverData) {
            serverData.setThundering(thundering);
        }
    }

    @Override
    public Position getSpawnLocation() {
        BlockPos spawn = level.getSharedSpawnPos();
        return new Position(spawn.getX(), spawn.getY(), spawn.getZ());
    }

    @Override
    public void setSpawnLocation(Position position) {
        level.setDefaultSpawnPos(new BlockPos((int) position.x(), (int) position.y(), (int) position.z()), 0);
    }

    @Override
    public String getBiome(int x, int y, int z) {
        Optional<ResourceKey<net.minecraft.world.level.biome.Biome>> key = level.getBiome(new BlockPos(x, y, z)).unwrapKey();
        return key.map(resourceKey -> resourceKey.location().toString()).orElse(null);
    }

    @Override
    public void setBiome(int x, int y, int z, String biomeKey) {
        // Runtime biome mutation in 1.21.1 requires direct chunk biome container
        // access, which is not exposed as a stable API on ServerLevel. This stub
        // exists so the Bukkit call does not throw; dedicated servers typically
        // do not mutate biomes at runtime through Bukkit.
    }

    @Override
    public String getBlockDataAsString(int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return state.toString();
    }

    @Override
    public void setBlockData(int x, int y, int z, String blockData) {
        // Full block-state parsing (including property brackets such as
        // [facing=north]) is not exposed as a stable single-method API in
        // 1.21.1. The Bukkit BlockData adapter falls back to material-only
        // updates; plugins that need exact state properties should use the
        // native loader APIs until HyperCore provides a complete mapping.
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        return level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(x, y, z));
    }

    @Override
    public int getSkyLight(int x, int y, int z) {
        return level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(x, y, z));
    }

    @Override
    public boolean isBlockPowered(int x, int y, int z) {
        return level.hasNeighborSignal(new BlockPos(x, y, z));
    }

    @Override
    public boolean isBlockIndirectlyPowered(int x, int y, int z) {
        return level.getBestNeighborSignal(new BlockPos(x, y, z)) > 0;
    }

    @Override
    public int getBlockPower(int x, int y, int z, String faceName) {
        Direction direction = Direction.byName(faceName);
        if (direction == null) {
            return 0;
        }
        return level.getSignal(new BlockPos(x, y, z), direction);
    }

    @Override
    public Material getBlockType(int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return toMaterial(state.getBlock());
    }

    @Override
    public void setBlockType(int x, int y, int z, Material type) {
        Block block = toBlock(type);
        // Bukkit block writes are expected to make the chunk exist; loading it
        // synchronously also makes the cross-process mirror reliable in chunks
        // that have not been generated yet.
        level.getChunk(x >> 4, z >> 4);
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
    }

    @Override
    public Inventory getBlockInventory(int x, int y, int z) {
        BlockEntity blockEntity = level.getBlockEntity(new BlockPos(x, y, z));
        if (!(blockEntity instanceof Container container)) {
            return null;
        }
        return new ForgeContainerInventory(container, this);
    }

    @Override
    public UUID spawnEntity(org.bukkit.entity.EntityType type, Position position) {
        EntityType<?> mcType = toEntityType(type);
        Entity entity = mcType.create(level);
        if (entity == null) {
            return null;
        }
        entity.setPos(position.x(), position.y(), position.z());
        if (!level.addFreshEntity(entity)) {
            return null;
        }
        return entity.getUUID();
    }

    @Override
    public Position getEntityPosition(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return null;
        }
        return new Position(entity.getX(), entity.getY(), entity.getZ());
    }

    @Override
    public boolean teleportEntity(UUID entityId, Position position) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return false;
        }
        entity.teleportTo(position.x(), position.y(), position.z());
        return true;
    }

    @Override
    public Inventory getPlayerInventory(UUID playerId) {
        Player player = level.getPlayerByUUID(playerId);
        if (player == null) {
            return null;
        }
        return new ForgePlayerInventory(player, this);
    }

    @Override
    public Collection<UUID> entityIds() {
        List<UUID> ids = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            ids.add(entity.getUUID());
        }
        return ids;
    }

    @Override
    public Collection<UUID> playerIds() {
        List<UUID> ids = new ArrayList<>();
        for (Player player : level.players()) {
            ids.add(player.getUUID());
        }
        return ids;
    }

    @Override
    public org.bukkit.entity.EntityType getEntityType(UUID entityId) {
        Entity entity = findEntity(entityId);
        return entity == null ? null : toBukkitEntityType(entity.getType());
    }

    @Override
    public String getEntityCustomName(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return null;
        }
        net.minecraft.network.chat.Component name = entity.getCustomName();
        return name == null ? null : name.getString();
    }

    @Override
    public boolean setEntityCustomName(UUID entityId, String name) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return false;
        }
        entity.setCustomName(name == null ? null : net.minecraft.network.chat.Component.literal(name));
        return true;
    }

    @Override
    public boolean isEntityAlive(UUID entityId) {
        Entity entity = findEntity(entityId);
        return entity != null && entity.isAlive();
    }

    @Override
    public boolean removeEntity(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return false;
        }
        entity.remove(Entity.RemovalReason.DISCARDED);
        return true;
    }

    @Override
    public org.bukkit.GameMode getPlayerGameMode(UUID playerId) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        return toBukkitGameMode(serverPlayer.gameMode.getGameModeForPlayer());
    }

    @Override
    public boolean setPlayerGameMode(UUID playerId, org.bukkit.GameMode gameMode) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        serverPlayer.setGameMode(toMinecraftGameMode(gameMode));
        return true;
    }

    @Override
    public void kickPlayer(UUID playerId, String message) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.connection == null) {
            return;
        }
        serverPlayer.connection.disconnect(Component.literal(message));
    }

    @Override
    public void sendTitle(UUID playerId, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.connection == null) {
            return;
        }
        if (title != null) {
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        }
        if (subtitle != null) {
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        }
        serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
    }

    @Override
    public void resetTitle(UUID playerId) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.connection == null) {
            return;
        }
        serverPlayer.connection.send(new ClientboundClearTitlesPacket(true));
    }

    @Override
    public boolean performCommand(UUID playerId, String command) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        CommandSourceStack source = serverPlayer.createCommandSourceStack();
        try {
            level.getServer().getCommands().getDispatcher().execute(command, source);
            return true;
        } catch (CommandSyntaxException error) {
            return false;
        }
    }

    @Override
    public void updateInventory(UUID playerId) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.inventoryMenu.sendAllDataToRemote();
    }

    @Override
    public boolean openInventory(UUID playerId, org.bukkit.inventory.Inventory inventory) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(inventory instanceof ForgeContainerInventory forgeInventory)) {
            return false;
        }
        Container container = forgeInventory.container;
        net.minecraft.world.MenuProvider provider = new net.minecraft.world.SimpleMenuProvider(
            (id, playerInventory, ignored) -> new net.minecraft.world.inventory.ChestMenu(
                net.minecraft.world.inventory.MenuType.GENERIC_9x3,
                id,
                playerInventory,
                container,
                container.getContainerSize() / 9
            ),
            Component.literal("Chest")
        );
        serverPlayer.openMenu(provider);
        return true;
    }

    @Override
    public void setResourcePack(UUID playerId, String url) {
        Player player = level.getPlayerByUUID(playerId);
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.connection == null) {
            return;
        }
        serverPlayer.connection.send(new ClientboundResourcePackPushPacket(
            java.util.UUID.randomUUID(),
            url,
            "",
            false,
            java.util.Optional.empty()
        ));
    }

    @Override
    public boolean isSneaking(UUID playerId) {
        Player player = level.getPlayerByUUID(playerId);
        return player != null && player.isShiftKeyDown();
    }

    @Override
    public void setSneaking(UUID playerId, boolean sneaking) {
        Player player = level.getPlayerByUUID(playerId);
        if (player == null) {
            return;
        }
        player.setShiftKeyDown(sneaking);
    }

    @Override
    public boolean isSprinting(UUID playerId) {
        Player player = level.getPlayerByUUID(playerId);
        return player != null && player.isSprinting();
    }

    @Override
    public void setSprinting(UUID playerId, boolean sprinting) {
        Player player = level.getPlayerByUUID(playerId);
        if (player == null) {
            return;
        }
        player.setSprinting(sprinting);
    }

    @Override
    public Vector3 getEntityVelocity(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return null;
        }
        net.minecraft.world.phys.Vec3 delta = entity.getDeltaMovement();
        return new Vector3(delta.x, delta.y, delta.z);
    }

    @Override
    public boolean setEntityVelocity(UUID entityId, Vector3 velocity) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return false;
        }
        entity.setDeltaMovement(new net.minecraft.world.phys.Vec3(velocity.x(), velocity.y(), velocity.z()));
        return true;
    }

    @Override
    public float getEntityFallDistance(UUID entityId) {
        Entity entity = findEntity(entityId);
        return entity == null ? 0.0f : entity.fallDistance;
    }

    @Override
    public boolean setEntityFallDistance(UUID entityId, float distance) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return false;
        }
        entity.fallDistance = distance;
        return true;
    }

    @Override
    public int getEntityFireTicks(UUID entityId) {
        Entity entity = findEntity(entityId);
        return entity == null ? 0 : entity.getRemainingFireTicks();
    }

    @Override
    public boolean setEntityFireTicks(UUID entityId, int ticks) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return false;
        }
        entity.setRemainingFireTicks(ticks);
        return true;
    }

    @Override
    public Collection<UUID> getEntityPassengers(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>(entity.getPassengers().size());
        for (Entity passenger : entity.getPassengers()) {
            ids.add(passenger.getUUID());
        }
        return ids;
    }

    @Override
    public boolean addEntityPassenger(UUID entityId, UUID passengerId) {
        Entity entity = findEntity(entityId);
        Entity passenger = findEntity(passengerId);
        if (entity == null || passenger == null) {
            return false;
        }
        return passenger.startRiding(entity, true);
    }

    @Override
    public boolean removeEntityPassenger(UUID entityId, UUID passengerId) {
        Entity passenger = findEntity(passengerId);
        if (passenger == null) {
            return false;
        }
        Entity vehicle = passenger.getVehicle();
        if (vehicle == null || !vehicle.getUUID().equals(entityId)) {
            return false;
        }
        passenger.stopRiding();
        return true;
    }

    @Override
    public boolean isEntityInsideVehicle(UUID entityId) {
        Entity entity = findEntity(entityId);
        return entity != null && entity.isPassenger();
    }

    @Override
    public UUID getEntityVehicle(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (entity == null) {
            return null;
        }
        Entity vehicle = entity.getVehicle();
        return vehicle == null ? null : vehicle.getUUID();
    }

    @Override
    public boolean leaveVehicle(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (entity == null || !entity.isPassenger()) {
            return false;
        }
        entity.stopRiding();
        return true;
    }

    @Override
    public double getEntityHealth(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return 0.0;
        }
        return living.getHealth();
    }

    @Override
    public boolean setEntityHealth(UUID entityId, double health) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return false;
        }
        living.setHealth((float) health);
        return true;
    }

    @Override
    public double getEntityMaxHealth(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return 0.0;
        }
        return living.getMaxHealth();
    }

    @Override
    public boolean setEntityMaxHealth(UUID entityId, double maxHealth) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return false;
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance attribute = living.getAttribute(
            net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
        );
        if (attribute == null) {
            return false;
        }
        attribute.setBaseValue(maxHealth);
        return true;
    }

    @Override
    public void damageEntity(UUID entityId, double amount) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return;
        }
        living.hurt(level.damageSources().generic(), (float) amount);
    }

    @Override
    public boolean isEntityAiEnabled(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) {
            return true;
        }
        return !mob.isNoAi();
    }

    @Override
    public boolean setEntityAiEnabled(UUID entityId, boolean ai) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) {
            return false;
        }
        mob.setNoAi(!ai);
        return true;
    }

    @Override
    public boolean isEntityCollidable(UUID entityId) {
        Entity entity = findEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return true;
        }
        return living.isPushable();
    }

    @Override
    public boolean setEntityCollidable(UUID entityId, boolean collidable) {
        // Minecraft 1.21.1 does not expose a setter for entity pushability under
        // the official mappings. The Bukkit collidable flag is therefore read-only
        // in this adapter; callers that need to disable collisions should use the
        // native loader APIs or the noPhysics field directly.
        return false;
    }

    private Entity findEntity(UUID entityId) {
        for (Entity entity : level.getAllEntities()) {
            if (entity.getUUID().equals(entityId)) {
                return entity;
            }
        }
        return null;
    }

    static Material toMaterial(Block block) {
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

    static Block toBlock(Material material) {
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

    static Material toMaterial(Item item) {
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

    static Item toItem(Material material) {
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

    ItemStack toMinecraft(org.bukkit.inventory.ItemStack bukkitStack) {
        if (bukkitStack == null || bukkitStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (bukkitStack instanceof ForgeItemStack forgeStack) {
            return forgeStack.toMinecraft();
        }
        ItemStack mcStack = new ItemStack(toItem(bukkitStack.getType()), bukkitStack.getAmount());
        if (bukkitStack.hasItemMeta()) {
            org.bukkit.inventory.ItemMeta meta = bukkitStack.getItemMeta();
            copyItemMetaToNative(meta, mcStack);
        }
        return mcStack;
    }

    private void copyItemMetaToNative(org.bukkit.inventory.ItemMeta meta, ItemStack mcStack) {
        if (meta == null) {
            return;
        }
        if (meta.hasDisplayName()) {
            mcStack.set(DataComponents.CUSTOM_NAME, Component.literal(meta.getDisplayName()));
        }
        if (meta.hasLore()) {
            List<Component> lines = new ArrayList<>(meta.getLore().size());
            for (String line : meta.getLore()) {
                lines.add(Component.literal(line));
            }
            mcStack.set(DataComponents.LORE, new ItemLore(lines));
        }
        if (!meta.getEnchants().isEmpty()) {
            var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                String key = entry.getKey().getKey().toString();
                var resourceKey = ResourceKey.create(
                    net.minecraft.core.registries.Registries.ENCHANTMENT,
                    ResourceLocation.parse(key)
                );
                registry.get(resourceKey).ifPresent(holder -> mcStack.enchant(holder, entry.getValue()));
            }
        }
    }

    static org.bukkit.inventory.ItemStack toBukkit(ItemStack mcStack) {
        if (mcStack == null || mcStack.isEmpty()) {
            return null;
        }
        return new ForgeItemStack(mcStack);
    }

    @SuppressWarnings("unchecked")
    static EntityType<? extends Entity> toEntityType(org.bukkit.entity.EntityType type) {
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

    private static org.bukkit.entity.EntityType toBukkitEntityType(EntityType<?> type) {
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

    private static org.bukkit.GameMode toBukkitGameMode(net.minecraft.world.level.GameType type) {
        return switch (type) {
            case SURVIVAL -> org.bukkit.GameMode.SURVIVAL;
            case CREATIVE -> org.bukkit.GameMode.CREATIVE;
            case ADVENTURE -> org.bukkit.GameMode.ADVENTURE;
            case SPECTATOR -> org.bukkit.GameMode.SPECTATOR;
            default -> throw new UnsupportedOperationException("Unmapped game mode: " + type);
        };
    }

    private static net.minecraft.world.level.GameType toMinecraftGameMode(org.bukkit.GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> net.minecraft.world.level.GameType.SURVIVAL;
            case CREATIVE -> net.minecraft.world.level.GameType.CREATIVE;
            case ADVENTURE -> net.minecraft.world.level.GameType.ADVENTURE;
            case SPECTATOR -> net.minecraft.world.level.GameType.SPECTATOR;
        };
    }
}

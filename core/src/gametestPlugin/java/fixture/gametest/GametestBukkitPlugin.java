package fixture.gametest;

import dev.hypercore.bukkit.BukkitServerAccess;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.RegionTickTask;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone Bukkit plugin used by Forge/Fabric dedicated-server GameTests to
 * verify end-to-end external plugin loading. It records lifecycle events and
 * exposes commands that exercise the Bukkit world/block/entity/inventory APIs
 * through HyperCore's region-locked execution service.
 *
 * <p>The plugin is packaged into its own JAR (see {@code :core:gametestPluginJar})
 * and copied into {@code run/plugins} before the game test server starts.
 */
public final class GametestBukkitPlugin extends JavaPlugin implements Listener {
    public static final List<String> LIFECYCLE = new ArrayList<>();

    private static volatile BlockPlaceEvent lastPlaceEvent;
    private static volatile boolean capturePlaceEvents;
    private static volatile boolean cancelNextPlaceEvent;
    private static volatile PlayerInteractEvent lastInteractEvent;
    private static volatile boolean captureInteractEvents;
    private static volatile EntityDamageEvent lastDamageEvent;
    private static volatile boolean captureDamageEvents;

    // Conformance test event capture state. These fields track event priority
    // ordering and cancellation propagation for the conformance subcommand.
    private static volatile boolean captureConformanceEvents;
    private static volatile boolean cancelConformancePlaceEvent;
    private static volatile int conformanceOrderCounter;
    private static volatile int lowestPriorityOrder;
    private static volatile int highestPriorityOrder;
    private static volatile boolean highestIgnoreCancelledFired;

    @Override
    protected void onLoad() {
        LIFECYCLE.add("load");
        if (getServer() == null) {
            throw new IllegalStateException("Bukkit server was not injected by the adapter");
        }
        if (getLogger() == null) {
            throw new IllegalStateException("Logger was not injected by the adapter");
        }
        if (getName() == null || getName().isBlank()) {
            throw new IllegalStateException("Plugin name was not injected by the adapter");
        }
        if (getDataFolder() == null) {
            throw new IllegalStateException("Data folder was not injected by the adapter");
        }
    }

    @Override
    protected void onEnable() {
        LIFECYCLE.add("enable");
        Bukkit.getPluginManager().registerEvents(this, this);

        CoexistenceProbe coexistenceProbe = new CoexistenceProbe();
        Bukkit.getPluginManager().registerEvents(coexistenceProbe, this);

        PluginCommand command = getCommand("hypercore-gametest");
        if (command == null) {
            throw new IllegalStateException("hypercore-gametest command was not registered from plugin.yml");
        }
        command.setExecutor((sender, cmd, label, args) -> {
            if (args.length == 0) {
                sender.sendMessage("HyperCore Bukkit GameTest OK");
                return true;
            }
            return switch (args[0]) {
                case "block" -> runBlockTest(args);
                case "blockdata" -> runBlockDataTest(args);
                case "blocklight" -> runBlockLightTest(args);
                case "entity" -> runEntityTest(args);
                case "entityproperties" -> runEntityPropertiesTest(args);
                case "player" -> runPlayerTest(args);
                case "playerexclusive" -> runPlayerExclusiveTest(args);
                case "inventory" -> runInventoryTest(args);
                case "inventorymeta" -> runInventoryMetaTest(args);
                case "playerarmor" -> runPlayerArmorTest();
                case "event" -> runEventTest(args);
                case "permission" -> runPermissionTest();
                case "conformance" -> runConformanceTest();
                case "world" -> runWorldTest();
                case "worldstate" -> runWorldStateTest();
                case "biome" -> runBiomeTest(args);
                case "parallel" -> runParallelTest(args);
                case "workermutation" -> runWorkerMutationTest(args);
                default -> false;
            };
        });

        PluginCommand coexistence = getCommand("hypercore-coexistence");
        if (coexistence == null) {
            throw new IllegalStateException("hypercore-coexistence command was not registered from plugin.yml");
        }
        coexistence.setExecutor((sender, cmd, label, args) -> coexistenceProbe.execute(sender, label, args));
    }

    @Override
    protected void onDisable() {
        LIFECYCLE.add("disable");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (capturePlaceEvents) {
            lastPlaceEvent = event;
        }
        if (cancelNextPlaceEvent) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (captureInteractEvents) {
            lastInteractEvent = event;
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (captureDamageEvents) {
            lastDamageEvent = event;
        }
    }

    // --- Conformance test event handlers ---
    // These listeners verify event priority ordering and cancellation
    // propagation. They only record state when captureConformanceEvents is
    // true so they do not interfere with other subcommands.

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlaceLowest(BlockPlaceEvent event) {
        if (captureConformanceEvents) {
            lowestPriorityOrder = conformanceOrderCounter++;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlaceLow(BlockPlaceEvent event) {
        if (captureConformanceEvents && cancelConformancePlaceEvent) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlaceHighest(BlockPlaceEvent event) {
        if (captureConformanceEvents) {
            highestPriorityOrder = conformanceOrderCounter++;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceHighestIgnoreCancelled(BlockPlaceEvent event) {
        if (captureConformanceEvents) {
            highestIgnoreCancelledFired = true;
        }
    }

    private boolean runBlockTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest block <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = firstWorld();
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);
        if (block.getType() != Material.STONE) {
            throw new IllegalStateException("Expected STONE at " + x + "," + y + "," + z + " but got " + block.getType());
        }

        BlockState snapshot = block.getState();
        snapshot.setType(Material.DIRT);
        if (!snapshot.update()) {
            throw new IllegalStateException("BlockState update returned false");
        }
        if (block.getType() != Material.DIRT) {
            throw new IllegalStateException("Expected DIRT after BlockState update at " + x + "," + y + "," + z);
        }

        return true;
    }

    private boolean runBlockDataTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest blockdata <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = firstWorld();
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);

        Block relative = block.getRelative(org.bukkit.block.BlockFace.UP);
        if (relative.getY() != y + 1) {
            throw new IllegalStateException("Relative block did not compute correct coordinates");
        }

        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data == null || data.getMaterial() != Material.STONE) {
            throw new IllegalStateException("BlockData material mismatch: " + data);
        }

        BlockState state = block.getState();
        state.setType(Material.DIRT);
        if (!state.update(true, false)) {
            throw new IllegalStateException("BlockState update returned false");
        }
        if (block.getType() != Material.DIRT) {
            throw new IllegalStateException("Expected DIRT after BlockData update at " + x + "," + y + "," + z);
        }

        return true;
    }

    private boolean runBlockLightTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest blocklight <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = firstWorld();
        Block block = world.getBlockAt(x, y, z);

        // Light values should be non-negative and within the Minecraft range.
        int light = block.getLightLevel();
        if (light < 0 || light > 15) {
            throw new IllegalStateException("Block light out of range: " + light);
        }
        int sky = block.getLightFromSky();
        if (sky < 0 || sky > 15) {
            throw new IllegalStateException("Sky light out of range: " + sky);
        }

        return true;
    }

    private boolean runEntityTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest entity <x> <y> <z>");
        }
        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);

        World world = firstWorld();
        Location spawn = new Location(world, x, y, z);
        Entity entity = world.spawnEntity(spawn, EntityType.ZOMBIE);
        if (entity == null) {
            throw new IllegalStateException("Failed to spawn zombie at " + spawn);
        }
        if (entity.getWorld() == null || !entity.getWorld().equals(world)) {
            throw new IllegalStateException("Spawned entity does not report the correct world");
        }
        if (entity.getType() != EntityType.ZOMBIE) {
            throw new IllegalStateException("Spawned entity reports wrong type: " + entity.getType());
        }
        if (!world.getEntities().contains(entity)) {
            throw new IllegalStateException("Spawned entity is missing from world.getEntities()");
        }

        entity.setCustomName("GameTestZombie");
        if (!"GameTestZombie".equals(entity.getCustomName())) {
            throw new IllegalStateException("Entity custom name was not set: " + entity.getCustomName());
        }

        Location destination = new Location(world, x + 1, y, z + 1);
        if (!entity.teleport(destination)) {
            throw new IllegalStateException("Entity teleport returned false");
        }
        Location current = entity.getLocation();
        if (current == null || current.getBlockX() != destination.getBlockX() || current.getBlockZ() != destination.getBlockZ()) {
            throw new IllegalStateException("Entity did not teleport to expected location: " + current);
        }

        entity.remove();
        if (entity.isValid()) {
            throw new IllegalStateException("Removed entity still reports valid");
        }

        return true;
    }

    private boolean runEntityPropertiesTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest entityproperties <x> <y> <z>");
        }
        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);

        World world = firstWorld();
        Location spawn = new Location(world, x, y, z);
        Entity entity = world.spawnEntity(spawn, EntityType.ZOMBIE);
        if (entity == null) {
            throw new IllegalStateException("Failed to spawn zombie at " + spawn);
        }
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalStateException("Spawned zombie is not a LivingEntity: " + entity.getClass());
        }

        Vector velocity = new Vector(1.0, 2.0, 3.0);
        living.setVelocity(velocity);
        Vector readVelocity = living.getVelocity();
        if (readVelocity == null
            || Math.abs(readVelocity.getX() - velocity.getX()) > 0.01
            || Math.abs(readVelocity.getY() - velocity.getY()) > 0.01
            || Math.abs(readVelocity.getZ() - velocity.getZ()) > 0.01) {
            throw new IllegalStateException("Velocity mismatch: " + readVelocity);
        }

        living.setFallDistance(5.0f);
        if (Math.abs(living.getFallDistance() - 5.0f) > 0.01f) {
            throw new IllegalStateException("Fall distance mismatch: " + living.getFallDistance());
        }

        living.setFireTicks(100);
        if (living.getFireTicks() != 100) {
            throw new IllegalStateException("Fire ticks mismatch: " + living.getFireTicks());
        }

        double originalMaxHealth = living.getMaxHealth();
        if (originalMaxHealth <= 0.0) {
            throw new IllegalStateException("Invalid original max health: " + originalMaxHealth);
        }

        living.setMaxHealth(30.0);
        if (Math.abs(living.getMaxHealth() - 30.0) > 0.01) {
            throw new IllegalStateException("Max health mismatch: " + living.getMaxHealth());
        }

        living.setHealth(10.0);
        if (Math.abs(living.getHealth() - 10.0) > 0.01) {
            throw new IllegalStateException("Health mismatch: " + living.getHealth());
        }

        if (!living.hasAI()) {
            throw new IllegalStateException("AI should be enabled by default");
        }
        living.setAI(false);
        if (living.hasAI()) {
            throw new IllegalStateException("AI was not disabled");
        }
        living.setAI(true);

        Entity passenger = world.spawnEntity(new Location(world, x, y + 1, z), EntityType.CHICKEN);
        if (passenger == null) {
            throw new IllegalStateException("Failed to spawn chicken passenger");
        }
        if (!living.addPassenger(passenger)) {
            throw new IllegalStateException("Failed to add passenger");
        }
        if (!living.getPassengers().contains(passenger)) {
            throw new IllegalStateException("Passenger not reported: " + living.getPassengers());
        }
        living.removePassenger(passenger);
        if (!living.getPassengers().isEmpty()) {
            throw new IllegalStateException("Passenger was not removed: " + living.getPassengers());
        }

        passenger.remove();
        entity.remove();
        return true;
    }

    private boolean runPlayerTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest player <x> <y> <z>");
        }
        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);

        World world = firstWorld();
        Player player = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
        if (player == null) {
            // Dedicated-server GameTest environments do not have real player
            // connections, so the player API cannot be exercised end-to-end here.
            // The same code paths are validated through unit tests and real servers.
            getLogger().warning("No online player available; skipping player API test");
            return true;
        }

        player.setDisplayName("GameTestPlayer");
        if (!"GameTestPlayer".equals(player.getDisplayName())) {
            throw new IllegalStateException("Player display name was not set: " + player.getDisplayName());
        }

        player.setGameMode(GameMode.CREATIVE);
        if (player.getGameMode() != GameMode.CREATIVE) {
            throw new IllegalStateException("Player game mode was not set to CREATIVE: " + player.getGameMode());
        }
        player.setGameMode(GameMode.SURVIVAL);

        Location destination = new Location(world, x, y, z);
        if (!player.teleport(destination)) {
            throw new IllegalStateException("Player teleport returned false");
        }
        Location current = player.getLocation();
        if (current == null || current.getBlockX() != destination.getBlockX() || current.getBlockZ() != destination.getBlockZ()) {
            throw new IllegalStateException("Player did not teleport to expected location: " + current);
        }

        return true;
    }

    private boolean runPlayerExclusiveTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest playerexclusive <x> <y> <z>");
        }
        Player player = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
        if (player == null) {
            getLogger().warning("No online player available; skipping player-exclusive API test");
            return true;
        }

        player.setSneaking(true);
        if (!player.isSneaking()) {
            throw new IllegalStateException("Player sneaking state was not set");
        }
        player.setSneaking(false);

        player.setSprinting(true);
        if (!player.isSprinting()) {
            throw new IllegalStateException("Player sprinting state was not set");
        }
        player.setSprinting(false);

        player.sendTitle("GameTest Title", "GameTest Subtitle", 10, 70, 20);
        player.resetTitle();
        player.updateInventory();
        player.setResourcePack("https://example.com/resourcepack.zip");

        return true;
    }

    private boolean runInventoryTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest inventory <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = firstWorld();
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST);

        BlockState state = block.getState();
        Inventory inventory = state.getInventory();
        if (inventory == null) {
            throw new IllegalStateException("Chest at " + x + "," + y + "," + z + " did not expose an inventory");
        }

        ItemStack item = new ItemStack(Material.DIAMOND, 5);
        inventory.setItem(0, item);
        ItemStack read = inventory.getItem(0);
        if (read == null || read.getType() != Material.DIAMOND || read.getAmount() != 5) {
            throw new IllegalStateException("Inventory write/read mismatch: " + read);
        }

        inventory.setItem(0, null);
        read = inventory.getItem(0);
        if (read != null) {
            throw new IllegalStateException("Inventory item was not removed: " + read);
        }

        return true;
    }

    private boolean runInventoryMetaTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest inventorymeta <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = firstWorld();
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST);

        BlockState state = block.getState();
        Inventory inventory = state.getInventory();
        if (inventory == null) {
            throw new IllegalStateException("Chest at " + x + "," + y + "," + z + " did not expose an inventory");
        }

        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMetaOrCreate();
        meta.setDisplayName("GameTest Sword");
        meta.setLore(List.of("Line 1", "Line 2"));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 3, true);
        item.setItemMeta(meta);

        inventory.setItem(0, item);
        ItemStack read = inventory.getItem(0);
        if (read == null || read.getType() != Material.DIAMOND_SWORD) {
            throw new IllegalStateException("Inventory meta write/read mismatch: " + read);
        }
        ItemMeta readMeta = read.getItemMeta();
        if (readMeta == null) {
            throw new IllegalStateException("Read item meta is null");
        }
        if (!"GameTest Sword".equals(readMeta.getDisplayName())) {
            throw new IllegalStateException("Display name mismatch: " + readMeta.getDisplayName());
        }
        if (readMeta.getLore() == null || readMeta.getLore().size() != 2) {
            throw new IllegalStateException("Lore mismatch: " + readMeta.getLore());
        }
        if (readMeta.getEnchantLevel(org.bukkit.enchantments.Enchantment.SHARPNESS) != 3) {
            throw new IllegalStateException("Enchantment mismatch: " + readMeta.getEnchants());
        }

        return true;
    }

    private boolean runPlayerArmorTest() {
        Player player = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
        if (player == null) {
            getLogger().warning("No online player available; skipping player armor test");
            return true;
        }

        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        ItemStack[] originalArmor = inventory.getArmorContents();

        ItemStack helmet = new ItemStack(Material.DIAMOND);
        ItemMeta meta = helmet.getItemMetaOrCreate();
        meta.setDisplayName("GameTest Helmet");
        helmet.setItemMeta(meta);

        ItemStack[] armor = new ItemStack[4];
        armor[3] = helmet;
        inventory.setArmorContents(armor);

        ItemStack[] updated = inventory.getArmorContents();
        if (updated[3] == null || updated[3].getType() != Material.DIAMOND) {
            throw new IllegalStateException("Armor contents were not set: " + updated[3]);
        }
        ItemMeta updatedMeta = updated[3].getItemMeta();
        if (updatedMeta == null || !"GameTest Helmet".equals(updatedMeta.getDisplayName())) {
            throw new IllegalStateException("Armor meta was not preserved: " + updatedMeta);
        }

        inventory.setArmorContents(originalArmor);
        return true;
    }

    private boolean runPermissionTest() {
        Permission permission = Bukkit.getPluginManager().getPermission("hypercore.gametest.admin");
        if (permission == null) {
            throw new IllegalStateException("Plugin.yml permission was not registered");
        }
        if (permission.getDefault() != PermissionDefault.OP) {
            throw new IllegalStateException("Permission default is not OP: " + permission.getDefault());
        }
        if (!permission.getChildren().containsKey("hypercore.gametest.use")) {
            throw new IllegalStateException("Permission child was not registered: " + permission.getChildren());
        }
        Permission child = Bukkit.getPluginManager().getPermission("hypercore.gametest.use");
        if (child == null) {
            throw new IllegalStateException("Child permission was not registered");
        }
        return true;
    }

    private boolean runWorldTest() {
        World world = Bukkit.getServer().createWorld(WorldCreator.name("world"));
        if (world == null) {
            throw new IllegalStateException("WorldCreator did not return the overworld");
        }
        if (world.getName() == null || world.getName().isEmpty()) {
            throw new IllegalStateException("Created world has no name");
        }
        World nether = Bukkit.getServer().createWorld(WorldCreator.name("the_nether"));
        if (nether == null) {
            throw new IllegalStateException("WorldCreator did not return the nether");
        }
        return true;
    }

    private boolean runWorldStateTest() {
        World world = firstWorld();

        long originalTime = world.getTime();
        world.setTime(6000);
        if (world.getTime() != 6000) {
            throw new IllegalStateException("World time was not set: " + world.getTime());
        }
        world.setTime(originalTime);

        boolean originalStorm = world.hasStorm();
        world.setStorm(true);
        if (!world.hasStorm()) {
            throw new IllegalStateException("World storm was not set");
        }
        world.setStorm(originalStorm);

        Location spawn = world.getSpawnLocation();
        if (spawn == null) {
            throw new IllegalStateException("World spawn location is null");
        }
        Location newSpawn = new Location(world, spawn.getX() + 1, spawn.getY(), spawn.getZ() + 1);
        world.setSpawnLocation(newSpawn);
        Location updatedSpawn = world.getSpawnLocation();
        if (updatedSpawn == null
            || updatedSpawn.getBlockX() != newSpawn.getBlockX()
            || updatedSpawn.getBlockZ() != newSpawn.getBlockZ()) {
            throw new IllegalStateException("World spawn location was not updated: " + updatedSpawn);
        }
        world.setSpawnLocation(spawn);

        return true;
    }

    private boolean runBiomeTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest biome <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = firstWorld();
        org.bukkit.block.Biome originalBiome = world.getBiome(x, y, z);
        world.setBiome(x, y, z, org.bukkit.block.Biome.PLAINS);
        org.bukkit.block.Biome updatedBiome = world.getBiome(x, y, z);
        if (updatedBiome != org.bukkit.block.Biome.PLAINS) {
            throw new IllegalStateException("Biome was not set to PLAINS: " + updatedBiome);
        }
        world.setBiome(x, y, z, originalBiome);
        return true;
    }

    private boolean runEventTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest event <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = firstWorld();
        Block block = world.getBlockAt(x, y, z);

        capturePlaceEvents = true;
        cancelNextPlaceEvent = false;
        lastPlaceEvent = null;
        block.setType(Material.STONE);
        if (lastPlaceEvent == null) {
            throw new IllegalStateException("BlockPlaceEvent was not fired");
        }
        if (lastPlaceEvent.getBlock().getX() != x || lastPlaceEvent.getBlock().getZ() != z) {
            throw new IllegalStateException("BlockPlaceEvent reported wrong block: " + lastPlaceEvent.getBlock());
        }

        cancelNextPlaceEvent = true;
        lastPlaceEvent = null;
        block.setType(Material.DIRT);
        if (block.getType() != Material.STONE) {
            throw new IllegalStateException("Cancelled BlockPlaceEvent did not prevent placement: " + block.getType());
        }

        capturePlaceEvents = false;
        cancelNextPlaceEvent = false;
        return true;
    }

    private boolean runConformanceTest() {
        // Ensure legacy capture/cancel flags do not interfere with the
        // conformance listeners.
        capturePlaceEvents = false;
        cancelNextPlaceEvent = false;

        World world = firstWorld();
        Block block = world.getBlockAt(0, 64, 0);

        // --- Event conformance tests ---

        // Test 1: Event priority ordering. The LOWEST priority listener must
        // fire before the HIGHEST priority listener. A shared counter records
        // the execution order of each listener.
        captureConformanceEvents = false;
        block.setType(Material.AIR);
        captureConformanceEvents = true;
        cancelConformancePlaceEvent = false;
        conformanceOrderCounter = 0;
        lowestPriorityOrder = -1;
        highestPriorityOrder = -1;
        highestIgnoreCancelledFired = false;
        block.setType(Material.STONE);
        if (lowestPriorityOrder < 0) {
            throw new IllegalStateException("LOWEST priority listener did not fire");
        }
        if (highestPriorityOrder < 0) {
            throw new IllegalStateException("HIGHEST priority listener did not fire");
        }
        if (lowestPriorityOrder >= highestPriorityOrder) {
            throw new IllegalStateException(
                "LOWEST listener did not fire before HIGHEST listener: lowest="
                    + lowestPriorityOrder + " highest=" + highestPriorityOrder);
        }
        getLogger().info("Event priority ordering conformance verified");

        // Test 2: Event cancellation propagation. A LOW priority listener
        // cancels the BlockPlaceEvent. A HIGHEST listener with
        // ignoreCancelled=true must NOT fire, while a HIGHEST listener without
        // ignoreCancelled still fires. The block placement must be prevented.
        captureConformanceEvents = false;
        block.setType(Material.STONE);
        captureConformanceEvents = true;
        cancelConformancePlaceEvent = true;
        conformanceOrderCounter = 0;
        lowestPriorityOrder = -1;
        highestPriorityOrder = -1;
        highestIgnoreCancelledFired = false;
        block.setType(Material.DIRT);
        if (highestIgnoreCancelledFired) {
            throw new IllegalStateException(
                "HIGHEST ignoreCancelled listener fired despite cancellation");
        }
        if (highestPriorityOrder < 0) {
            throw new IllegalStateException(
                "HIGHEST listener (ignoreCancelled=false) did not fire after cancellation");
        }
        if (block.getType() != Material.STONE) {
            throw new IllegalStateException(
                "Cancelled BlockPlaceEvent did not prevent placement: " + block.getType());
        }
        getLogger().info("Event cancellation propagation conformance verified");

        captureConformanceEvents = false;
        cancelConformancePlaceEvent = false;

        // Test 3: Event getHandlerList. BlockPlaceEvent.getHandlerList() must
        // return a non-null HandlerList with at least one registered listener.
        HandlerList handlerList = BlockPlaceEvent.getHandlerList();
        if (handlerList == null) {
            throw new IllegalStateException("BlockPlaceEvent.getHandlerList() returned null");
        }
        if (handlerList.getRegisteredListeners().isEmpty()) {
            throw new IllegalStateException(
                "BlockPlaceEvent.getHandlerList() has no registered listeners");
        }
        getLogger().info("Event getHandlerList conformance verified");

        // --- Permission conformance tests ---

        // Test 4: Permission default behavior. hypercore.gametest.admin is
        // registered with PermissionDefault.OP, which is the Bukkit mechanism
        // that grants the permission to OP players and denies it to non-OP
        // players by default.
        Permission adminPermission = Bukkit.getPluginManager().getPermission("hypercore.gametest.admin");
        if (adminPermission == null) {
            throw new IllegalStateException(
                "Permission hypercore.gametest.admin was not registered");
        }
        if (adminPermission.getDefault() != PermissionDefault.OP) {
            throw new IllegalStateException(
                "Permission default is not OP: " + adminPermission.getDefault());
        }
        getLogger().info("Permission default behavior conformance verified");

        // Test 5: Permission child inheritance. hypercore.gametest.use is
        // registered and declared as a child of hypercore.gametest.admin.
        Permission childPermission = Bukkit.getPluginManager().getPermission("hypercore.gametest.use");
        if (childPermission == null) {
            throw new IllegalStateException(
                "Child permission hypercore.gametest.use was not registered");
        }
        if (!adminPermission.getChildren().containsKey("hypercore.gametest.use")) {
            throw new IllegalStateException(
                "Parent permission does not declare child hypercore.gametest.use");
        }
        getLogger().info("Permission child inheritance conformance verified");

        // Test 6: Permission add/remove. A dynamically added permission is
        // retrievable via getPermission and then removable via removePermission.
        Permission testPermission = new Permission(
            "hypercore.conformance.test", PermissionDefault.TRUE);
        Bukkit.getPluginManager().addPermission(testPermission);
        Permission retrieved = Bukkit.getPluginManager().getPermission("hypercore.conformance.test");
        if (retrieved == null) {
            throw new IllegalStateException(
                "Permission was not returned by getPermission after addPermission");
        }
        if (retrieved.getDefault() != PermissionDefault.TRUE) {
            throw new IllegalStateException(
                "Added permission has wrong default: " + retrieved.getDefault());
        }
        Bukkit.getPluginManager().removePermission("hypercore.conformance.test");
        Permission removed = Bukkit.getPluginManager().getPermission("hypercore.conformance.test");
        if (removed != null) {
            throw new IllegalStateException(
                "Permission was still present after removePermission");
        }
        getLogger().info("Permission add/remove conformance verified");

        getLogger().info("HyperCore Bukkit GameTest OK");
        return true;
    }

    private boolean runParallelTest(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest parallel <regions>");
        }
        int regions = Integer.parseInt(args[1]);
        if (regions < 1) {
            throw new IllegalArgumentException("regions must be positive");
        }

        RegionExecutionService execution = BukkitServerAccess.regionExecution();
        if (execution == null) {
            throw new IllegalStateException("Region execution service is not available");
        }

        World world = firstWorld();
        int spacing = RegionTaskCoordinator.DEFAULT_REGION_SIZE_CHUNKS * 16;
        int[][] positions = new int[regions][3];
        for (int index = 0; index < regions; index++) {
            positions[index][0] = index * spacing;
            positions[index][1] = 64;
            positions[index][2] = index * spacing;
        }

        // Activate each target region directly through the execution service. In
        // a dedicated-server GameTest level, forcing block writes at far-apart
        // coordinates can trigger slow chunk generation and time out; activation
        // avoids world I/O while still including the regions in the tick.
        for (int index = 0; index < regions; index++) {
            execution.activateRegion(world.getName(), positions[index][0], positions[index][2]);
        }

        AtomicInteger tickedRegions = new AtomicInteger();
        CountDownLatch parallelismLatch = new CountDownLatch(Math.min(regions, 2));
        RegionTickTask task = (exec, region, tickId) -> {
            tickedRegions.incrementAndGet();
            parallelismLatch.countDown();
        };

        CompletableFuture<RegionTaskCoordinator.TickResult> tickFuture = execution.tickRegions(task);
        RegionTaskCoordinator.TickResult result;
        try {
            result = tickFuture.get(10, TimeUnit.SECONDS);
            if (!parallelismLatch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Region tick tasks did not execute in parallel");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel test interrupted", error);
        } catch (Exception error) {
            throw new IllegalStateException("Region tick failed", error);
        }

        if (result.targetRegions() < regions) {
            throw new IllegalStateException(
                "Expected at least " + regions + " target regions but got " + result.targetRegions()
            );
        }
        if (tickedRegions.get() < regions) {
            throw new IllegalStateException(
                "Expected " + regions + " regions to be ticked but got " + tickedRegions.get()
            );
        }
        if (result.ownersUsed() < 2) {
            throw new IllegalStateException(
                "Expected parallel region execution (ownersUsed >= 2) but got " + result.ownersUsed()
            );
        }

        return true;
    }

    private boolean runWorkerMutationTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest workermutation <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        RegionExecutionService execution = BukkitServerAccess.regionExecution();
        if (execution == null) {
            throw new IllegalStateException("Region execution service is not available");
        }

        World world = firstWorld();
        String worldName = world.getName();

        // Place a block at the test position on the server thread. The mutation
        // runs directly because the current thread is the server thread.
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);
        if (block.getType() != Material.STONE) {
            throw new IllegalStateException("Expected STONE at test position but got " + block.getType());
        }

        // Activate the region containing the test position so it is included in
        // the next region tick.
        execution.activateRegion(worldName, x, z);

        // Run tickRegions with a custom RegionTickTask that mutates the block
        // from the worker thread. Because the task runs on a HyperCore worker
        // thread (not the server thread), the WorldAccess adapter enqueues the
        // mutation instead of applying it synchronously.
        AtomicInteger mutated = new AtomicInteger();
        RegionTickTask task = (exec, region, tickId) -> {
            if (mutated.compareAndSet(0, 1)) {
                exec.setBlockType(worldName, x, y, z, Material.DIRT);
            }
        };

        CompletableFuture<RegionTaskCoordinator.TickResult> tickFuture = execution.tickRegions(task);
        try {
            tickFuture.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker mutation test interrupted", error);
        } catch (Exception error) {
            throw new IllegalStateException("Region tick failed", error);
        }

        if (mutated.get() == 0) {
            throw new IllegalStateException("Worker mutation task did not execute");
        }

        // The mutation should still be queued (not yet applied) because the
        // worker thread enqueued it for deferred execution on the server thread.
        if (block.getType() != Material.STONE) {
            throw new IllegalStateException("Block changed before flush: " + block.getType());
        }

        // Flush pending mutations on the server thread. This drains the queue
        // and applies the deferred block change.
        execution.flushAllPendingMutations();

        // Verify the block was changed to DIRT by the queued mutation.
        if (block.getType() != Material.DIRT) {
            throw new IllegalStateException("Expected DIRT after flush but got " + block.getType());
        }

        // Clean up.
        block.setType(Material.AIR);

        return true;
    }

    private World firstWorld() {
        World world = getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().get(0);
        if (world == null) {
            throw new IllegalStateException("No Bukkit world is exposed by HyperCore");
        }
        return world;
    }
}

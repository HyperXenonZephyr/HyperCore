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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
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
                case "entity" -> runEntityTest(args);
                case "player" -> runPlayerTest(args);
                case "inventory" -> runInventoryTest(args);
                case "event" -> runEventTest(args);
                case "permission" -> runPermissionTest();
                case "world" -> runWorldTest();
                case "parallel" -> runParallelTest(args);
                default -> false;
            };
        });
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

    private World firstWorld() {
        World world = getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().get(0);
        if (world == null) {
            throw new IllegalStateException("No Bukkit world is exposed by HyperCore");
        }
        return world;
    }
}

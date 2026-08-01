package fixture.gametest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone Bukkit plugin used by Forge/Fabric dedicated-server GameTests to
 * verify end-to-end external plugin loading. It records lifecycle events and
 * exposes commands that exercise the Bukkit world/block/entity APIs through
 * HyperCore's region-locked execution service.
 *
 * <p>The plugin is packaged into its own JAR (see {@code :core:gametestPluginJar})
 * and copied into {@code run/plugins} before the game test server starts.
 */
public final class GametestBukkitPlugin extends JavaPlugin {
    public static final List<String> LIFECYCLE = new ArrayList<>();

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
                default -> false;
            };
        });
    }

    @Override
    protected void onDisable() {
        LIFECYCLE.add("disable");
    }

    private boolean runBlockTest(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-gametest block <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        World world = getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().get(0);
        if (world == null) {
            throw new IllegalStateException("No Bukkit world is exposed by HyperCore");
        }

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

        World world = getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().get(0);
        if (world == null) {
            throw new IllegalStateException("No Bukkit world is exposed by HyperCore");
        }

        Location spawn = new Location(world, x, y, z);
        Entity entity = world.spawnEntity(spawn, EntityType.ZOMBIE);
        if (entity == null) {
            throw new IllegalStateException("Failed to spawn zombie at " + spawn);
        }
        if (entity.getWorld() == null || !entity.getWorld().equals(world)) {
            throw new IllegalStateException("Spawned entity does not report the correct world");
        }
        if (!world.getEntities().contains(entity)) {
            throw new IllegalStateException("Spawned entity is missing from world.getEntities()");
        }

        Location destination = new Location(world, x + 1, y, z + 1);
        if (!entity.teleport(destination)) {
            throw new IllegalStateException("Entity teleport returned false");
        }
        Location current = entity.getLocation();
        if (current == null || current.getBlockX() != destination.getBlockX() || current.getBlockZ() != destination.getBlockZ()) {
            throw new IllegalStateException("Entity did not teleport to expected location: " + current);
        }

        return true;
    }
}

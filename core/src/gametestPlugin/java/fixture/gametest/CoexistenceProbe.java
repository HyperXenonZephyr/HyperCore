package fixture.gametest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-host coexistence probe for the orchestrated Forge/Fabric deployment.
 *
 * <p>The probe exposes commands that the coexistence GameTests use to exercise
 * the bridge: block placement, entity spawn/move, mirrored command execution,
 * and block event cancellation propagation. GameTest assertions observe results
 * through the world itself (marker blocks and mirrored positions) rather than
 * sharing Java state across processes, so the probe stays a plain Bukkit
 * listener running in the external plugin classloader.
 */
public final class CoexistenceProbe implements Listener {
    /** Marker placed when a "forgeonly" execution lands on THIS host. */
    public static final int FORGE_MARKER_X = 10_041;
    public static final int FORGE_MARKER_Y = 64;
    public static final int FORGE_MARKER_Z = 10_041;
    /** Marker placed when a BlockBreakEvent is observed here as cancelled. */
    public static final int CANCEL_MARKER_X = 10_051;
    public static final int CANCEL_MARKER_Y = 64;
    public static final int CANCEL_MARKER_Z = 10_051;

    private static final Map<String, Boolean> CANCEL_BREAKS = new ConcurrentHashMap<>();
    private static volatile UUID trackedEntity;

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        String key = key(event.getBlock().getWorld().getName(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        if (Boolean.TRUE.equals(CANCEL_BREAKS.get(key))) {
            event.setCancelled(true);
        }
        if (event.isCancelled()) {
            // Signal the cancellation to GameTests through the world: both hosts
            // converge on a marker block at the fixed logical coordinates.
            setMarker(CANCEL_MARKER_X, CANCEL_MARKER_Y, CANCEL_MARKER_Z, Material.GOLD_BLOCK);
        }
    }

    /**
     * Handles the {@code hypercore-coexistence} command tree.
     */
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("HyperCore coexistence probe OK");
            return true;
        }
        return switch (args[0]) {
            case "set" -> runSet(sender, args);
            case "break" -> runBreak(sender, args);
            case "spawn" -> runSpawn(sender, args);
            case "move" -> runMove(sender, args);
            case "cancelbreak" -> runCancelBreak(sender, args);
            case "forgeonly" -> runForgeOnly(sender);
            default -> false;
        };
    }

    private boolean runSet(CommandSender sender, String[] args) {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: /hypercore-coexistence set <x> <y> <z> <material>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);
        Material material = Material.valueOf(args[4]);
        World world = firstWorld();
        world.getBlockAt(x, y, z).setType(material);
        sender.sendMessage("Set " + material + " at " + x + "," + y + "," + z);
        return true;
    }

    private boolean runBreak(CommandSender sender, String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-coexistence break <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);
        firstWorld().getBlockAt(x, y, z).setType(Material.AIR);
        sender.sendMessage("Broke block at " + x + "," + y + "," + z);
        return true;
    }

    private boolean runSpawn(CommandSender sender, String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-coexistence spawn <x> <y> <z>");
        }
        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);
        World world = firstWorld();
        Entity entity = world.spawnEntity(new Location(world, x, y, z), EntityType.ZOMBIE);
        if (entity == null) {
            throw new IllegalStateException("Failed to spawn zombie");
        }
        trackedEntity = entity.getUniqueId();
        sender.sendMessage("Spawned zombie " + trackedEntity + " at " + x + "," + y + "," + z);
        return true;
    }

    private boolean runMove(CommandSender sender, String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-coexistence move <x> <y> <z>");
        }
        UUID entityId = trackedEntity;
        if (entityId == null) {
            throw new IllegalStateException("No tracked entity to move");
        }
        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);
        boolean teleported = firstWorld().getEntities().stream()
            .filter(entity -> entity.getUniqueId().equals(entityId))
            .findFirst()
            .map(entity -> entity.teleport(new Location(entity.getWorld(), x, y, z)))
            .orElse(false);
        if (!teleported) {
            throw new IllegalStateException("Tracked entity was not found");
        }
        sender.sendMessage("Moved zombie " + entityId + " to " + x + "," + y + "," + z);
        return true;
    }

    private boolean runCancelBreak(CommandSender sender, String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /hypercore-coexistence cancelbreak <x> <y> <z>");
        }
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);
        CANCEL_BREAKS.put(key(firstWorld().getName(), x, y, z), true);
        sender.sendMessage("Next break at " + x + "," + y + "," + z + " will be cancelled");
        return true;
    }

    private boolean runForgeOnly(CommandSender sender) {
        // Prove the command executed on this host by placing a marker block that
        // the GameTest can observe in its own world.
        setMarker(FORGE_MARKER_X, FORGE_MARKER_Y, FORGE_MARKER_Z, Material.DIAMOND_BLOCK);
        sender.sendMessage("forgeonly executed on this host");
        return true;
    }

    private static void setMarker(int x, int y, int z, Material material) {
        firstWorld().getBlockAt(x, y, z).setType(material);
    }

    private static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    private static World firstWorld() {
        World world = Bukkit.getServer().getWorlds().isEmpty() ? null : Bukkit.getServer().getWorlds().get(0);
        if (world == null) {
            throw new IllegalStateException("No Bukkit world is exposed by HyperCore");
        }
        return world;
    }
}

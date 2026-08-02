package org.bukkit.generator;

import org.bukkit.World;

import java.util.Random;

/**
 * Minimal Bukkit-compatible chunk generator marker interface.
 *
 * <p>HyperCore does not support custom chunk generators in the core; this class
 * exists so that Bukkit plugins can declare and pass generators without
 * compilation errors. Actual generation is always delegated to Minecraft.
 */
public abstract class ChunkGenerator {

    /**
     * Returns the fixed spawn location for the given world, or {@code null} to
     * use the default.
     */
    public org.bukkit.Location getFixedSpawnLocation(World world, Random random) {
        return null;
    }
}

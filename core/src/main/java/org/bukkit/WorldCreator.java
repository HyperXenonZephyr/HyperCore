package org.bukkit;

import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Minimal Bukkit-compatible {@code WorldCreator} used to configure a new world
 * before asking the server to create or load it.
 *
 * <p>HyperCore does not implement custom chunk generators in the core; the
 * creator only carries the configuration that the loader adapter passes to
 * Minecraft's own world creation path.
 */
public final class WorldCreator {
    private final String name;
    private World.Environment environment = World.Environment.NORMAL;
    private long seed = new Random().nextLong();
    private boolean generateStructures = true;
    private ChunkGenerator generator;

    private WorldCreator(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("World name must not be blank");
        }
        this.name = name;
    }

    /**
     * Creates a creator for the world with the given name.
     *
     * @param name the world name
     * @return a new world creator
     */
    public static WorldCreator name(String name) {
        return new WorldCreator(name);
    }

    /**
     * Returns the configured world name.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the configured environment.
     */
    public World.Environment environment() {
        return environment;
    }

    /**
     * Sets the environment for the new world.
     *
     * @param environment the environment
     * @return this creator
     */
    public WorldCreator environment(World.Environment environment) {
        this.environment = environment == null ? World.Environment.NORMAL : environment;
        return this;
    }

    /**
     * Returns the configured seed.
     */
    public long seed() {
        return seed;
    }

    /**
     * Sets the seed for the new world.
     *
     * @param seed the seed
     * @return this creator
     */
    public WorldCreator seed(long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Returns whether structures will be generated.
     */
    public boolean generateStructures() {
        return generateStructures;
    }

    /**
     * Sets whether structures will be generated.
     *
     * @param generateStructures whether to generate structures
     * @return this creator
     */
    public WorldCreator generateStructures(boolean generateStructures) {
        this.generateStructures = generateStructures;
        return this;
    }

    /**
     * Returns the custom chunk generator, or {@code null} if none.
     */
    public ChunkGenerator generator() {
        return generator;
    }

    /**
     * Sets a custom chunk generator. In HyperCore this is reserved for future
     * use; the core does not install custom generators.
     *
     * @param generator the generator, or {@code null}
     * @return this creator
     */
    public WorldCreator generator(ChunkGenerator generator) {
        this.generator = generator;
        return this;
    }
}

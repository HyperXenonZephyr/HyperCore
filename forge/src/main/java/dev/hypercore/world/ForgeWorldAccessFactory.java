package dev.hypercore.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Forge implementation of {@link WorldAccessFactory}.
 *
 * <p>Builds a {@link ForgeWorldAccess} for every loaded {@link ServerLevel}.
 */
public final class ForgeWorldAccessFactory implements WorldAccessFactory {
    private final MinecraftServer server;
    private final Map<String, WorldAccess> accessByName = new HashMap<>();

    public ForgeWorldAccessFactory(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public WorldAccess access(String worldName) {
        WorldAccess existing = accessByName.get(worldName);
        if (existing != null) {
            return existing;
        }
        WorldAccess created = createAccess(worldName);
        if (created != null) {
            accessByName.put(worldName, created);
        }
        return created;
    }

    @Override
    public String createWorld(org.bukkit.WorldCreator creator) {
        String requested = creator.name();
        // HyperCore does not create custom dimensions in the core. If the
        // requested name matches an already-loaded vanilla dimension, return
        // that dimension's canonical name so plugins get a valid World view.
        for (ServerLevel level : server.getAllLevels()) {
            String dimensionName = level.dimension().location().toString();
            if (dimensionName.equals(requested)
                || (Level.OVERWORLD.equals(level.dimension()) && ("overworld".equals(requested) || "world".equals(requested)))
                || (Level.NETHER.equals(level.dimension()) && "the_nether".equals(requested))
                || (Level.END.equals(level.dimension()) && "the_end".equals(requested))) {
                return dimensionName;
            }
        }
        return null;
    }

    @Override
    public Collection<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            names.add(level.dimension().location().toString());
        }
        return names;
    }

    private ForgeWorldAccess createAccess(String worldName) {
        ServerLevel level = findLevel(worldName);
        if (level == null) {
            return null;
        }
        return new ForgeWorldAccess(worldName, level);
    }

    private ServerLevel findLevel(String worldName) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            String dimensionName = key.location().toString();
            if (dimensionName.equals(worldName)
                || Level.OVERWORLD.equals(key) && ("overworld".equals(worldName) || "world".equals(worldName))) {
                return level;
            }
        }
        return null;
    }
}

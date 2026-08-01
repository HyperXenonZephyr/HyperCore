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
 * Fabric implementation of {@link WorldAccessFactory}.
 *
 * <p>Builds a {@link FabricWorldAccess} for every loaded {@link ServerLevel}.
 */
public final class FabricWorldAccessFactory implements WorldAccessFactory {
    private final MinecraftServer server;
    private final Map<String, FabricWorldAccess> accessByName = new HashMap<>();

    public FabricWorldAccessFactory(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public WorldAccess access(String worldName) {
        return accessByName.computeIfAbsent(worldName, this::createAccess);
    }

    @Override
    public Collection<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            names.add(level.dimension().location().toString());
        }
        return names;
    }

    private FabricWorldAccess createAccess(String worldName) {
        ServerLevel level = findLevel(worldName);
        if (level == null) {
            return null;
        }
        return new FabricWorldAccess(worldName, level);
    }

    private ServerLevel findLevel(String worldName) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            String dimensionName = key.location().toString();
            if (dimensionName.equals(worldName)
                || key == Level.OVERWORLD && ("overworld".equals(worldName) || "world".equals(worldName))) {
                return level;
            }
        }
        return null;
    }
}

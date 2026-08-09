package dev.hypercore.world;

import java.util.Collection;
import java.util.List;

/**
 * Test factory that returns a shared {@link StubWorldAccess} for a fixed set
 * of world names. Tests obtain the stub via {@link #stub(String)} to configure
 * return values and inspect recorded mutations.
 */
public final class StubWorldAccessFactory implements WorldAccessFactory {

    private final StubWorldAccess[] stubs;

    public StubWorldAccessFactory(String... worldNames) {
        stubs = new StubWorldAccess[worldNames.length];
        for (int i = 0; i < worldNames.length; i++) {
            stubs[i] = new StubWorldAccess(worldNames[i]);
        }
    }

    @Override
    public WorldAccess access(String worldName) {
        for (StubWorldAccess stub : stubs) {
            if (stub.worldName().equals(worldName)) {
                return stub;
            }
        }
        return null;
    }

    @Override
    public Collection<String> worldNames() {
        return List.of(stubNames());
    }

    /**
     * Returns the stub for the given world name.
     *
     * @throws IllegalArgumentException if no stub was registered for that name
     */
    public StubWorldAccess stub(String worldName) {
        for (StubWorldAccess stub : stubs) {
            if (stub.worldName().equals(worldName)) {
                return stub;
            }
        }
        throw new IllegalArgumentException("No stub for world: " + worldName);
    }

    private String[] stubNames() {
        String[] names = new String[stubs.length];
        for (int i = 0; i < stubs.length; i++) {
            names[i] = stubs[i].worldName();
        }
        return names;
    }
}

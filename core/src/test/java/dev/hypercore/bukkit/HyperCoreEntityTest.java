package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.StubWorldAccess;
import dev.hypercore.world.WorldAccess;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HyperCoreEntity} verifying that the adapter delegates
 * to {@link RegionExecutionService}, which in turn delegates to a configurable
 * {@link StubWorldAccess}.
 *
 * <p>Each test configures the stub's per-entity state maps and then asserts that
 * the Bukkit-facing adapter reads and writes through to the stub as expected.
 */
class HyperCoreEntityTest {

    @Test
    void getUniqueIdReturnsConstructorId() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            UUID entityId = UUID.randomUUID();

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertEquals(entityId, entity.getUniqueId());
        }
    }

    @Test
    void getTypeDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertEquals(EntityType.ZOMBIE, entity.getType());
        }
    }

    @Test
    void getLocationReturnsLocationWithWorld() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            Location location = entity.getLocation();
            assertNotNull(location);
            assertEquals(10.0, location.getX());
            assertEquals(20.0, location.getY());
            assertEquals(30.0, location.getZ());
            assertNotNull(location.getWorld());
            assertEquals("world", location.getWorld().getName());
        }
    }

    @Test
    void getLocationReturnsNullForUnknownEntity() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            UUID entityId = UUID.randomUUID();

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertNull(entity.getLocation());
        }
    }

    @Test
    void getWorldReturnsWorldFromLocation() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertNotNull(entity.getWorld());
        }
    }

    @Test
    void getWorldReturnsNullWhenLocationIsNull() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            UUID entityId = UUID.randomUUID();

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertNull(entity.getWorld());
        }
    }

    @Test
    void teleportDelegatesToExecution() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            Location destination = new Location(null, 12, 22, 32);
            assertTrue(entity.teleport(destination));

            WorldAccess.Position updated = stub.entityPositions.get(entityId);
            assertNotNull(updated);
            assertEquals(12.0, updated.x());
            assertEquals(22.0, updated.y());
            assertEquals(32.0, updated.z());
        }
    }

    @Test
    void getCustomNameDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityCustomNames.put(entityId, "Boss");
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertEquals("Boss", entity.getCustomName());
        }
    }

    @Test
    void setCustomNameRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            entity.setCustomName("Boss");

            assertEquals("Boss", stub.recordedCustomName);
        }
    }

    @Test
    void getNameReturnsCustomNameWhenSet() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityCustomNames.put(entityId, "Boss");
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertEquals("Boss", entity.getName());
        }
    }

    @Test
    void getNameReturnsEntityTypeLowercaseWhenNoCustomName() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertEquals("zombie", entity.getName());
        }
    }

    @Test
    void isDeadReturnsFalseWhenEntityExists() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertFalse(entity.isDead());
        }
    }

    @Test
    void isDeadReturnsTrueWhenEntityRemoved() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = new ArrayList<>(List.of(entityId));

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            entity.remove();

            assertTrue(entity.isDead());
        }
    }

    @Test
    void isValidReturnsTrueWhenEntityExists() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertTrue(entity.isValid());
        }
    }

    @Test
    void removeRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = new ArrayList<>(List.of(entityId));

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            entity.remove();

            assertEquals(entityId, stub.removedEntity);
        }
    }

    @Test
    void getVelocityDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityVelocities.put(entityId, new WorldAccess.Vector3(1, 2, 3));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            Vector velocity = entity.getVelocity();
            assertEquals(1.0, velocity.getX());
            assertEquals(2.0, velocity.getY());
            assertEquals(3.0, velocity.getZ());
        }
    }

    @Test
    void setVelocityRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            entity.setVelocity(new Vector(1, 2, 3));

            assertNotNull(stub.recordedVelocity);
            assertEquals(1.0, stub.recordedVelocity.x());
            assertEquals(2.0, stub.recordedVelocity.y());
            assertEquals(3.0, stub.recordedVelocity.z());
        }
    }

    @Test
    void getFireTicksDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityFireTicks.put(entityId, 42);
            stub.entityIds = List.of(entityId);

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertEquals(42, entity.getFireTicks());
        }
    }

    @Test
    void equalsUsesEntityId() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            UUID entityId = UUID.randomUUID();

            HyperCoreEntity a = new HyperCoreEntity(execution, entityId);
            HyperCoreEntity b = new HyperCoreEntity(execution, entityId);
            HyperCoreEntity c = new HyperCoreEntity(execution, UUID.randomUUID());

            assertTrue(a.equals(b));
            assertFalse(a.equals(c));
        }
    }

    @Test
    void hashCodeMatchesEntityIdHashCode() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            UUID entityId = UUID.randomUUID();

            HyperCoreEntity entity = new HyperCoreEntity(execution, entityId);

            assertEquals(entityId.hashCode(), entity.hashCode());
        }
    }
}

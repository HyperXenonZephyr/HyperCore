package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.StubWorldAccess;
import dev.hypercore.world.WorldAccess;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HyperCoreLivingEntity} verifying that the adapter
 * delegates health, damage, AI, and collision attributes to
 * {@link RegionExecutionService}, which in turn delegates to a configurable
 * {@link StubWorldAccess}.
 *
 * <p>Each test configures the stub's per-entity state maps and then asserts that
 * the Bukkit-facing adapter reads and writes through to the stub as expected.
 */
class HyperCoreLivingEntityTest {

    @Test
    void getHealthDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityHealth.put(entityId, 15.0);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            assertEquals(15.0, entity.getHealth());
        }
    }

    @Test
    void setHealthRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            entity.setHealth(10.0);

            assertEquals(10.0, stub.recordedHealth);
        }
    }

    @Test
    void getMaxHealthDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityMaxHealth.put(entityId, 20.0);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            assertEquals(20.0, entity.getMaxHealth());
        }
    }

    @Test
    void setMaxHealthRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            entity.setMaxHealth(30.0);

            assertEquals(30.0, stub.recordedMaxHealth);
        }
    }

    @Test
    void damageDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            entity.damage(5.0);

            assertEquals(5.0, stub.recordedDamage);
        }
    }

    @Test
    void hasAiDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityAi.put(entityId, true);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            assertEquals(true, entity.hasAI());
        }
    }

    @Test
    void setAiRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            entity.setAI(false);

            assertEquals(false, stub.recordedAi);
        }
    }

    @Test
    void isCollidableDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityCollidable.put(entityId, true);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            assertTrue(entity.isCollidable());
        }
    }

    @Test
    void setCollidableRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);

            entity.setCollidable(false);

            assertEquals(false, stub.recordedCollidable);
        }
    }

    @Test
    void damageWithSourceDelegatesToAmountOnly() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            UUID entityId = UUID.randomUUID();
            stub.entityPositions.put(entityId, new WorldAccess.Position(10, 20, 30));
            stub.entityTypes.put(entityId, EntityType.ZOMBIE);
            stub.entityIds = List.of(entityId);

            HyperCoreLivingEntity entity = new HyperCoreLivingEntity(execution, entityId);
            HyperCoreEntity source = new HyperCoreEntity(execution, UUID.randomUUID());

            entity.damage(5.0, source);

            assertEquals(5.0, stub.recordedDamage);
        }
    }
}

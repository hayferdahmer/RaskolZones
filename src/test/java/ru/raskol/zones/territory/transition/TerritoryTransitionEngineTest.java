package ru.raskol.zones.territory.transition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raskol.zones.foundation.storage.SqliteStorageService;
import ru.raskol.zones.territory.SqliteTerritoryRepository;
import ru.raskol.zones.territory.TerritoryDraft;
import ru.raskol.zones.territory.TerritoryService;
import ru.raskol.zones.territory.geometry.CuboidGeometry;
import ru.raskol.zones.territory.spatial.SpatialQueryService;
import ru.raskol.zones.territory.type.TerritoryTypeRegistry;
import ru.raskol.zones.world.WorldRegistryService;

class TerritoryTransitionEngineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void emitsSingleEnterAndExitAndDoesNotRepeatWhilePlayerStaysInside() throws Exception {
        try (var storage = storage("enter-exit.db")) {
            UUID worldId = registerWorld(storage);
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            var territory = territories.create(new TerritoryDraft(
                    worldId, null, "city", "Город", 0,
                    new CuboidGeometry(10, 20, 0, 100, 10, 20)
            )).toCompletableFuture().get();
            var engine = new TerritoryTransitionEngine(queries);
            UUID playerId = UUID.randomUUID();

            assertTrue(engine.initialize(playerId, pos(worldId, 0, 64, 0), TransitionReason.JOIN).isEmpty());
            var entered = engine.move(playerId, pos(worldId, 12, 64, 12), TransitionReason.MOVEMENT);
            assertEquals(1, entered.size());
            assertEquals(TransitionType.ENTER, entered.getFirst().type());
            assertEquals(territory.id(), entered.getFirst().territory().id());

            assertTrue(engine.move(playerId, pos(worldId, 13, 64, 13), TransitionReason.MOVEMENT).isEmpty());
            var exited = engine.move(playerId, pos(worldId, 30, 64, 30), TransitionReason.MOVEMENT);
            assertEquals(1, exited.size());
            assertEquals(TransitionType.EXIT, exited.getFirst().type());
        }
    }

    @Test
    void samplesMovementPathAndDetectsNarrowTerritoryCrossedBetweenEndpoints() throws Exception {
        try (var storage = storage("narrow-crossing.db")) {
            UUID worldId = registerWorld(storage);
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            var territory = territories.create(new TerritoryDraft(
                    worldId, null, "resource", "Узкий проход", 0,
                    new CuboidGeometry(5, 5, 0, 100, 0, 10)
            )).toCompletableFuture().get();
            var engine = new TerritoryTransitionEngine(queries);
            UUID playerId = UUID.randomUUID();

            engine.initialize(playerId, pos(worldId, 0, 64, 5), TransitionReason.JOIN);
            var transitions = engine.move(playerId, pos(worldId, 10, 64, 5), TransitionReason.MOVEMENT);

            assertEquals(2, transitions.size());
            assertEquals(TransitionType.ENTER, transitions.get(0).type());
            assertEquals(TransitionType.EXIT, transitions.get(1).type());
            assertEquals(territory.id(), transitions.get(0).territory().id());
            assertEquals(territory.id(), transitions.get(1).territory().id());
        }
    }

    @Test
    void teleportUsesDestinationAndWorldChangeExitsOldTerritories() throws Exception {
        try (var storage = storage("teleport-world-change.db")) {
            UUID firstWorld = registerWorld(storage, "world");
            UUID secondWorld = registerWorld(storage, "world_nether");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            var territory = territories.create(new TerritoryDraft(
                    firstWorld, null, "safe", "Безопасная зона", 0,
                    new CuboidGeometry(0, 20, 0, 100, 0, 20)
            )).toCompletableFuture().get();
            territories.create(new TerritoryDraft(
                    secondWorld, null, "dungeon", "Подземелье", 0,
                    new CuboidGeometry(0, 20, 0, 100, 0, 20)
            )).toCompletableFuture().get();
            var engine = new TerritoryTransitionEngine(queries);
            UUID playerId = UUID.randomUUID();

            engine.initialize(playerId, pos(firstWorld, 10, 64, 10), TransitionReason.JOIN);
            var transitions = engine.move(playerId, pos(secondWorld, 10, 64, 10), TransitionReason.TELEPORT);

            assertEquals(2, transitions.size());
            assertEquals(TransitionReason.WORLD_CHANGE, transitions.get(0).reason());
            assertEquals(TransitionType.EXIT, transitions.get(0).type());
            assertEquals(territory.id(), transitions.get(0).territory().id());
            assertEquals(TransitionType.ENTER, transitions.get(1).type());
        }
    }

    @Test
    void forgetAndClearReleasePlayerState() throws Exception {
        try (var storage = storage("cleanup.db")) {
            UUID worldId = registerWorld(storage);
            var queries = new SpatialQueryService(new SqliteTerritoryRepository(storage));
            queries.refreshWorld(worldId).toCompletableFuture().get();
            var engine = new TerritoryTransitionEngine(queries);
            UUID playerId = UUID.randomUUID();

            engine.initialize(playerId, pos(worldId, 0, 64, 0), TransitionReason.JOIN);
            assertEquals(1, engine.trackedPlayers());
            engine.forget(playerId);
            assertEquals(0, engine.trackedPlayers());
            engine.initialize(playerId, pos(worldId, 0, 64, 0), TransitionReason.JOIN);
            engine.clear();
            assertEquals(0, engine.trackedPlayers());
        }
    }

    private SqliteStorageService storage(String name) {
        return new SqliteStorageService(temporaryDirectory.resolve(name));
    }

    private UUID registerWorld(SqliteStorageService storage) throws Exception {
        return registerWorld(storage, "world");
    }

    private UUID registerWorld(SqliteStorageService storage, String name) throws Exception {
        UUID worldId = UUID.randomUUID();
        new WorldRegistryService(storage).registerLoadedWorld(worldId, name).toCompletableFuture().get();
        return worldId;
    }

    private static PlayerPosition pos(UUID worldId, double x, int y, double z) {
        return new PlayerPosition(worldId, x, y, z);
    }
}

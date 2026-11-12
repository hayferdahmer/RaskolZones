package ru.raskol.zones.territory.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import ru.raskol.zones.territory.TerritoryStatus;
import ru.raskol.zones.territory.geometry.CuboidGeometry;
import ru.raskol.zones.territory.geometry.Point2;
import ru.raskol.zones.territory.geometry.PolygonGeometry;
import ru.raskol.zones.territory.type.TerritoryTypeRegistry;
import ru.raskol.zones.world.WorldRegistryService;

class SpatialQueryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void queryIsNotReadyUntilAnImmutableWorldSnapshotIsPublished() throws Exception {
        try (var storage = storage("readiness.db")) {
            UUID worldId = registerWorld(storage, "world");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);

            assertEquals(SpatialQueryStatus.NOT_READY, queries.query(worldId, 0, 64, 0).status());

            queries.refreshWorld(worldId).toCompletableFuture().get();

            SpatialQueryResult result = queries.query(worldId, 0, 64, 0);
            assertEquals(SpatialQueryStatus.READY, result.status());
            assertEquals(0, result.candidateCount());
            assertTrue(result.territories().isEmpty());
        }
    }

    @Test
    void findsOnlyExactMatchesAmongCellCandidatesAndKeepsWorldsIsolated() throws Exception {
        try (var storage = storage("world-isolation.db")) {
            UUID firstWorld = registerWorld(storage, "world");
            UUID secondWorld = registerWorld(storage, "world_nether");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);

            var inside = territories.create(new TerritoryDraft(
                    firstWorld, null, "city", "Город", 10,
                    new CuboidGeometry(-20, 20, -64, 120, -20, 20)
            )).toCompletableFuture().get();
            territories.create(new TerritoryDraft(
                    firstWorld, null, "resource", "Соседняя область", 0,
                    new CuboidGeometry(140, 180, -64, 120, -20, 20)
            )).toCompletableFuture().get();
            territories.create(new TerritoryDraft(
                    secondWorld, null, "city", "Другой мир", 0,
                    new CuboidGeometry(-20, 20, -64, 120, -20, 20)
            )).toCompletableFuture().get();

            SpatialQueryResult first = queries.query(firstWorld, 0, 64, 0);
            assertTrue(first.isReady());
            assertEquals(1, first.territories().size());
            assertEquals(inside.id(), first.territories().getFirst().id());
            assertEquals(1, queries.query(secondWorld, 0, 64, 0).territories().size());
        }
    }

    @Test
    void polygonUsesExactGeometryAndVerticalBoundsAfterBroadPhase() throws Exception {
        try (var storage = storage("polygon.db")) {
            UUID worldId = registerWorld(storage, "world");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            territories.create(new TerritoryDraft(
                    worldId, null, "dungeon", "Полигон", 0,
                    new PolygonGeometry(List.of(
                            new Point2(0, 0),
                            new Point2(100, 0),
                            new Point2(100, 100),
                            new Point2(0, 100)
                    ), 20, 80)
            )).toCompletableFuture().get();

            assertEquals(1, queries.query(worldId, 50, 50, 50).territories().size());
            assertTrue(queries.query(worldId, 50, 10, 50).territories().isEmpty());
            assertTrue(queries.query(worldId, 110, 50, 50).territories().isEmpty());
            assertEquals(1, queries.query(worldId, 0, 20, 50).territories().size());
        }
    }

    @Test
    void negativeCoordinatesAndCellBoundariesAreHandledConsistently() throws Exception {
        try (var storage = storage("negative-coordinates.db")) {
            UUID worldId = registerWorld(storage, "world");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            territories.create(new TerritoryDraft(
                    worldId, null, "region", "Отрицательные координаты", 0,
                    new CuboidGeometry(-128, -1, -10, 10, -128, -1)
            )).toCompletableFuture().get();

            assertEquals(1, queries.query(worldId, -128, 0, -128).territories().size());
            assertEquals(1, queries.query(worldId, -1, 0, -1).territories().size());
            assertTrue(queries.query(worldId, 0, 0, -1).territories().isEmpty());
        }
    }

    @Test
    void oversizedTerritoriesRemainQueryableWithoutExpandingMillionsOfCells() throws Exception {
        try (var storage = storage("oversized.db")) {
            UUID worldId = registerWorld(storage, "world");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            territories.create(new TerritoryDraft(
                    worldId, null, "continent", "Континент", 0,
                    new CuboidGeometry(-30_000_000, 30_000_000, -64, 320,
                            -30_000_000, 30_000_000)
            )).toCompletableFuture().get();

            SpatialQueryResult result = queries.query(worldId, 29_000_000, 100, -29_000_000);
            assertEquals(1, result.territories().size());
            assertTrue(result.candidateCount() <= 1);
        }
    }

    @Test
    void mutationsInvalidateAndRebuildSnapshotsAndArchivedTerritoriesDisappear() throws Exception {
        try (var storage = storage("invalidation.db")) {
            UUID worldId = registerWorld(storage, "world");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            var created = territories.create(new TerritoryDraft(
                    worldId, null, "city", "Город", 0,
                    new CuboidGeometry(0, 100, -64, 320, 0, 100)
            )).toCompletableFuture().get();

            assertEquals(1, queries.query(worldId, 50, 64, 50).territories().size());
            var updated = territories.update(created.id(), created.version(), new TerritoryDraft(
                    worldId, null, "city", "Город перемещён", 0,
                    new CuboidGeometry(200, 300, -64, 320, 200, 300)
            )).toCompletableFuture().get();

            assertEquals(0, queries.query(worldId, 50, 64, 50).territories().size());
            assertEquals(1, queries.query(worldId, 250, 64, 250).territories().size());

            var archived = territories.archive(updated.id(), updated.version()).toCompletableFuture().get();
            assertEquals(TerritoryStatus.ARCHIVED, archived.status());
            assertTrue(queries.query(worldId, 250, 64, 250).territories().isEmpty());
        }
    }

    @Test
    void worldUnloadInvalidatesRuntimeIndexWithoutDeletingPersistentTerritories() throws Exception {
        try (var storage = storage("unload.db")) {
            UUID worldId = registerWorld(storage, "world");
            var repository = new SqliteTerritoryRepository(storage);
            var queries = new SpatialQueryService(repository);
            var territories = new TerritoryService(repository, TerritoryTypeRegistry.standard(), queries);
            var created = territories.create(new TerritoryDraft(
                    worldId, null, "region", "Регион", 0,
                    new CuboidGeometry(0, 100, -64, 320, 0, 100)
            )).toCompletableFuture().get();

            queries.unloadWorld(worldId);
            assertEquals(SpatialQueryStatus.NOT_READY, queries.query(worldId, 50, 64, 50).status());
            assertTrue(repository.find(created.id()).toCompletableFuture().get().isPresent());

            queries.refreshWorld(worldId).toCompletableFuture().get();
            assertEquals(1, queries.query(worldId, 50, 64, 50).territories().size());
        }
    }

    private SqliteStorageService storage(String name) {
        return new SqliteStorageService(temporaryDirectory.resolve(name));
    }

    private UUID registerWorld(SqliteStorageService storage, String name) throws Exception {
        UUID worldId = UUID.randomUUID();
        new WorldRegistryService(storage).registerLoadedWorld(worldId, name).toCompletableFuture().get();
        return worldId;
    }
}

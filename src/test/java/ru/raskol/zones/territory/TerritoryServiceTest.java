package ru.raskol.zones.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raskol.zones.foundation.storage.SqliteStorageService;
import ru.raskol.zones.territory.geometry.CuboidGeometry;
import ru.raskol.zones.territory.geometry.Point2;
import ru.raskol.zones.territory.geometry.PolygonGeometry;
import ru.raskol.zones.territory.type.TerritoryTypeDefinition;
import ru.raskol.zones.territory.type.TerritoryTypeRegistry;
import ru.raskol.zones.world.WorldRegistryService;

class TerritoryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReadsCuboidTerritory() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("cuboid.db"))) {
            UUID worldId = registerWorld(storage, "world");
            var service = service(storage);

            Territory created = service.create(new TerritoryDraft(
                    worldId, null, "region", "Северный регион", 10,
                    new CuboidGeometry(-100, 100, -64, 320, -100, 100)
            )).toCompletableFuture().get();

            Territory loaded = service.find(created.id()).toCompletableFuture().get().orElseThrow();
            assertEquals(created.id(), loaded.id());
            assertEquals(worldId, loaded.worldId());
            assertEquals("Северный регион", loaded.name());
            assertEquals(0, loaded.version());
            assertEquals(created.geometry(), loaded.geometry());
        }
    }

    @Test
    void persistsPolygonVerticesAndVerticalBounds() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("polygon.db"))) {
            UUID worldId = registerWorld(storage, "world");
            var service = service(storage);
            var polygon = new PolygonGeometry(java.util.List.of(
                    new Point2(0, 0),
                    new Point2(20, 0),
                    new Point2(15, 20),
                    new Point2(0, 15)
            ), -20, 180);

            Territory created = service.create(new TerritoryDraft(
                    worldId, null, "dungeon", "Старая шахта", 5, polygon
            )).toCompletableFuture().get();

            Territory loaded = service.find(created.id()).toCompletableFuture().get().orElseThrow();
            assertEquals(polygon, loaded.geometry());
            assertTrue(loaded.geometry().contains(5, 50, 5));
            assertEquals(-20, loaded.geometry().minY());
            assertEquals(180, loaded.geometry().maxY());
        }
    }

    @Test
    void validatesParentWorldAndPreventsHierarchyCycles() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("hierarchy.db"))) {
            UUID firstWorld = registerWorld(storage, "world");
            UUID secondWorld = registerWorld(storage, "world_nether");
            var service = service(storage);
            var geometry = new CuboidGeometry(0, 100, 0, 100, 0, 100);

            Territory parent = service.create(new TerritoryDraft(
                    firstWorld, null, "region", "Материк", 0, geometry
            )).toCompletableFuture().get();
            Territory otherWorldParent = service.create(new TerritoryDraft(
                    secondWorld, null, "region", "Иной мир", 0, geometry
            )).toCompletableFuture().get();

            var wrongWorld = service.create(new TerritoryDraft(
                    firstWorld, otherWorldParent.id(), "city", "Чужой город", 0, geometry
            )).toCompletableFuture();
            ExecutionException mismatch = assertThrows(ExecutionException.class, wrongWorld::get);
            assertEquals(TerritoryException.Code.PARENT_WORLD_MISMATCH,
                    ((TerritoryException) mismatch.getCause()).code());

            Territory child = service.create(new TerritoryDraft(
                    firstWorld, parent.id(), "city", "Город", 0, geometry
            )).toCompletableFuture().get();
            var archiveParent = service.archive(parent.id(), parent.version()).toCompletableFuture();
            ExecutionException activeChildren = assertThrows(ExecutionException.class, archiveParent::get);
            assertEquals(TerritoryException.Code.ACTIVE_CHILDREN,
                    ((TerritoryException) activeChildren.getCause()).code());

            var cycle = service.update(parent.id(), parent.version(), new TerritoryDraft(
                    firstWorld, child.id(), "region", "Материк", 0, geometry
            )).toCompletableFuture();
            ExecutionException cycleFailure = assertThrows(ExecutionException.class, cycle::get);
            assertEquals(TerritoryException.Code.HIERARCHY_CYCLE,
                    ((TerritoryException) cycleFailure.getCause()).code());
        }
    }

    @Test
    void optimisticVersionRejectsStaleUpdateAndArchivePreservesRecord() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("versions.db"))) {
            UUID worldId = registerWorld(storage, "world");
            var service = service(storage);
            var geometry = new CuboidGeometry(0, 10, 0, 100, 0, 10);
            Territory created = service.create(new TerritoryDraft(
                    worldId, null, "region", "Старое название", 1, geometry
            )).toCompletableFuture().get();

            Territory updated = service.update(created.id(), 0, new TerritoryDraft(
                    worldId, null, "region", "Новое название", 2, geometry
            )).toCompletableFuture().get();
            assertEquals(1, updated.version());
            assertNotEquals(created.name(), updated.name());

            var stale = service.update(created.id(), 0, new TerritoryDraft(
                    worldId, null, "region", "Устаревшая запись", 3, geometry
            )).toCompletableFuture();
            ExecutionException conflict = assertThrows(ExecutionException.class, stale::get);
            assertEquals(TerritoryException.Code.VERSION_CONFLICT,
                    ((TerritoryException) conflict.getCause()).code());

            Territory archived = service.archive(updated.id(), 1).toCompletableFuture().get();
            assertEquals(TerritoryStatus.ARCHIVED, archived.status());
            assertEquals(2, archived.version());
            assertEquals(TerritoryStatus.ARCHIVED,
                    service.find(updated.id()).toCompletableFuture().get().orElseThrow().status());
        }
    }

    @Test
    void rejectsUnknownTypeButAllowsRegisteredCustomType() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("custom-types.db"))) {
            UUID worldId = registerWorld(storage, "world");
            var types = TerritoryTypeRegistry.standard();
            var service = new TerritoryService(new SqliteTerritoryRepository(storage), types);
            var draft = new TerritoryDraft(
                    worldId, null, "hidden_grove", "Тайная роща", 0,
                    new CuboidGeometry(0, 10, 0, 100, 0, 10)
            );

            assertThrows(TerritoryException.class, () -> service.create(draft));
            types.registerCustom(new TerritoryTypeDefinition(
                    "hidden_grove", "Тайная роща", "secret", false
            ));
            assertEquals("hidden_grove", service.create(draft).toCompletableFuture().get().typeId());
        }
    }

    @Test
    void listsTerritoriesInWorldAndDoesNotMixWorlds() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("world-list.db"))) {
            UUID firstWorld = registerWorld(storage, "world");
            UUID secondWorld = registerWorld(storage, "world_nether");
            var service = service(storage);
            var geometry = new CuboidGeometry(0, 10, 0, 100, 0, 10);

            service.create(new TerritoryDraft(firstWorld, null, "region", "A", 0, geometry))
                    .toCompletableFuture().get();
            service.create(new TerritoryDraft(firstWorld, null, "region", "B", 0, geometry))
                    .toCompletableFuture().get();
            service.create(new TerritoryDraft(secondWorld, null, "region", "C", 0, geometry))
                    .toCompletableFuture().get();

            assertEquals(2, service.listByWorld(firstWorld).toCompletableFuture().get().size());
            assertEquals(1, service.listByWorld(secondWorld).toCompletableFuture().get().size());
        }
    }

    private UUID registerWorld(SqliteStorageService storage, String name) throws Exception {
        UUID worldId = UUID.randomUUID();
        new WorldRegistryService(storage).registerLoadedWorld(worldId, name).toCompletableFuture().get();
        return worldId;
    }

    private TerritoryService service(SqliteStorageService storage) {
        return new TerritoryService(
                new SqliteTerritoryRepository(storage),
                TerritoryTypeRegistry.standard()
        );
    }
}

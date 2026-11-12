package ru.raskol.zones.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raskol.zones.foundation.storage.SqliteStorageService;

class WorldRegistryServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void registersWorldUpdatesNameAndPreservesIdentityAndFirstSeenTime() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("worlds.db"))) {
            var registry = new WorldRegistryService(storage);
            UUID worldId = UUID.randomUUID();

            registry.registerLoadedWorld(worldId, "world").toCompletableFuture().get();
            WorldRecord first = registry.find(worldId).toCompletableFuture().get().orElseThrow();

            registry.registerLoadedWorld(worldId, "world-renamed").toCompletableFuture().get();
            WorldRecord renamed = registry.find(worldId).toCompletableFuture().get().orElseThrow();

            assertEquals(worldId, renamed.worldId());
            assertEquals("world-renamed", renamed.name());
            assertEquals(first.firstSeenAt(), renamed.firstSeenAt());
            assertEquals(WorldAvailability.LOADED, renamed.availability());
            assertTrue(renamed.lastSeenAt().compareTo(first.lastSeenAt()) >= 0);
        }
    }

    @Test
    void unloadKeepsWorldRecordAndReloadRestoresAvailability() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("world-lifecycle.db"))) {
            var registry = new WorldRegistryService(storage);
            UUID worldId = UUID.randomUUID();
            registry.registerLoadedWorld(worldId, "survival").toCompletableFuture().get();

            assertTrue(registry.markWorldUnloaded(worldId).toCompletableFuture().get());
            WorldRecord unloaded = registry.find(worldId).toCompletableFuture().get().orElseThrow();
            assertEquals(WorldAvailability.UNLOADED, unloaded.availability());

            assertFalse(registry.markWorldUnloaded(UUID.randomUUID()).toCompletableFuture().get());
            assertEquals(1, registry.listAll().toCompletableFuture().get().size());

            registry.registerLoadedWorld(worldId, "survival").toCompletableFuture().get();
            WorldRecord loaded = registry.find(worldId).toCompletableFuture().get().orElseThrow();
            assertEquals(WorldAvailability.LOADED, loaded.availability());
        }
    }
}

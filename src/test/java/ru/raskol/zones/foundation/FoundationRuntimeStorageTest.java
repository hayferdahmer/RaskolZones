package ru.raskol.zones.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raskol.zones.foundation.config.PluginSettings;
import ru.raskol.zones.foundation.health.HealthService;
import ru.raskol.zones.foundation.health.HealthStatus;
import ru.raskol.zones.foundation.storage.SqliteStorageService;

class FoundationRuntimeStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void becomesReadyOnlyAfterStorageInitializationAndStopsAsynchronously() throws Exception {
        var health = new HealthService();
        Path database = temporaryDirectory.resolve("runtime/raskolzones.db");
        var runtime = new FoundationRuntime(
                PluginSettings::defaults,
                health,
                Logger.getLogger("RaskolZonesStorageTest"),
                new SqliteStorageService(database)
        );

        var starting = runtime.startAsync();
        assertEquals(HealthStatus.STARTING, health.snapshot().status());
        starting.toCompletableFuture().get();

        assertEquals(HealthStatus.READY, health.snapshot().status());
        assertTrue(Files.isRegularFile(database));

        runtime.stopAsync().toCompletableFuture().get();
        assertEquals(HealthStatus.STOPPED, health.snapshot().status());
        assertThrows(IllegalStateException.class, runtime::settings);
    }

    @Test
    void marksRuntimeFailedWhenStorageInitializationFails() throws Exception {
        Path database = temporaryDirectory.resolve("newer.db");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }

        var health = new HealthService();
        var runtime = new FoundationRuntime(
                PluginSettings::defaults,
                health,
                Logger.getLogger("RaskolZonesStorageTest"),
                new SqliteStorageService(database)
        );

        var startup = runtime.startAsync().toCompletableFuture();
        assertThrows(ExecutionException.class, startup::get);
        assertEquals(HealthStatus.FAILED, health.snapshot().status());
        runtime.stopAsync().toCompletableFuture().get();
        assertEquals(HealthStatus.FAILED, health.snapshot().status());
    }
}

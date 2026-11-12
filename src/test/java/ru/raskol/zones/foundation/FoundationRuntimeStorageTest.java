package ru.raskol.zones.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raskol.zones.foundation.config.PluginSettings;
import ru.raskol.zones.foundation.health.HealthService;
import ru.raskol.zones.foundation.health.HealthStatus;
import ru.raskol.zones.foundation.storage.SqlWork;
import ru.raskol.zones.foundation.storage.SqliteStorageService;
import ru.raskol.zones.foundation.storage.StorageService;

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
    void doesNotBecomeReadyUntilRequiredStartupWorkCompletes() throws Exception {
        var health = new HealthService();
        var requiredWork = new CompletableFuture<Void>();
        var runtime = new FoundationRuntime(
                PluginSettings::defaults,
                health,
                Logger.getLogger("RaskolZonesStorageTest"),
                new SqliteStorageService(temporaryDirectory.resolve("required-work.db"))
        );

        var startup = runtime.startAsync(requiredWork);
        assertEquals(HealthStatus.STARTING, health.snapshot().status());

        requiredWork.complete(null);
        startup.toCompletableFuture().get();

        assertEquals(HealthStatus.READY, health.snapshot().status());
        runtime.stopAsync().toCompletableFuture().get();
    }

    @Test
    void shutdownDuringInitializationIsSafeAndIdempotent() throws Exception {
        var initialization = new CompletableFuture<Void>();
        var closing = new CompletableFuture<Void>();
        var closeCalls = new AtomicInteger();
        StorageService storage = new StorageService() {
            @Override
            public CompletionStage<Void> initialize() {
                return initialization;
            }

            @Override
            public <T> CompletionStage<T> transaction(SqlWork<T> work) {
                return CompletableFuture.failedFuture(new IllegalStateException("not initialized"));
            }

            @Override
            public CompletionStage<Void> closeAsync() {
                closeCalls.incrementAndGet();
                return closing;
            }

            @Override
            public void close() {
                closeCalls.incrementAndGet();
            }
        };

        var health = new HealthService();
        var runtime = new FoundationRuntime(
                PluginSettings::defaults,
                health,
                Logger.getLogger("RaskolZonesStorageTest"),
                storage
        );

        var startup = runtime.startAsync();
        var shutdown = runtime.stopAsync();
        var repeatedShutdown = runtime.stopAsync();

        assertSame(shutdown, repeatedShutdown);
        assertEquals(1, closeCalls.get());
        assertEquals(HealthStatus.STOPPING, health.snapshot().status());

        initialization.complete(null);
        closing.complete(null);
        shutdown.toCompletableFuture().get();
        startup.toCompletableFuture().get();

        assertEquals(HealthStatus.STOPPED, health.snapshot().status());
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

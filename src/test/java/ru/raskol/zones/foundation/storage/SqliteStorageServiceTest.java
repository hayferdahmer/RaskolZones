package ru.raskol.zones.foundation.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStorageServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesVersionedSchema() throws Exception {
        Path database = temporaryDirectory.resolve("data/raskolzones.db");
        try (var storage = new SqliteStorageService(database)) {
            storage.initialize().toCompletableFuture().get();
            int version = storage.transaction(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("PRAGMA user_version")) {
                    result.next();
                    return result.getInt(1);
                }
            }).toCompletableFuture().get();
            assertEquals(2, version);
        }
    }

    @Test
    void migratesExistingVersionOneDatabaseToWorldRegistry() throws Exception {
        Path database = temporaryDirectory.resolve("legacy-v1.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE schema_migrations (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE plugin_metadata (
                        metadata_key TEXT PRIMARY KEY,
                        metadata_value TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("PRAGMA user_version = 1");
        }

        try (var storage = new SqliteStorageService(database)) {
            storage.initialize().toCompletableFuture().get();
            int version = storage.transaction(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("PRAGMA user_version")) {
                    result.next();
                    return result.getInt(1);
                }
            }).toCompletableFuture().get();
            assertEquals(2, version);

            int tableCount = storage.transaction(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                                     + "AND name = 'world_registry'")) {
                    result.next();
                    return result.getInt(1);
                }
            }).toCompletableFuture().get();
            assertEquals(1, tableCount);
        }
    }

    @Test
    void rollsBackFailedTransactions() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("rollback.db"))) {
            storage.initialize().toCompletableFuture().get();
            var failed = storage.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE should_rollback(id INTEGER PRIMARY KEY)");
                }
                throw new SQLException("Имитированная ошибка");
            }).toCompletableFuture();
            assertThrows(ExecutionException.class, failed::get);

            int tableCount = storage.transaction(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                                     + "AND name = 'should_rollback'")) {
                    result.next();
                    return result.getInt(1);
                }
            }).toCompletableFuture().get();
            assertEquals(0, tableCount);
        }
    }

    @Test
    void closeWaitsForTransactionsAcceptedBeforeShutdown() throws Exception {
        Path database = temporaryDirectory.resolve("shutdown-drain.db");
        var storage = new SqliteStorageService(database);

        var transaction = storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO plugin_metadata(metadata_key, metadata_value, updated_at)
                    VALUES ('shutdown-check', 'persisted', '2026-11-12T00:00:00Z')
                    """)) {
                statement.executeUpdate();
            }
            return null;
        });
        var closing = storage.closeAsync();

        transaction.toCompletableFuture().get();
        closing.toCompletableFuture().get();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT metadata_value FROM plugin_metadata
                     WHERE metadata_key = 'shutdown-check'
                     """)) {
            assertTrue(result.next());
            assertEquals("persisted", result.getString(1));
        }
    }

    @Test
    void drainsQueuedTransactionsInSubmissionOrderDuringShutdown() throws Exception {
        Path database = temporaryDirectory.resolve("queued-shutdown.db");
        var storage = new SqliteStorageService(database);

        var insert = storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO plugin_metadata(metadata_key, metadata_value, updated_at)
                    VALUES ('ordered-check', 'first', '2026-11-12T00:00:00Z')
                    """)) {
                statement.executeUpdate();
            }
            return null;
        });
        var update = storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE plugin_metadata
                    SET metadata_value = 'second'
                    WHERE metadata_key = 'ordered-check'
                    """)) {
                return statement.executeUpdate();
            }
        });
        var closing = storage.closeAsync();

        insert.toCompletableFuture().get();
        assertEquals(1, update.toCompletableFuture().get());
        closing.toCompletableFuture().get();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT metadata_value FROM plugin_metadata
                     WHERE metadata_key = 'ordered-check'
                     """)) {
            assertTrue(result.next());
            assertEquals("second", result.getString(1));
        }
    }

    @Test
    void asynchronousCloseIsIdempotent() throws Exception {
        var storage = new SqliteStorageService(temporaryDirectory.resolve("idempotent-close.db"));

        var firstClose = storage.closeAsync();
        var secondClose = storage.closeAsync();

        assertSame(firstClose, secondClose);
        firstClose.toCompletableFuture().get();
    }

    @Test
    void refusesDatabaseWithNewerSchemaVersion() throws Exception {
        Path database = temporaryDirectory.resolve("newer.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }
        try (var storage = new SqliteStorageService(database)) {
            var initialization = storage.initialize().toCompletableFuture();
            ExecutionException exception = assertThrows(ExecutionException.class, initialization::get);
            assertTrue(exception.getCause().getMessage().contains("Не удалось инициализировать"));
        }
    }
}

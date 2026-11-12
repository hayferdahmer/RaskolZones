package ru.raskol.zones.foundation.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertEquals(1, version);
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

package ru.raskol.zones.foundation.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SqliteStorageService implements StorageService {
    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private final Path databasePath;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletableFuture<Void> initialization;

    public SqliteStorageService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath().normalize();
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RaskolZones-SQLite");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized CompletionStage<Void> initialize() {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Хранилище уже закрыто"));
        }
        if (initialization != null) {
            return initialization;
        }
        initialization = CompletableFuture.runAsync(this::initializeDatabase, executor);
        return initialization;
    }

    @Override
    public <T> CompletionStage<T> transaction(SqlWork<T> work) {
        Objects.requireNonNull(work, "work");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Хранилище уже закрыто"));
        }
        return initialize().thenCompose(ignored -> {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Хранилище уже закрыто"));
            }
            try {
                return CompletableFuture.supplyAsync(() -> executeTransaction(work), executor);
            } catch (RejectedExecutionException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        });
    }

    private void initializeDatabase() {
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new StorageException("Не удалось создать каталог SQLite-хранилища", exception);
        }

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            int currentVersion;
            try (var result = statement.executeQuery("PRAGMA user_version")) {
                if (!result.next()) {
                    throw new SQLException("SQLite не вернула версию схемы");
                }
                currentVersion = result.getInt(1);
            }
            if (currentVersion > CURRENT_SCHEMA_VERSION) {
                throw new SQLException("Версия схемы БД " + currentVersion
                        + " новее поддерживаемой " + CURRENT_SCHEMA_VERSION);
            }
            if (currentVersion < 1) {
                applyInitialMigration(connection);
                currentVersion = 1;
            }
            if (currentVersion < 2) {
                applyWorldRegistryMigration(connection);
            }
        } catch (SQLException exception) {
            throw new StorageException("Не удалось инициализировать SQLite-хранилище", exception);
        }
    }

    private void applyWorldRegistryMigration(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE world_registry (
                        world_uuid TEXT PRIMARY KEY,
                        world_name TEXT NOT NULL CHECK (length(trim(world_name)) > 0),
                        first_seen_at TEXT NOT NULL,
                        last_seen_at TEXT NOT NULL,
                        availability TEXT NOT NULL CHECK (availability IN ('LOADED', 'UNLOADED'))
                    )
                    """);
            statement.execute("""
                    CREATE INDEX idx_world_registry_availability
                    ON world_registry(availability)
                    """);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, applied_at) VALUES (?, ?, ?)")) {
                insert.setInt(1, 2);
                insert.setString(2, "world_registry");
                insert.setString(3, Instant.now().toString());
                insert.executeUpdate();
            }
            statement.execute("PRAGMA user_version = 2");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyInitialMigration(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
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
            try (var insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, applied_at) VALUES (?, ?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "initial_foundation");
                insert.setString(3, Instant.now().toString());
                insert.executeUpdate();
            }
            statement.execute("PRAGMA user_version = 1");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private <T> T executeTransaction(SqlWork<T> work) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new StorageException("Транзакция SQLite завершилась ошибкой", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLIS);
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Thread shutdownThread = new Thread(() -> {
            try {
                close();
                completion.complete(null);
            } catch (RuntimeException exception) {
                completion.completeExceptionally(exception);
            }
        }, "RaskolZones-SQLite-Shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
        return completion;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("SQLite executor не завершился за отведённое время");
                }
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание завершения SQLite executor прервано", exception);
        }
    }
}

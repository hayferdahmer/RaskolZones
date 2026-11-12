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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SqliteStorageService implements StorageService {
    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private final Path databasePath;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletableFuture<Void> initialization;
    private CompletableFuture<Void> pendingOperations = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> closing;

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
    public synchronized <T> CompletionStage<T> transaction(SqlWork<T> work) {
        Objects.requireNonNull(work, "work");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Хранилище уже закрыто"));
        }

        CompletableFuture<Void> ready = initialize().toCompletableFuture();
        CompletableFuture<T> operation = pendingOperations
                .thenCompose(ignored -> ready)
                .thenCompose(ignored -> CompletableFuture.supplyAsync(
                        () -> executeTransaction(work),
                        executor
                ))
                .toCompletableFuture();
        pendingOperations = operation.handle((ignored, failure) -> null);
        return operation;
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
                currentVersion = 2;
            }
            if (currentVersion < 3) {
                applyTerritoryCoreMigration(connection);
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

    private void applyTerritoryCoreMigration(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE territories (
                        territory_uuid TEXT PRIMARY KEY,
                        world_uuid TEXT NOT NULL,
                        parent_uuid TEXT,
                        type_id TEXT NOT NULL CHECK (length(trim(type_id)) BETWEEN 1 AND 64),
                        name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 128),
                        weight INTEGER NOT NULL,
                        lifecycle_status TEXT NOT NULL CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
                        version INTEGER NOT NULL CHECK (version >= 0),
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        CHECK (parent_uuid IS NULL OR parent_uuid <> territory_uuid),
                        FOREIGN KEY (world_uuid) REFERENCES world_registry(world_uuid) ON DELETE RESTRICT,
                        FOREIGN KEY (parent_uuid) REFERENCES territories(territory_uuid) ON DELETE RESTRICT
                    )
                    """);
            statement.execute("""
                    CREATE INDEX idx_territories_world
                    ON territories(world_uuid, lifecycle_status, name COLLATE NOCASE)
                    """);
            statement.execute("""
                    CREATE INDEX idx_territories_parent
                    ON territories(parent_uuid)
                    """);
            statement.execute("""
                    CREATE TABLE territory_geometry (
                        territory_uuid TEXT PRIMARY KEY,
                        shape_type TEXT NOT NULL CHECK (shape_type IN ('CUBOID', 'POLYGON')),
                        min_x REAL NOT NULL,
                        max_x REAL NOT NULL,
                        min_z REAL NOT NULL,
                        max_z REAL NOT NULL,
                        min_y INTEGER NOT NULL,
                        max_y INTEGER NOT NULL,
                        CHECK (min_x <= max_x),
                        CHECK (min_z <= max_z),
                        CHECK (min_y <= max_y),
                        FOREIGN KEY (territory_uuid) REFERENCES territories(territory_uuid)
                            ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE territory_vertices (
                        territory_uuid TEXT NOT NULL,
                        vertex_index INTEGER NOT NULL CHECK (vertex_index >= 0),
                        x REAL NOT NULL,
                        z REAL NOT NULL,
                        PRIMARY KEY (territory_uuid, vertex_index),
                        FOREIGN KEY (territory_uuid) REFERENCES territory_geometry(territory_uuid)
                            ON DELETE CASCADE
                    )
                    """);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, applied_at) VALUES (?, ?, ?)")) {
                insert.setInt(1, 3);
                insert.setString(2, "territory_core");
                insert.setString(3, Instant.now().toString());
                insert.executeUpdate();
            }
            statement.execute("PRAGMA user_version = 3");
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
    public synchronized CompletionStage<Void> closeAsync() {
        if (closing != null) {
            return closing;
        }

        closing = new CompletableFuture<>();
        Thread shutdownThread = new Thread(() -> {
            try {
                close();
                closing.complete(null);
            } catch (RuntimeException exception) {
                closing.completeExceptionally(exception);
            }
        }, "RaskolZones-SQLite-Shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
        return closing;
    }

    @Override
    public void close() {
        CompletableFuture<Void> pending;
        synchronized (this) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            CompletableFuture<Void> initializationResult = initialization == null
                    ? CompletableFuture.completedFuture(null)
                    : initialization.handle((ignored, failure) -> null);
            pending = CompletableFuture.allOf(initializationResult, pendingOperations);
        }

        try {
            pending.get();
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Ошибка завершения операций SQLite", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание операций SQLite прервано", exception);
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

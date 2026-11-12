package ru.raskol.zones.world;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import ru.raskol.zones.foundation.storage.StorageService;

public final class WorldRegistryService {

    private final StorageService storage;

    public WorldRegistryService(StorageService storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public CompletionStage<Void> registerLoadedWorld(UUID worldId, String worldName) {
        Objects.requireNonNull(worldId, "worldId");
        String normalizedName = requireName(worldName);
        String now = Instant.now().toString();

        return storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO world_registry(
                        world_uuid, world_name, first_seen_at, last_seen_at, availability
                    ) VALUES (?, ?, ?, ?, 'LOADED')
                    ON CONFLICT(world_uuid) DO UPDATE SET
                        world_name = excluded.world_name,
                        last_seen_at = excluded.last_seen_at,
                        availability = 'LOADED'
                    """)) {
                statement.setString(1, worldId.toString());
                statement.setString(2, normalizedName);
                statement.setString(3, now);
                statement.setString(4, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletionStage<Boolean> markWorldUnloaded(UUID worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE world_registry
                    SET availability = 'UNLOADED'
                    WHERE world_uuid = ?
                    """)) {
                statement.setString(1, worldId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletionStage<Optional<WorldRecord>> find(UUID worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT world_uuid, world_name, first_seen_at, last_seen_at, availability
                    FROM world_registry
                    WHERE world_uuid = ?
                    """)) {
                statement.setString(1, worldId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readRecord(result)) : Optional.empty();
                }
            }
        });
    }

    public CompletionStage<List<WorldRecord>> listAll() {
        return storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT world_uuid, world_name, first_seen_at, last_seen_at, availability
                    FROM world_registry
                    ORDER BY world_name COLLATE NOCASE, world_uuid
                    """);
                 ResultSet result = statement.executeQuery()) {
                var worlds = new java.util.ArrayList<WorldRecord>();
                while (result.next()) {
                    worlds.add(readRecord(result));
                }
                return List.copyOf(worlds);
            }
        });
    }

    private static WorldRecord readRecord(ResultSet result) throws SQLException {
        return new WorldRecord(
                UUID.fromString(result.getString("world_uuid")),
                result.getString("world_name"),
                Instant.parse(result.getString("first_seen_at")),
                Instant.parse(result.getString("last_seen_at")),
                WorldAvailability.valueOf(result.getString("availability"))
        );
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "worldName");
        String normalized = name.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Имя мира не может быть пустым");
        }
        return normalized;
    }
}

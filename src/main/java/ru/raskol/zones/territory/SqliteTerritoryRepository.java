package ru.raskol.zones.territory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import ru.raskol.zones.foundation.storage.StorageService;
import ru.raskol.zones.territory.geometry.CuboidGeometry;
import ru.raskol.zones.territory.geometry.Point2;
import ru.raskol.zones.territory.geometry.PolygonGeometry;
import ru.raskol.zones.territory.geometry.TerritoryGeometry;

public final class SqliteTerritoryRepository implements TerritoryRepository {
    private final StorageService storage;

    public SqliteTerritoryRepository(StorageService storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<Territory> create(Territory territory) {
        Objects.requireNonNull(territory, "territory");
        if (territory.version() != 0 || territory.status() != TerritoryStatus.ACTIVE) {
            throw new IllegalArgumentException("Новая территория должна иметь версию 0 и статус ACTIVE");
        }

        return storage.transaction(connection -> {
            validateWorldAndParent(connection, territory.worldId(), territory.parentId(), territory.id());
            try (var statement = connection.prepareStatement("""
                    INSERT INTO territories(
                        territory_uuid, world_uuid, parent_uuid, type_id, name, weight,
                        lifecycle_status, version, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, territory.id().toString());
                statement.setString(2, territory.worldId().toString());
                statement.setString(3, territory.parentId() == null ? null : territory.parentId().toString());
                statement.setString(4, territory.typeId());
                statement.setString(5, territory.name());
                statement.setInt(6, territory.weight());
                statement.setString(7, territory.status().name());
                statement.setLong(8, territory.version());
                statement.setString(9, territory.createdAt().toString());
                statement.setString(10, territory.updatedAt().toString());
                statement.executeUpdate();
            }
            writeGeometry(connection, territory.id(), territory.geometry());
            return territory;
        });
    }

    @Override
    public CompletionStage<Optional<Territory>> find(UUID territoryId) {
        Objects.requireNonNull(territoryId, "territoryId");
        return storage.transaction(connection -> find(connection, territoryId));
    }

    @Override
    public CompletionStage<List<Territory>> listByWorld(UUID worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return storage.transaction(connection -> {
            List<UUID> ids = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT territory_uuid FROM territories
                    WHERE world_uuid = ?
                    ORDER BY name COLLATE NOCASE, territory_uuid
                    """)) {
                statement.setString(1, worldId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        ids.add(UUID.fromString(result.getString(1)));
                    }
                }
            }

            List<Territory> territories = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                find(connection, id).ifPresent(territories::add);
            }
            return List.copyOf(territories);
        });
    }

    @Override
    public CompletionStage<Territory> update(Territory territory, long expectedVersion) {
        Objects.requireNonNull(territory, "territory");
        if (expectedVersion < 0 || territory.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("Новая версия должна быть ровно на единицу больше ожидаемой");
        }

        return storage.transaction(connection -> {
            validateWorldAndParent(connection, territory.worldId(), territory.parentId(), territory.id());
            if (territory.status() == TerritoryStatus.ARCHIVED
                    && hasActiveChildren(connection, territory.id())) {
                throw new TerritoryException(TerritoryException.Code.ACTIVE_CHILDREN,
                        "Нельзя архивировать территорию, пока у неё есть активные дочерние территории");
            }
            int changed;
            try (var statement = connection.prepareStatement("""
                    UPDATE territories
                    SET parent_uuid = ?, type_id = ?, name = ?, weight = ?,
                        lifecycle_status = ?, version = ?, updated_at = ?
                    WHERE territory_uuid = ? AND version = ?
                    """)) {
                statement.setString(1, territory.parentId() == null ? null : territory.parentId().toString());
                statement.setString(2, territory.typeId());
                statement.setString(3, territory.name());
                statement.setInt(4, territory.weight());
                statement.setString(5, territory.status().name());
                statement.setLong(6, territory.version());
                statement.setString(7, territory.updatedAt().toString());
                statement.setString(8, territory.id().toString());
                statement.setLong(9, expectedVersion);
                changed = statement.executeUpdate();
            }
            if (changed != 1) {
                Optional<Long> currentVersion = findVersion(connection, territory.id());
                if (currentVersion.isEmpty()) {
                    throw new TerritoryException(TerritoryException.Code.NOT_FOUND,
                            "Территория не найдена: " + territory.id());
                }
                throw new TerritoryException(TerritoryException.Code.VERSION_CONFLICT,
                        "Территория уже изменена: ожидалась версия " + expectedVersion
                                + ", текущая версия " + currentVersion.get());
            }

            try (var statement = connection.prepareStatement(
                    "DELETE FROM territory_geometry WHERE territory_uuid = ?")) {
                statement.setString(1, territory.id().toString());
                statement.executeUpdate();
            }
            writeGeometry(connection, territory.id(), territory.geometry());
            return territory;
        });
    }

    private static boolean hasActiveChildren(Connection connection, UUID territoryId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM territories
                WHERE parent_uuid = ? AND lifecycle_status = 'ACTIVE'
                LIMIT 1
                """)) {
            statement.setString(1, territoryId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static Optional<Territory> find(Connection connection, UUID territoryId) throws SQLException {
        TerritoryRow row;
        try (var statement = connection.prepareStatement("""
                SELECT territory_uuid, world_uuid, parent_uuid, type_id, name, weight,
                       lifecycle_status, version, created_at, updated_at
                FROM territories WHERE territory_uuid = ?
                """)) {
            statement.setString(1, territoryId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String parent = result.getString("parent_uuid");
                row = new TerritoryRow(
                        UUID.fromString(result.getString("territory_uuid")),
                        UUID.fromString(result.getString("world_uuid")),
                        parent == null ? null : UUID.fromString(parent),
                        result.getString("type_id"),
                        result.getString("name"),
                        result.getInt("weight"),
                        TerritoryStatus.valueOf(result.getString("lifecycle_status")),
                        result.getLong("version"),
                        Instant.parse(result.getString("created_at")),
                        Instant.parse(result.getString("updated_at"))
                );
            }
        }
        TerritoryGeometry geometry = readGeometry(connection, row.id());
        return Optional.of(new Territory(
                row.id(), row.worldId(), row.parentId(), row.typeId(), row.name(), row.weight(),
                geometry, row.status(), row.version(), row.createdAt(), row.updatedAt()
        ));
    }

    private static Optional<Long> findVersion(Connection connection, UUID territoryId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT version FROM territories WHERE territory_uuid = ?")) {
            statement.setString(1, territoryId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getLong(1)) : Optional.empty();
            }
        }
    }

    private static void validateWorldAndParent(
            Connection connection,
            UUID worldId,
            UUID parentId,
            UUID territoryId
    ) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM world_registry WHERE world_uuid = ?")) {
            statement.setString(1, worldId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new TerritoryException(TerritoryException.Code.WORLD_NOT_FOUND,
                            "Мир отсутствует в World Registry: " + worldId);
                }
            }
        }

        if (parentId == null) {
            return;
        }
        if (parentId.equals(territoryId)) {
            throw new TerritoryException(TerritoryException.Code.HIERARCHY_CYCLE,
                    "Территория не может ссылаться на саму себя как на родителя");
        }

        Set<UUID> visited = new HashSet<>();
        UUID currentId = parentId;
        while (currentId != null) {
            if (currentId.equals(territoryId) || !visited.add(currentId)) {
                throw new TerritoryException(TerritoryException.Code.HIERARCHY_CYCLE,
                        "Обнаружен цикл в иерархии территорий");
            }

            try (var statement = connection.prepareStatement("""
                    SELECT world_uuid, parent_uuid, lifecycle_status
                    FROM territories WHERE territory_uuid = ?
                    """)) {
                statement.setString(1, currentId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new TerritoryException(TerritoryException.Code.PARENT_NOT_FOUND,
                                "Родительская территория не найдена: " + currentId);
                    }
                    UUID parentWorld = UUID.fromString(result.getString("world_uuid"));
                    if (!worldId.equals(parentWorld)) {
                        throw new TerritoryException(TerritoryException.Code.PARENT_WORLD_MISMATCH,
                                "Родительская территория принадлежит другому миру");
                    }
                    if (TerritoryStatus.valueOf(result.getString("lifecycle_status"))
                            != TerritoryStatus.ACTIVE) {
                        throw new TerritoryException(TerritoryException.Code.PARENT_ARCHIVED,
                                "Архивная территория не может быть родителем");
                    }
                    String nextParent = result.getString("parent_uuid");
                    currentId = nextParent == null ? null : UUID.fromString(nextParent);
                }
            }
        }
    }

    private static void writeGeometry(
            Connection connection,
            UUID territoryId,
            TerritoryGeometry geometry
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO territory_geometry(
                    territory_uuid, shape_type, min_x, max_x, min_z, max_z, min_y, max_y
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, territoryId.toString());
            statement.setString(2, geometry.shapeType());
            statement.setDouble(3, geometry.minX());
            statement.setDouble(4, geometry.maxX());
            statement.setDouble(5, geometry.minZ());
            statement.setDouble(6, geometry.maxZ());
            statement.setInt(7, geometry.minY());
            statement.setInt(8, geometry.maxY());
            statement.executeUpdate();
        }

        List<Point2> vertices = geometry.vertices();
        if (vertices.isEmpty()) {
            return;
        }
        try (var statement = connection.prepareStatement("""
                INSERT INTO territory_vertices(territory_uuid, vertex_index, x, z)
                VALUES (?, ?, ?, ?)
                """)) {
            for (int index = 0; index < vertices.size(); index++) {
                Point2 vertex = vertices.get(index);
                statement.setString(1, territoryId.toString());
                statement.setInt(2, index);
                statement.setDouble(3, vertex.x());
                statement.setDouble(4, vertex.z());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static TerritoryGeometry readGeometry(Connection connection, UUID territoryId) throws SQLException {
        String shapeType;
        double minX;
        double maxX;
        double minZ;
        double maxZ;
        int minY;
        int maxY;
        try (var statement = connection.prepareStatement("""
                SELECT shape_type, min_x, max_x, min_z, max_z, min_y, max_y
                FROM territory_geometry WHERE territory_uuid = ?
                """)) {
            statement.setString(1, territoryId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new TerritoryException(TerritoryException.Code.DATA_CORRUPTION,
                            "У территории отсутствует геометрия: " + territoryId);
                }
                shapeType = result.getString("shape_type");
                minX = result.getDouble("min_x");
                maxX = result.getDouble("max_x");
                minZ = result.getDouble("min_z");
                maxZ = result.getDouble("max_z");
                minY = result.getInt("min_y");
                maxY = result.getInt("max_y");
            }
        }

        if ("CUBOID".equals(shapeType)) {
            return new CuboidGeometry(minX, maxX, minY, maxY, minZ, maxZ);
        }
        if ("POLYGON".equals(shapeType)) {
            List<Point2> vertices = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT x, z FROM territory_vertices
                    WHERE territory_uuid = ? ORDER BY vertex_index
                    """)) {
                statement.setString(1, territoryId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        vertices.add(new Point2(result.getDouble("x"), result.getDouble("z")));
                    }
                }
            }
            return new PolygonGeometry(vertices, minY, maxY);
        }
        throw new TerritoryException(TerritoryException.Code.DATA_CORRUPTION,
                "Неизвестный тип геометрии " + shapeType + " у территории " + territoryId);
    }

    private record TerritoryRow(
            UUID id,
            UUID worldId,
            UUID parentId,
            String typeId,
            String name,
            int weight,
            TerritoryStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

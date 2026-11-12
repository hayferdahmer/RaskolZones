package ru.raskol.zones.territory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ru.raskol.zones.territory.geometry.TerritoryGeometry;

public record Territory(
        UUID id,
        UUID worldId,
        UUID parentId,
        String typeId,
        String name,
        int weight,
        TerritoryGeometry geometry,
        TerritoryStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public Territory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        typeId = typeId.strip().toLowerCase(java.util.Locale.ROOT);
        name = name.strip();
        if (!typeId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Некорректный идентификатор типа территории");
        }
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("Название территории должно содержать от 1 до 128 символов");
        }
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("Территория не может быть родителем самой себе");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Версия территории не может быть отрицательной");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Время изменения раньше времени создания");
        }
    }
}

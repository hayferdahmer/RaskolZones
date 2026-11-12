package ru.raskol.zones.territory;

import java.util.Objects;
import java.util.UUID;
import ru.raskol.zones.territory.geometry.TerritoryGeometry;

public record TerritoryDraft(
        UUID worldId,
        UUID parentId,
        String typeId,
        String name,
        int weight,
        TerritoryGeometry geometry
) {
    public TerritoryDraft {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(geometry, "geometry");
        typeId = typeId.strip().toLowerCase(java.util.Locale.ROOT);
        name = name.strip();
        if (!typeId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Некорректный идентификатор типа территории");
        }
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("Название территории должно содержать от 1 до 128 символов");
        }
    }
}

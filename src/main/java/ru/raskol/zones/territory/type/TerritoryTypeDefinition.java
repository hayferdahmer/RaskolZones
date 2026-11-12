package ru.raskol.zones.territory.type;

import java.util.Locale;
import java.util.Objects;

public record TerritoryTypeDefinition(
        String id,
        String displayName,
        String atlasCategory,
        boolean builtIn
) {
    public TerritoryTypeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(atlasCategory, "atlasCategory");
        id = id.strip().toLowerCase(Locale.ROOT);
        displayName = displayName.strip();
        atlasCategory = atlasCategory.strip().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Некорректный идентификатор типа территории");
        }
        if (displayName.isEmpty() || displayName.length() > 128 || atlasCategory.isEmpty()) {
            throw new IllegalArgumentException("Некорректный профиль типа территории");
        }
    }
}

package ru.raskol.zones.territory.transition;

import java.util.Objects;
import java.util.UUID;

public record PlayerPosition(UUID worldId, double x, int y, double z) {
    public PlayerPosition {
        Objects.requireNonNull(worldId, "worldId");
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Координаты игрока должны быть конечными числами");
        }
    }
}

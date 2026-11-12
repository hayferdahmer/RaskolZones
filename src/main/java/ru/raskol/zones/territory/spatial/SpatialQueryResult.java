package ru.raskol.zones.territory.spatial;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import ru.raskol.zones.territory.Territory;

public record SpatialQueryResult(
        UUID worldId,
        SpatialQueryStatus status,
        long snapshotVersion,
        int candidateCount,
        List<Territory> territories
) {
    public SpatialQueryResult {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(status, "status");
        territories = List.copyOf(territories);
        if (snapshotVersion < 0 || candidateCount < 0) {
            throw new IllegalArgumentException("Версия снимка и число кандидатов не могут быть отрицательными");
        }
        if (status == SpatialQueryStatus.NOT_READY && (!territories.isEmpty() || candidateCount != 0)) {
            throw new IllegalArgumentException("Недоступный снимок не может содержать результаты");
        }
    }

    public boolean isReady() {
        return status == SpatialQueryStatus.READY;
    }
}

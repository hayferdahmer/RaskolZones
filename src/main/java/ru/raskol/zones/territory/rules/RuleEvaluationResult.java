package ru.raskol.zones.territory.rules;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RuleEvaluationResult(
        UUID worldId,
        String actionKey,
        long spatialSnapshotVersion,
        RuleEvaluationStatus status,
        Optional<RuleResolutionResult> resolution
) {
    public RuleEvaluationResult {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(actionKey, "actionKey");
        Objects.requireNonNull(status, "status");
        resolution = Objects.requireNonNull(resolution, "resolution");
        if (spatialSnapshotVersion < 0) {
            throw new IllegalArgumentException("Версия пространственного снимка не может быть отрицательной");
        }
        if (status == RuleEvaluationStatus.RESOLVED && resolution.isEmpty()) {
            throw new IllegalArgumentException("Для RESOLVED требуется результат разрешения");
        }
    }
}

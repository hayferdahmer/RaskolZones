package ru.raskol.zones.territory.rules;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Неизменяемый контекст одного проверяемого игрового действия.
 * Bukkit-объекты в контекст не передаются: только безопасные значения.
 */
public record RuleEvaluationContext(
        UUID worldId,
        String actionKey,
        Optional<UUID> actorId,
        Optional<UUID> targetId,
        Optional<String> targetType,
        Optional<String> eventKey,
        double x,
        int y,
        double z
) {
    public RuleEvaluationContext {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(actionKey, "actionKey");
        actorId = Objects.requireNonNull(actorId, "actorId");
        targetId = Objects.requireNonNull(targetId, "targetId");
        targetType = normalizeOptional(targetType, "targetType");
        eventKey = normalizeOptional(eventKey, "eventKey");
        actionKey = actionKey.strip().toLowerCase(Locale.ROOT);
        if (!actionKey.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный ключ действия");
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Координаты должны быть конечными числами");
        }
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(item -> {
            String normalized = item.strip().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("Некорректное значение " + name);
            }
            return normalized;
        });
    }
}

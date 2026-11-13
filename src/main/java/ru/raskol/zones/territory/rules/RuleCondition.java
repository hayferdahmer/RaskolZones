package ru.raskol.zones.territory.rules;

import java.util.Objects;
import java.util.UUID;

/** Типизированные условия правил. Произвольные выражения и скрипты не поддерживаются. */
public sealed interface RuleCondition permits RuleCondition.ActorIs,
        RuleCondition.TargetIs, RuleCondition.TargetTypeIs,
        RuleCondition.EventIs, RuleCondition.WorldIs, RuleCondition.YRange {

    record ActorIs(UUID actorId) implements RuleCondition {
        public ActorIs { Objects.requireNonNull(actorId, "actorId"); }
    }

    record TargetIs(UUID targetId) implements RuleCondition {
        public TargetIs { Objects.requireNonNull(targetId, "targetId"); }
    }

    record TargetTypeIs(String targetType) implements RuleCondition {
        public TargetTypeIs {
            Objects.requireNonNull(targetType, "targetType");
            targetType = targetType.strip().toLowerCase(java.util.Locale.ROOT);
            if (!targetType.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("Некорректный тип цели");
            }
        }
    }

    record EventIs(String eventKey) implements RuleCondition {
        public EventIs {
            Objects.requireNonNull(eventKey, "eventKey");
            eventKey = eventKey.strip().toLowerCase(java.util.Locale.ROOT);
            if (!eventKey.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("Некорректный ключ события");
            }
        }
    }

    record WorldIs(UUID worldId) implements RuleCondition {
        public WorldIs { Objects.requireNonNull(worldId, "worldId"); }
    }

    record YRange(int minimum, int maximum) implements RuleCondition {
        public YRange {
            if (minimum > maximum) {
                throw new IllegalArgumentException("Минимальная высота выше максимальной");
            }
        }
    }
}

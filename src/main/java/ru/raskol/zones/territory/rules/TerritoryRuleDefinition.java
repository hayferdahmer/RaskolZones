package ru.raskol.zones.territory.rules;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record TerritoryRuleDefinition(
        String ruleId,
        UUID territoryId,
        String actionKey,
        RuleEffect effect,
        RuleSafetyClass safetyClass,
        RuleSpecificity specificity,
        String sourceId,
        boolean enabled,
        long version,
        Instant updatedAt,
        java.util.List<RuleCondition> conditions
) {
    public TerritoryRuleDefinition(
            String ruleId, UUID territoryId, String actionKey, RuleEffect effect,
            RuleSafetyClass safetyClass, RuleSpecificity specificity, String sourceId,
            boolean enabled, long version, Instant updatedAt
    ) {
        this(ruleId, territoryId, actionKey, effect, safetyClass, specificity,
                sourceId, enabled, version, updatedAt, java.util.List.of());
    }
    public TerritoryRuleDefinition {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(territoryId, "territoryId");
        Objects.requireNonNull(actionKey, "actionKey");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(safetyClass, "safetyClass");
        Objects.requireNonNull(specificity, "specificity");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        conditions = java.util.List.copyOf(Objects.requireNonNull(conditions, "conditions"));
        if (conditions.size() > RuleConditionEvaluator.MAX_CONDITIONS) {
            throw new IllegalArgumentException("Превышен лимит условий");
        }
        ruleId = ruleId.strip().toLowerCase(Locale.ROOT);
        actionKey = actionKey.strip().toLowerCase(Locale.ROOT);
        sourceId = sourceId.strip().toLowerCase(Locale.ROOT);
        if (!ruleId.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный стабильный ID правила");
        }
        if (!actionKey.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный ключ действия");
        }
        if (!sourceId.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный ID источника");
        }
        if (safetyClass == RuleSafetyClass.MANDATORY_RESTRICTION && effect != RuleEffect.DENY) {
            throw new IllegalArgumentException("Обязательное ограничение безопасности должно иметь эффект DENY");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Версия правила не может быть отрицательной");
        }
    }
}

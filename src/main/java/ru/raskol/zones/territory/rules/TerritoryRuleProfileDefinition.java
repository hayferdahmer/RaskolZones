package ru.raskol.zones.territory.rules;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record TerritoryRuleProfileDefinition(
        String profileId,
        String territoryTypeId,
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
    public TerritoryRuleProfileDefinition(
            String profileId, String territoryTypeId, String actionKey, RuleEffect effect,
            RuleSafetyClass safetyClass, RuleSpecificity specificity, String sourceId,
            boolean enabled, long version, Instant updatedAt
    ) {
        this(profileId, territoryTypeId, actionKey, effect, safetyClass, specificity,
                sourceId, enabled, version, updatedAt, java.util.List.of());
    }
    public TerritoryRuleProfileDefinition {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(territoryTypeId, "territoryTypeId");
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
        profileId = profileId.strip().toLowerCase(Locale.ROOT);
        territoryTypeId = territoryTypeId.strip().toLowerCase(Locale.ROOT);
        actionKey = actionKey.strip().toLowerCase(Locale.ROOT);
        sourceId = sourceId.strip().toLowerCase(Locale.ROOT);
        if (!profileId.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный стабильный ID профиля");
        }
        if (!territoryTypeId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Некорректный ID типа территории");
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
            throw new IllegalArgumentException("Версия профиля не может быть отрицательной");
        }
    }
}

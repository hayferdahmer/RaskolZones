package ru.raskol.zones.territory.rules;

import java.util.Locale;
import java.util.Objects;
import ru.raskol.zones.territory.Territory;

public record TerritoryRuleCandidate(
        String ruleId,
        Territory territory,
        RuleEffect effect,
        RuleSafetyClass safetyClass,
        RuleSpecificity specificity,
        String sourceId
) {
    public TerritoryRuleCandidate {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(safetyClass, "safetyClass");
        Objects.requireNonNull(specificity, "specificity");
        Objects.requireNonNull(sourceId, "sourceId");
        ruleId = ruleId.strip().toLowerCase(Locale.ROOT);
        sourceId = sourceId.strip().toLowerCase(Locale.ROOT);
        if (!ruleId.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный стабильный ID правила");
        }
        if (!sourceId.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный ID источника");
        }
        if (safetyClass == RuleSafetyClass.MANDATORY_RESTRICTION && effect != RuleEffect.DENY) {
            throw new IllegalArgumentException("Обязательное ограничение безопасности не может разрешать действие");
        }
    }
}

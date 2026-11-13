package ru.raskol.zones.territory.rules;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class TerritoryRuleRegistry {
    private final TerritoryRuleRepository repository;

    public TerritoryRuleRegistry(TerritoryRuleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public CompletionStage<TerritoryRuleDefinition> register(
            String ruleId,
            UUID territoryId,
            String actionKey,
            RuleEffect effect,
            RuleSafetyClass safetyClass,
            RuleSpecificity specificity,
            String sourceId
    ) {
        TerritoryRuleDefinition definition = new TerritoryRuleDefinition(
                ruleId, territoryId, actionKey, effect, safetyClass, specificity,
                sourceId, true, 0, Instant.now()
        );
        return repository.create(definition);
    }

    public CompletionStage<TerritoryRuleDefinition> register(
            String ruleId,
            UUID territoryId,
            String actionKey,
            RuleEffect effect,
            RuleSafetyClass safetyClass,
            RuleSpecificity specificity,
            String sourceId,
            java.util.List<RuleCondition> conditions
    ) {
        TerritoryRuleDefinition definition = new TerritoryRuleDefinition(
                ruleId, territoryId, actionKey, effect, safetyClass, specificity,
                sourceId, true, 0, Instant.now(), conditions
        );
        return repository.create(definition);
    }

    public CompletionStage<TerritoryRuleDefinition> update(
            TerritoryRuleDefinition current,
            RuleEffect effect,
            RuleSafetyClass safetyClass,
            RuleSpecificity specificity,
            boolean enabled,
            long expectedVersion
    ) {
        Objects.requireNonNull(current, "current");
        if (current.version() != expectedVersion) {
            throw new IllegalArgumentException("Передана устаревшая версия правила");
        }
        TerritoryRuleDefinition updated = new TerritoryRuleDefinition(
                current.ruleId(), current.territoryId(), current.actionKey(),
                effect, safetyClass, specificity, current.sourceId(), enabled,
                expectedVersion + 1, Instant.now(), current.conditions()
        );
        return repository.update(updated, expectedVersion);
    }
}

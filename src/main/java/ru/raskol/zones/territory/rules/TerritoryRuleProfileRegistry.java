package ru.raskol.zones.territory.rules;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import ru.raskol.zones.territory.type.TerritoryTypeRegistry;

public final class TerritoryRuleProfileRegistry {
    private final TerritoryRuleProfileRepository repository;
    private final TerritoryTypeRegistry territoryTypes;

    public TerritoryRuleProfileRegistry(
            TerritoryRuleProfileRepository repository,
            TerritoryTypeRegistry territoryTypes
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.territoryTypes = Objects.requireNonNull(territoryTypes, "territoryTypes");
    }

    public CompletionStage<TerritoryRuleProfileDefinition> register(
            String profileId,
            String territoryTypeId,
            String actionKey,
            RuleEffect effect,
            RuleSafetyClass safetyClass,
            RuleSpecificity specificity,
            String sourceId
    ) {
        territoryTypes.require(territoryTypeId);
        TerritoryRuleProfileDefinition profile = new TerritoryRuleProfileDefinition(
                profileId, territoryTypeId, actionKey, effect, safetyClass,
                specificity, sourceId, true, 0, Instant.now()
        );
        return repository.create(profile);
    }

    public CompletionStage<TerritoryRuleProfileDefinition> register(
            String profileId,
            String territoryTypeId,
            String actionKey,
            RuleEffect effect,
            RuleSafetyClass safetyClass,
            RuleSpecificity specificity,
            String sourceId,
            java.util.List<RuleCondition> conditions
    ) {
        territoryTypes.require(territoryTypeId);
        TerritoryRuleProfileDefinition profile = new TerritoryRuleProfileDefinition(
                profileId, territoryTypeId, actionKey, effect, safetyClass,
                specificity, sourceId, true, 0, Instant.now(), conditions
        );
        return repository.create(profile);
    }

    public CompletionStage<TerritoryRuleProfileDefinition> update(
            TerritoryRuleProfileDefinition current,
            RuleEffect effect,
            RuleSafetyClass safetyClass,
            RuleSpecificity specificity,
            boolean enabled,
            long expectedVersion
    ) {
        Objects.requireNonNull(current, "current");
        if (current.version() != expectedVersion) {
            throw new IllegalArgumentException("Передана устаревшая версия профиля");
        }
        TerritoryRuleProfileDefinition updated = new TerritoryRuleProfileDefinition(
                current.profileId(), current.territoryTypeId(), current.actionKey(),
                effect, safetyClass, specificity, current.sourceId(), enabled,
                expectedVersion + 1, Instant.now(), current.conditions()
        );
        return repository.update(updated, expectedVersion);
    }
}

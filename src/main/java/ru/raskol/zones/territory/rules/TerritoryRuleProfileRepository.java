package ru.raskol.zones.territory.rules;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface TerritoryRuleProfileRepository {
    CompletionStage<TerritoryRuleProfileDefinition> create(TerritoryRuleProfileDefinition profile);

    CompletionStage<List<TerritoryRuleProfileDefinition>> findApplicable(
            Collection<String> territoryTypeIds,
            String actionKey
    );

    CompletionStage<TerritoryRuleProfileDefinition> update(
            TerritoryRuleProfileDefinition profile,
            long expectedVersion
    );
}

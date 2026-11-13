package ru.raskol.zones.territory.rules;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface TerritoryRuleRepository {
    CompletionStage<TerritoryRuleDefinition> create(TerritoryRuleDefinition rule);

    CompletionStage<List<TerritoryRuleDefinition>> findApplicable(
            Collection<String> territoryIds,
            String actionKey
    );

    CompletionStage<TerritoryRuleDefinition> update(
            TerritoryRuleDefinition rule,
            long expectedVersion
    );
}

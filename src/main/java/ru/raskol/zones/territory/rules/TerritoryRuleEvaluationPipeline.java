package ru.raskol.zones.territory.rules;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import ru.raskol.zones.territory.spatial.SpatialQueryResult;
import ru.raskol.zones.territory.spatial.SpatialQueryService;

public final class TerritoryRuleEvaluationPipeline {
    private final SpatialQueryService spatialQueries;
    private final TerritoryRuleRepository rules;
    private final TerritoryRuleResolutionEngine resolver;

    public TerritoryRuleEvaluationPipeline(
            SpatialQueryService spatialQueries,
            TerritoryRuleRepository rules,
            TerritoryRuleResolutionEngine resolver
    ) {
        this.spatialQueries = Objects.requireNonNull(spatialQueries, "spatialQueries");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public CompletionStage<RuleEvaluationResult> evaluate(
            UUID worldId,
            double x,
            int y,
            double z,
            String actionKey
    ) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(actionKey, "actionKey");
        String normalizedAction = actionKey.strip().toLowerCase(java.util.Locale.ROOT);
        if (!normalizedAction.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Некорректный ключ действия");
        }

        SpatialQueryResult spatial = spatialQueries.query(worldId, x, y, z);
        if (!spatial.isReady()) {
            return CompletableFuture.completedFuture(new RuleEvaluationResult(
                    worldId, normalizedAction, spatial.snapshotVersion(),
                    RuleEvaluationStatus.INDETERMINATE, java.util.Optional.empty()
            ));
        }

        List<String> territoryIds = spatial.territories().stream()
                .map(territory -> territory.id().toString())
                .toList();
        return rules.findApplicable(territoryIds, normalizedAction).thenApply(definitions -> {
            List<TerritoryRuleCandidate> candidates = definitions.stream()
                    .map(rule -> spatial.territories().stream()
                            .filter(territory -> territory.id().equals(rule.territoryId()))
                            .findFirst()
                            .map(territory -> new TerritoryRuleCandidate(
                                    rule.ruleId(), territory, rule.effect(), rule.safetyClass(),
                                    rule.specificity(), rule.sourceId()
                            ))
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .toList();

            RuleResolutionResult result = resolver.resolve(worldId, candidates, spatial.territories());
            RuleEvaluationStatus status = switch (result.status()) {
                case RESOLVED -> RuleEvaluationStatus.RESOLVED;
                case NO_CANDIDATES -> RuleEvaluationStatus.NO_APPLICABLE_RULE;
                case INDETERMINATE -> RuleEvaluationStatus.INDETERMINATE;
            };
            return new RuleEvaluationResult(
                    worldId, normalizedAction, spatial.snapshotVersion(), status,
                    java.util.Optional.of(result)
            );
        });
    }
}

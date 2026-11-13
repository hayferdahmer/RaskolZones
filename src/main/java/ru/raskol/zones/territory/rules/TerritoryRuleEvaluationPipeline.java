package ru.raskol.zones.territory.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import ru.raskol.zones.territory.Territory;
import ru.raskol.zones.territory.TerritoryRepository;
import ru.raskol.zones.territory.TerritoryStatus;
import ru.raskol.zones.territory.spatial.SpatialQueryResult;
import ru.raskol.zones.territory.spatial.SpatialQueryService;

public final class TerritoryRuleEvaluationPipeline {
    private final SpatialQueryService spatialQueries;
    private final TerritoryRuleRepository rules;
    private final TerritoryRuleProfileRepository profiles;
    private final TerritoryRuleResolutionEngine resolver;
    private final TerritoryRepository territories;

    public TerritoryRuleEvaluationPipeline(
            SpatialQueryService spatialQueries,
            TerritoryRuleRepository rules,
            TerritoryRuleProfileRepository profiles,
            TerritoryRuleResolutionEngine resolver,
            TerritoryRepository territories
    ) {
        this.spatialQueries = Objects.requireNonNull(spatialQueries, "spatialQueries");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.territories = Objects.requireNonNull(territories, "territories");
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

        return territories.listByWorld(worldId).thenCompose(snapshot -> {
            Map<UUID, Territory> byId = new LinkedHashMap<>();
            snapshot.forEach(territory -> byId.put(territory.id(), territory));
            Map<UUID, Territory> scope;
            try {
                scope = resolvePolicyScope(worldId, spatial.territories(), byId);
            } catch (IllegalStateException invalidHierarchy) {
                return CompletableFuture.completedFuture(new RuleEvaluationResult(
                        worldId, normalizedAction, spatial.snapshotVersion(),
                        RuleEvaluationStatus.INDETERMINATE, java.util.Optional.empty()
                ));
            }
            List<String> territoryIds = scope.values().stream()
                    .map(territory -> territory.id().toString())
                    .sorted()
                    .toList();
            List<String> typeIds = scope.values().stream()
                    .map(Territory::typeId)
                    .distinct()
                    .sorted()
                    .toList();

            CompletionStage<List<TerritoryRuleDefinition>> directRules =
                    rules.findApplicable(territoryIds, normalizedAction);
            CompletionStage<List<TerritoryRuleProfileDefinition>> typeProfiles =
                    profiles.findApplicable(typeIds, normalizedAction);

            return directRules.thenCombine(typeProfiles, (definitions, profileDefinitions) -> {
                Set<UUID> directOverrides = new LinkedHashSet<>();
                List<TerritoryRuleCandidate> candidates = new ArrayList<>();

                for (TerritoryRuleDefinition rule : definitions) {
                    Territory territory = scope.get(rule.territoryId());
                    if (territory == null) {
                        continue;
                    }
                    directOverrides.add(territory.id());
                    candidates.add(new TerritoryRuleCandidate(
                            rule.ruleId(), territory, rule.effect(), rule.safetyClass(),
                            rule.specificity(), rule.sourceId()
                    ));
                }

                Map<String, TerritoryRuleProfileDefinition> profilesByType = new LinkedHashMap<>();
                for (TerritoryRuleProfileDefinition profile : profileDefinitions) {
                    profilesByType.put(profile.territoryTypeId(), profile);
                }
                for (Territory territory : scope.values()) {
                    if (directOverrides.contains(territory.id())) {
                        continue;
                    }
                    TerritoryRuleProfileDefinition profile = profilesByType.get(territory.typeId());
                    if (profile == null) {
                        continue;
                    }
                    candidates.add(new TerritoryRuleCandidate(
                            profile.profileId(), territory, profile.effect(), profile.safetyClass(),
                            profile.specificity(), profile.sourceId()
                    ));
                }

                RuleResolutionResult result = resolver.resolve(worldId, candidates, snapshot);
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
        });
    }

    private static Map<UUID, Territory> resolvePolicyScope(
            UUID worldId,
            List<Territory> spatialTerritories,
            Map<UUID, Territory> snapshot
    ) {
        Map<UUID, Territory> scope = new LinkedHashMap<>();
        for (Territory spatialTerritory : spatialTerritories) {
            Territory current = snapshot.get(spatialTerritory.id());
            if (current == null) {
                throw new IllegalStateException("Территория отсутствует в снимке мира: " + spatialTerritory.id());
            }
            Set<UUID> visited = new LinkedHashSet<>();
            while (current != null) {
                if (!visited.add(current.id())) {
                    throw new IllegalStateException("Цикл в иерархии территорий: " + current.id());
                }
                if (!worldId.equals(current.worldId()) || current.status() != TerritoryStatus.ACTIVE) {
                    throw new IllegalStateException("Некорректный родительский контекст территории: "
                            + current.id());
                }
                scope.putIfAbsent(current.id(), current);
                if (current.parentId() == null) {
                    break;
                }
                current = snapshot.get(current.parentId());
                if (current == null) {
                    throw new IllegalStateException("Родительская территория отсутствует в снимке");
                }
            }
        }
        return Map.copyOf(scope);
    }
}

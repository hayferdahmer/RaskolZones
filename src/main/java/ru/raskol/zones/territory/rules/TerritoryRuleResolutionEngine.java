package ru.raskol.zones.territory.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ru.raskol.zones.territory.Territory;
import ru.raskol.zones.territory.TerritoryStatus;

public final class TerritoryRuleResolutionEngine {
    private static final Comparator<TerritoryRuleCandidate> STABLE_ID_ORDER =
            Comparator.comparing(TerritoryRuleCandidate::ruleId)
                    .thenComparing(candidate -> candidate.territory().id().toString());

    private final SourceAuthorityRegistry authorities;

    public TerritoryRuleResolutionEngine(SourceAuthorityRegistry authorities) {
        this.authorities = Objects.requireNonNull(authorities, "authorities");
    }

    public RuleResolutionResult resolve(
            UUID worldId,
            Collection<TerritoryRuleCandidate> candidates,
            Collection<Territory> territorySnapshot
    ) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(territorySnapshot, "territorySnapshot");

        if (candidates.isEmpty()) {
            return unresolved(RuleResolutionStatus.NO_CANDIDATES,
                    RuleDecisionReason.NO_APPLICABLE_RULE, List.of());
        }

        Map<UUID, Territory> territories;
        try {
            territories = territoryMap(territorySnapshot);
            validateCandidates(worldId, candidates, territories);
            validateCandidateHierarchies(candidates, territories);
        } catch (ResolutionValidationException failure) {
            return unresolved(RuleResolutionStatus.INDETERMINATE, failure.reason(), traceFailure(candidates, failure.reason()));
        }

        Map<TerritoryRuleCandidate, Integer> sourceLevels = new HashMap<>();
        for (TerritoryRuleCandidate candidate : candidates) {
            Optional<Integer> authority = authorities.authorityOf(candidate.sourceId());
            if (authority.isEmpty()) {
                return unresolved(
                        RuleResolutionStatus.INDETERMINATE,
                        RuleDecisionReason.UNKNOWN_SOURCE,
                        traceFailure(candidates, RuleDecisionReason.UNKNOWN_SOURCE)
                );
            }
            sourceLevels.put(candidate, authority.get());
        }

        List<TerritoryRuleCandidate> safetyRestrictions = candidates.stream()
                .filter(candidate -> candidate.safetyClass() == RuleSafetyClass.MANDATORY_RESTRICTION)
                .sorted(STABLE_ID_ORDER)
                .toList();
        if (!safetyRestrictions.isEmpty()) {
            return resolved(safetyRestrictions.getFirst(), candidates,
                    RuleDecisionReason.MANDATORY_SAFETY_RESTRICTION, sourceLevels, territories);
        }

        List<TerritoryRuleCandidate> hierarchyWinners = candidates.stream()
                .filter(candidate -> candidates.stream().noneMatch(other ->
                        !candidate.territory().id().equals(other.territory().id())
                                && isAncestor(candidate.territory().id(), other.territory().id(), territories)))
                .toList();

        Comparator<TerritoryRuleCandidate> comparator = Comparator
                .comparingInt((TerritoryRuleCandidate candidate) -> candidate.territory().weight()).reversed()
                .thenComparing(TerritoryRuleCandidate::specificity, Comparator.reverseOrder())
                .thenComparing((left, right) -> Integer.compare(
                        sourceLevels.get(right), sourceLevels.get(left)))
                .thenComparing((left, right) -> {
                    if (left.effect() == right.effect()) {
                        return 0;
                    }
                    return left.effect() == RuleEffect.DENY ? -1 : 1;
                })
                .thenComparing(STABLE_ID_ORDER);

        TerritoryRuleCandidate winner = hierarchyWinners.stream().min(comparator).orElseThrow();
        RuleDecisionReason reason = winnerReason(winner, candidates, territories, sourceLevels);
        return resolved(winner, candidates, reason, sourceLevels, territories);
    }

    private static Map<UUID, Territory> territoryMap(Collection<Territory> snapshot) {
        Map<UUID, Territory> result = new LinkedHashMap<>();
        for (Territory territory : snapshot) {
            if (territory == null) {
                throw new ResolutionValidationException(RuleDecisionReason.INVALID_HIERARCHY);
            }
            if (result.putIfAbsent(territory.id(), territory) != null) {
                throw new ResolutionValidationException(RuleDecisionReason.INVALID_HIERARCHY);
            }
        }
        return Map.copyOf(result);
    }

    private static void validateCandidates(
            UUID worldId,
            Collection<TerritoryRuleCandidate> candidates,
            Map<UUID, Territory> territories
    ) {
        for (TerritoryRuleCandidate candidate : candidates) {
            if (candidate == null) {
                throw new ResolutionValidationException(RuleDecisionReason.INVALID_HIERARCHY);
            }
            if (!candidate.territory().worldId().equals(worldId)) {
                throw new ResolutionValidationException(RuleDecisionReason.WORLD_MISMATCH);
            }
            Territory stored = territories.get(candidate.territory().id());
            if (stored == null || !stored.equals(candidate.territory())
                    || stored.status() != TerritoryStatus.ACTIVE) {
                throw new ResolutionValidationException(RuleDecisionReason.INVALID_HIERARCHY);
            }
        }
    }

    private static void validateCandidateHierarchies(
            Collection<TerritoryRuleCandidate> candidates,
            Map<UUID, Territory> territories
    ) {
        for (TerritoryRuleCandidate candidate : candidates) {
            Set<UUID> visited = new HashSet<>();
            Territory current = candidate.territory();
            while (current.parentId() != null) {
                if (!visited.add(current.id())) {
                    throw new ResolutionValidationException(RuleDecisionReason.INVALID_HIERARCHY);
                }
                Territory parent = territories.get(current.parentId());
                if (parent == null || !parent.worldId().equals(current.worldId())) {
                    throw new ResolutionValidationException(RuleDecisionReason.INVALID_HIERARCHY);
                }
                current = parent;
            }
        }
    }

    private static boolean isAncestor(UUID ancestorId, UUID descendantId, Map<UUID, Territory> territories) {
        Territory current = territories.get(descendantId);
        while (current != null && current.parentId() != null) {
            if (current.parentId().equals(ancestorId)) {
                return true;
            }
            current = territories.get(current.parentId());
        }
        return false;
    }

    private static RuleDecisionReason winnerReason(
            TerritoryRuleCandidate winner,
            Collection<TerritoryRuleCandidate> candidates,
            Map<UUID, Territory> territories,
            Map<TerritoryRuleCandidate, Integer> sourceLevels
    ) {
        if (candidates.stream().anyMatch(candidate ->
                !candidate.territory().id().equals(winner.territory().id())
                        && isAncestor(candidate.territory().id(), winner.territory().id(), territories))) {
            return RuleDecisionReason.DESCENDANT_TERRITORY;
        }
        for (TerritoryRuleCandidate candidate : candidates) {
            if (candidate == winner) {
                continue;
            }
            if (winner.territory().weight() != candidate.territory().weight()) {
                return RuleDecisionReason.HIGHER_WEIGHT;
            }
            if (winner.specificity().compareTo(candidate.specificity()) != 0) {
                return RuleDecisionReason.HIGHER_SPECIFICITY;
            }
            if (!sourceLevels.get(winner).equals(sourceLevels.get(candidate))) {
                return RuleDecisionReason.HIGHER_SOURCE_AUTHORITY;
            }
            if (winner.effect() != candidate.effect()) {
                return RuleDecisionReason.DENY_ON_TIE;
            }
        }
        return RuleDecisionReason.STABLE_ID_TIE_BREAKER;
    }

    private static RuleResolutionResult resolved(
            TerritoryRuleCandidate winner,
            Collection<TerritoryRuleCandidate> candidates,
            RuleDecisionReason reason,
            Map<TerritoryRuleCandidate, Integer> sourceLevels,
            Map<UUID, Territory> territories
    ) {
        List<RuleDecisionTraceEntry> trace = new ArrayList<>();
        candidates.stream().sorted(STABLE_ID_ORDER).forEach(candidate -> trace.add(
                new RuleDecisionTraceEntry(
                        candidate.ruleId(),
                        candidate.territory().id().toString(),
                        candidate.sourceId(),
                        candidate.effect(),
                        candidate.equals(winner),
                        candidate.equals(winner) ? reason : loserReason(winner, candidate, sourceLevels, territories)
                )
        ));
        return new RuleResolutionResult(
                RuleResolutionStatus.RESOLVED,
                Optional.of(winner),
                reason,
                trace
        );
    }

    private static RuleDecisionReason loserReason(
            TerritoryRuleCandidate winner,
            TerritoryRuleCandidate loser,
            Map<TerritoryRuleCandidate, Integer> sourceLevels,
            Map<UUID, Territory> territories
    ) {
        if (winner.safetyClass() == RuleSafetyClass.MANDATORY_RESTRICTION) {
            return RuleDecisionReason.MANDATORY_SAFETY_RESTRICTION;
        }
        if (!winner.territory().id().equals(loser.territory().id())
                && isAncestor(loser.territory().id(), winner.territory().id(), territories)) {
            return RuleDecisionReason.DESCENDANT_TERRITORY;
        }
        if (winner.territory().weight() != loser.territory().weight()) {
            return RuleDecisionReason.HIGHER_WEIGHT;
        }
        if (winner.specificity().compareTo(loser.specificity()) != 0) {
            return RuleDecisionReason.HIGHER_SPECIFICITY;
        }
        if (!sourceLevels.get(winner).equals(sourceLevels.get(loser))) {
            return RuleDecisionReason.HIGHER_SOURCE_AUTHORITY;
        }
        if (winner.effect() != loser.effect()) {
            return RuleDecisionReason.DENY_ON_TIE;
        }
        return RuleDecisionReason.STABLE_ID_TIE_BREAKER;
    }

    private static List<RuleDecisionTraceEntry> traceFailure(
            Collection<TerritoryRuleCandidate> candidates,
            RuleDecisionReason reason
    ) {
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(STABLE_ID_ORDER)
                .map(candidate -> new RuleDecisionTraceEntry(
                        candidate.ruleId(),
                        candidate.territory().id().toString(),
                        candidate.sourceId(),
                        candidate.effect(),
                        false,
                        reason
                ))
                .toList();
    }

    private static RuleResolutionResult unresolved(
            RuleResolutionStatus status,
            RuleDecisionReason reason,
            List<RuleDecisionTraceEntry> trace
    ) {
        return new RuleResolutionResult(status, Optional.empty(), reason, trace);
    }

    private static final class ResolutionValidationException extends RuntimeException {
        private final RuleDecisionReason reason;

        private ResolutionValidationException(RuleDecisionReason reason) {
            this.reason = reason;
        }

        private RuleDecisionReason reason() {
            return reason;
        }
    }
}

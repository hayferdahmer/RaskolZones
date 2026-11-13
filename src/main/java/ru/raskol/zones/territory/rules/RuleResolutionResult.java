package ru.raskol.zones.territory.rules;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuleResolutionResult(
        RuleResolutionStatus status,
        Optional<TerritoryRuleCandidate> winner,
        RuleDecisionReason reason,
        List<RuleDecisionTraceEntry> trace
) {
    public RuleResolutionResult {
        Objects.requireNonNull(status, "status");
        winner = Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(reason, "reason");
        trace = List.copyOf(trace);
        if (status == RuleResolutionStatus.RESOLVED && winner.isEmpty()) {
            throw new IllegalArgumentException("Resolved result must have a winner");
        }
        if (status != RuleResolutionStatus.RESOLVED && winner.isPresent()) {
            throw new IllegalArgumentException("Unresolved result cannot have a winner");
        }
    }
}

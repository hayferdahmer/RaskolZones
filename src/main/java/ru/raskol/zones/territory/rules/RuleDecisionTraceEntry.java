package ru.raskol.zones.territory.rules;

import java.util.Objects;

public record RuleDecisionTraceEntry(
        String ruleId,
        String territoryId,
        String sourceId,
        RuleEffect effect,
        boolean selected,
        RuleDecisionReason reason
) {
    public RuleDecisionTraceEntry {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(territoryId, "territoryId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(reason, "reason");
    }
}

package ru.raskol.zones.territory.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.raskol.zones.territory.Territory;
import ru.raskol.zones.territory.TerritoryStatus;
import ru.raskol.zones.territory.geometry.CuboidGeometry;

class TerritoryRuleResolutionEngineTest {
    private final TerritoryRuleResolutionEngine engine =
            new TerritoryRuleResolutionEngine(SourceAuthorityRegistry.coreOnly());

    @Test
    void mandatorySafetyDenyOverridesAllOrdinaryCriteria() {
        UUID world = UUID.randomUUID();
        Territory parent = territory(world, null, 10);
        Territory child = territory(world, parent.id(), 1);
        TerritoryRuleCandidate ordinary = candidate("ordinary", child, RuleEffect.ALLOW, 1_000);
        TerritoryRuleCandidate safety = new TerritoryRuleCandidate(
                "safety.lockdown", parent, RuleEffect.DENY,
                RuleSafetyClass.MANDATORY_RESTRICTION,
                new RuleSpecificity(0), "raskol-core"
        );

        var result = engine.resolve(world, List.of(ordinary, safety), List.of(parent, child));

        assertEquals(RuleResolutionStatus.RESOLVED, result.status());
        assertEquals("safety.lockdown", result.winner().orElseThrow().ruleId());
        assertEquals(RuleDecisionReason.MANDATORY_SAFETY_RESTRICTION, result.reason());
    }

    @Test
    void confirmedDescendantWinsOverAncestorEvenWithLowerWeight() {
        UUID world = UUID.randomUUID();
        Territory parent = territory(world, null, 900);
        Territory child = territory(world, parent.id(), 1);

        var result = engine.resolve(world, List.of(
                candidate("parent.allow", parent, RuleEffect.ALLOW, 1_000),
                candidate("child.deny", child, RuleEffect.DENY, 0)
        ), List.of(parent, child));

        assertEquals("child.deny", result.winner().orElseThrow().ruleId());
        assertEquals(RuleDecisionReason.DESCENDANT_TERRITORY, result.reason());
    }

    @Test
    void higherWeightWinsForUnrelatedTerritories() {
        UUID world = UUID.randomUUID();
        Territory low = territory(world, null, 5);
        Territory high = territory(world, null, 6);

        var result = engine.resolve(world, List.of(
                candidate("low.allow", low, RuleEffect.ALLOW, 500),
                candidate("high.deny", high, RuleEffect.DENY, 0)
        ), List.of(low, high));

        assertEquals("high.deny", result.winner().orElseThrow().ruleId());
        assertEquals(RuleDecisionReason.HIGHER_WEIGHT, result.reason());
    }

    @Test
    void specificityThenSourceAuthorityAreComparedInOrder() {
        UUID world = UUID.randomUUID();
        Territory territory = territory(world, null, 10);
        var lowAuthority = new TerritoryRuleCandidate(
                "rule.a", territory, RuleEffect.ALLOW, RuleSafetyClass.ORDINARY,
                new RuleSpecificity(50), "raskol-core"
        );
        var highSpecificity = new TerritoryRuleCandidate(
                "rule.b", territory, RuleEffect.DENY, RuleSafetyClass.ORDINARY,
                new RuleSpecificity(51), "raskol-core"
        );

        var specificityResult = engine.resolve(world, List.of(lowAuthority, highSpecificity), List.of(territory));
        assertEquals("rule.b", specificityResult.winner().orElseThrow().ruleId());
        assertEquals(RuleDecisionReason.HIGHER_SPECIFICITY, specificityResult.reason());

        var registry = new SourceAuthorityRegistry(Map.of("raskol-core", 1_000, "trusted.adapter", 500));
        var authorityEngine = new TerritoryRuleResolutionEngine(registry);
        var sourceResult = authorityEngine.resolve(world, List.of(
                new TerritoryRuleCandidate("rule.core", territory, RuleEffect.ALLOW,
                        RuleSafetyClass.ORDINARY, new RuleSpecificity(50), "raskol-core"),
                new TerritoryRuleCandidate("rule.adapter", territory, RuleEffect.DENY,
                        RuleSafetyClass.ORDINARY, new RuleSpecificity(50), "trusted.adapter")
        ), List.of(territory));

        assertEquals("rule.core", sourceResult.winner().orElseThrow().ruleId());
        assertEquals(RuleDecisionReason.HIGHER_SOURCE_AUTHORITY, sourceResult.reason());
    }

    @Test
    void denyWinsWhenAllPriorCriteriaAreEqual() {
        UUID world = UUID.randomUUID();
        Territory territory = territory(world, null, 10);

        var result = engine.resolve(world, List.of(
                candidate("rule.allow", territory, RuleEffect.ALLOW, 100),
                candidate("rule.deny", territory, RuleEffect.DENY, 100)
        ), List.of(territory));

        assertEquals("rule.deny", result.winner().orElseThrow().ruleId());
        assertEquals(RuleDecisionReason.DENY_ON_TIE, result.reason());
    }

    @Test
    void stableIdsResolveRemainingEqualCandidatesDeterministically() {
        UUID world = UUID.randomUUID();
        Territory territory = territory(world, null, 10);

        var result = engine.resolve(world, List.of(
                candidate("rule.z", territory, RuleEffect.ALLOW, 100),
                candidate("rule.a", territory, RuleEffect.ALLOW, 100)
        ), List.of(territory));

        assertEquals("rule.a", result.winner().orElseThrow().ruleId());
        assertEquals(RuleDecisionReason.STABLE_ID_TIE_BREAKER, result.reason());
        assertEquals(2, result.trace().size());
        assertTrue(result.trace().stream().anyMatch(entry -> entry.selected()));
    }

    @Test
    void unknownSourceFailsClosed() {
        UUID world = UUID.randomUUID();
        Territory territory = territory(world, null, 10);
        TerritoryRuleCandidate unknown = new TerritoryRuleCandidate(
                "external.rule", territory, RuleEffect.ALLOW, RuleSafetyClass.ORDINARY,
                new RuleSpecificity(100), "unregistered.plugin"
        );

        var result = engine.resolve(world, List.of(unknown), List.of(territory));

        assertEquals(RuleResolutionStatus.INDETERMINATE, result.status());
        assertTrue(result.winner().isEmpty());
        assertEquals(RuleDecisionReason.UNKNOWN_SOURCE, result.reason());
    }

    @Test
    void missingParentAndWorldMismatchFailClosed() {
        UUID world = UUID.randomUUID();
        Territory missingParent = territory(world, UUID.randomUUID(), 10);
        var brokenHierarchy = engine.resolve(world,
                List.of(candidate("broken", missingParent, RuleEffect.ALLOW, 1)),
                List.of(missingParent));
        assertEquals(RuleResolutionStatus.INDETERMINATE, brokenHierarchy.status());
        assertEquals(RuleDecisionReason.INVALID_HIERARCHY, brokenHierarchy.reason());

        Territory otherWorld = territory(UUID.randomUUID(), null, 10);
        var wrongWorld = engine.resolve(world,
                List.of(candidate("wrong.world", otherWorld, RuleEffect.ALLOW, 1)),
                List.of(otherWorld));
        assertEquals(RuleResolutionStatus.INDETERMINATE, wrongWorld.status());
        assertEquals(RuleDecisionReason.WORLD_MISMATCH, wrongWorld.reason());
    }

    @Test
    void emptyCandidateSetHasExplicitNoCandidatesResult() {
        var result = engine.resolve(UUID.randomUUID(), List.of(), List.of());
        assertEquals(RuleResolutionStatus.NO_CANDIDATES, result.status());
        assertTrue(result.winner().isEmpty());
    }

    private static TerritoryRuleCandidate candidate(
            String id, Territory territory, RuleEffect effect, int specificity
    ) {
        return new TerritoryRuleCandidate(
                id, territory, effect, RuleSafetyClass.ORDINARY,
                new RuleSpecificity(specificity), "raskol-core"
        );
    }

    private static Territory territory(UUID worldId, UUID parentId, int weight) {
        Instant now = Instant.now();
        return new Territory(
                UUID.randomUUID(), worldId, parentId, "region", "Тестовая территория", weight,
                new CuboidGeometry(0, 10, 0, 100, 0, 10),
                TerritoryStatus.ACTIVE, 0, now, now
        );
    }
}

package ru.raskol.zones.territory.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TerritoryRuleDefinitionTest {
    @Test
    void normalizesStableIdentifiersAndActionKeys() {
        var rule = new TerritoryRuleDefinition(
                " BUILD.PLACE ", UUID.randomUUID(), " BLOCK.PLACE ",
                RuleEffect.DENY, RuleSafetyClass.ORDINARY,
                new RuleSpecificity(500), " RASKOL-CORE ",
                true, 0, Instant.parse("2026-01-01T00:00:00Z")
        );

        assertEquals("build.place", rule.ruleId());
        assertEquals("block.place", rule.actionKey());
        assertEquals("raskol-core", rule.sourceId());
    }

    @Test
    void mandatorySafetyRuleCannotAllow() {
        assertThrows(IllegalArgumentException.class, () -> new TerritoryRuleDefinition(
                "safety.allow", UUID.randomUUID(), "pvp.attack",
                RuleEffect.ALLOW, RuleSafetyClass.MANDATORY_RESTRICTION,
                new RuleSpecificity(1), "raskol-core",
                true, 0, Instant.now()
        ));
    }

    @Test
    void rejectsInvalidActionKeysAndVersions() {
        assertThrows(IllegalArgumentException.class, () -> new TerritoryRuleDefinition(
                "rule.valid", UUID.randomUUID(), "not valid",
                RuleEffect.DENY, RuleSafetyClass.ORDINARY,
                new RuleSpecificity(1), "raskol-core",
                true, 0, Instant.now()
        ));
        assertThrows(IllegalArgumentException.class, () -> new TerritoryRuleDefinition(
                "rule.valid", UUID.randomUUID(), "pvp.attack",
                RuleEffect.DENY, RuleSafetyClass.ORDINARY,
                new RuleSpecificity(1), "raskol-core",
                true, -1, Instant.now()
        ));
    }
}

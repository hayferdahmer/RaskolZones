package ru.raskol.zones.territory.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleConditionEvaluatorTest {
    private final RuleConditionEvaluator evaluator = new RuleConditionEvaluator();
    private final UUID worldId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    void matchesActorEventAndHeightTogether() {
        var context = new RuleEvaluationContext(
                worldId, "block.break", Optional.of(actorId), Optional.empty(),
                Optional.empty(), Optional.of("block-break"), 10.5, 64, -2.5
        );

        assertEquals(ConditionEvaluationStatus.MATCH, evaluator.evaluate(List.of(
                new RuleCondition.ActorIs(actorId),
                new RuleCondition.EventIs("BLOCK-BREAK"),
                new RuleCondition.YRange(0, 100),
                new RuleCondition.WorldIs(worldId)
        ), context));
    }

    @Test
    void missingRequiredContextIsIndeterminate() {
        var context = new RuleEvaluationContext(
                worldId, "entity.interact", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, 64, 0
        );

        assertEquals(ConditionEvaluationStatus.INDETERMINATE,
                evaluator.evaluate(List.of(new RuleCondition.ActorIs(actorId)), context));
    }

    @Test
    void definitiveMismatchTakesPrecedenceOverMissingContext() {
        var context = new RuleEvaluationContext(
                worldId, "entity.interact", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, 64, 0
        );

        assertEquals(ConditionEvaluationStatus.NO_MATCH, evaluator.evaluate(List.of(
                new RuleCondition.ActorIs(actorId),
                new RuleCondition.YRange(100, 120)
        ), context));
    }

    @Test
    void emptyConditionSetMatches() {
        var context = new RuleEvaluationContext(
                worldId, "block.break", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, 64, 0
        );

        assertEquals(ConditionEvaluationStatus.MATCH, evaluator.evaluate(List.of(), context));
    }

    @Test
    void oversizedConditionSetFailsClosed() {
        var context = new RuleEvaluationContext(
                worldId, "block.break", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, 64, 0
        );

        List<RuleCondition> conditions = java.util.Collections.nCopies(
                RuleConditionEvaluator.MAX_CONDITIONS + 1,
                new RuleCondition.YRange(0, 256)
        );
        assertEquals(ConditionEvaluationStatus.INDETERMINATE, evaluator.evaluate(conditions, context));
    }

    @Test
    void rejectsInvalidCoordinatesAndRanges() {
        assertThrows(IllegalArgumentException.class, () -> new RuleEvaluationContext(
                worldId, "block.break", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Double.NaN, 64, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new RuleCondition.YRange(20, 10));
    }
}

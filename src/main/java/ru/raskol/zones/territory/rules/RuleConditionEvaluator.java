package ru.raskol.zones.territory.rules;

import java.util.List;
import java.util.Objects;

/** Детерминированная проверка ограниченного набора условий без выполнения пользовательского кода. */
public final class RuleConditionEvaluator {
    public static final int MAX_CONDITIONS = 32;

    public ConditionEvaluationStatus evaluate(
            List<RuleCondition> conditions,
            RuleEvaluationContext context
    ) {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(context, "context");
        if (conditions.size() > MAX_CONDITIONS || conditions.stream().anyMatch(Objects::isNull)) {
            return ConditionEvaluationStatus.INDETERMINATE;
        }

        boolean indeterminate = false;
        for (RuleCondition condition : conditions) {
            ConditionEvaluationStatus result = evaluateOne(condition, context);
            if (result == ConditionEvaluationStatus.NO_MATCH) {
                return ConditionEvaluationStatus.NO_MATCH;
            }
            if (result == ConditionEvaluationStatus.INDETERMINATE) {
                indeterminate = true;
            }
        }
        return indeterminate ? ConditionEvaluationStatus.INDETERMINATE : ConditionEvaluationStatus.MATCH;
    }

    private static ConditionEvaluationStatus evaluateOne(
            RuleCondition condition,
            RuleEvaluationContext context
    ) {
        if (condition instanceof RuleCondition.ActorIs expected) {
            return context.actorId().map(expected.actorId()::equals)
                    .map(RuleConditionEvaluator::fromBoolean)
                    .orElse(ConditionEvaluationStatus.INDETERMINATE);
        }
        if (condition instanceof RuleCondition.TargetIs expected) {
            return context.targetId().map(expected.targetId()::equals)
                    .map(RuleConditionEvaluator::fromBoolean)
                    .orElse(ConditionEvaluationStatus.INDETERMINATE);
        }
        if (condition instanceof RuleCondition.TargetTypeIs expected) {
            return context.targetType().map(expected.targetType()::equals)
                    .map(RuleConditionEvaluator::fromBoolean)
                    .orElse(ConditionEvaluationStatus.INDETERMINATE);
        }
        if (condition instanceof RuleCondition.EventIs expected) {
            return context.eventKey().map(expected.eventKey()::equals)
                    .map(RuleConditionEvaluator::fromBoolean)
                    .orElse(ConditionEvaluationStatus.INDETERMINATE);
        }
        if (condition instanceof RuleCondition.WorldIs expected) {
            return fromBoolean(expected.worldId().equals(context.worldId()));
        }
        if (condition instanceof RuleCondition.YRange expected) {
            return fromBoolean(context.y() >= expected.minimum() && context.y() <= expected.maximum());
        }
        return ConditionEvaluationStatus.INDETERMINATE;
    }

    private static ConditionEvaluationStatus fromBoolean(boolean matches) {
        return matches ? ConditionEvaluationStatus.MATCH : ConditionEvaluationStatus.NO_MATCH;
    }
}

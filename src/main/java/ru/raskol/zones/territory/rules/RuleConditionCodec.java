package ru.raskol.zones.territory.rules;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Компактное версионированное представление только известных типов условий. */
final class RuleConditionCodec {
    private static final String VERSION = "v1";

    private RuleConditionCodec() { }

    static String encode(List<RuleCondition> conditions) {
        if (conditions.size() > RuleConditionEvaluator.MAX_CONDITIONS) {
            throw new IllegalArgumentException("Превышен лимит условий правила");
        }
        StringBuilder value = new StringBuilder(VERSION);
        for (RuleCondition condition : conditions) {
            String type;
            String payload;
            if (condition instanceof RuleCondition.ActorIs item) {
                type = "actor"; payload = item.actorId().toString();
            } else if (condition instanceof RuleCondition.TargetIs item) {
                type = "target"; payload = item.targetId().toString();
            } else if (condition instanceof RuleCondition.TargetTypeIs item) {
                type = "target_type"; payload = item.targetType();
            } else if (condition instanceof RuleCondition.EventIs item) {
                type = "event"; payload = item.eventKey();
            } else if (condition instanceof RuleCondition.WorldIs item) {
                type = "world"; payload = item.worldId().toString();
            } else if (condition instanceof RuleCondition.YRange item) {
                type = "y_range"; payload = item.minimum() + ":" + item.maximum();
            } else {
                throw new IllegalArgumentException("Неизвестный тип условия");
            }
            value.append('\n').append(type).append(':')
                    .append(Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return value.toString();
    }

    static List<RuleCondition> decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return List.of();
        String[] lines = encoded.split("\\n", -1);
        if (!VERSION.equals(lines[0]) || lines.length - 1 > RuleConditionEvaluator.MAX_CONDITIONS) {
            throw new IllegalArgumentException("Неизвестный формат или превышен лимит условий");
        }
        List<RuleCondition> conditions = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            int separator = lines[index].indexOf(':');
            if (separator <= 0) throw new IllegalArgumentException("Повреждённая запись условия");
            String type = lines[index].substring(0, separator);
            String payload = new String(Base64.getUrlDecoder().decode(lines[index].substring(separator + 1)),
                    java.nio.charset.StandardCharsets.UTF_8);
            conditions.add(switch (type) {
                case "actor" -> new RuleCondition.ActorIs(UUID.fromString(payload));
                case "target" -> new RuleCondition.TargetIs(UUID.fromString(payload));
                case "target_type" -> new RuleCondition.TargetTypeIs(payload);
                case "event" -> new RuleCondition.EventIs(payload);
                case "world" -> new RuleCondition.WorldIs(UUID.fromString(payload));
                case "y_range" -> {
                    String[] bounds = payload.split(":", -1);
                    if (bounds.length != 2) throw new IllegalArgumentException("Повреждён диапазон Y");
                    yield new RuleCondition.YRange(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]));
                }
                default -> throw new IllegalArgumentException("Неизвестный тип условия: " + type);
            });
        }
        return List.copyOf(conditions);
    }
}

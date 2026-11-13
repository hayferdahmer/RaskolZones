package ru.raskol.zones.territory.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleConditionCodecTest {
    @Test
    void roundTripsEverySupportedConditionType() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        List<RuleCondition> conditions = List.of(
                new RuleCondition.ActorIs(actor),
                new RuleCondition.TargetIs(target),
                new RuleCondition.TargetTypeIs("container.chest"),
                new RuleCondition.EventIs("block-break"),
                new RuleCondition.WorldIs(world),
                new RuleCondition.YRange(-64, 320)
        );
        assertEquals(conditions, RuleConditionCodec.decode(RuleConditionCodec.encode(conditions)));
    }

    @Test
    void rejectsUnknownCodecVersionAndUnknownCondition() {
        assertThrows(IllegalArgumentException.class, () -> RuleConditionCodec.decode("v9"));
        assertThrows(IllegalArgumentException.class, () -> RuleConditionCodec.decode("v1\nfuture:YWJj"));
    }
}

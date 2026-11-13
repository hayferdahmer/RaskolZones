package ru.raskol.zones.territory.transition;

import java.util.Objects;
import java.util.UUID;
import ru.raskol.zones.territory.Territory;

public record TerritoryTransition(
        UUID playerId,
        Territory territory,
        TransitionType type,
        TransitionReason reason,
        PlayerPosition position
) {
    public TerritoryTransition {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(position, "position");
    }
}

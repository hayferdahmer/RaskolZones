package ru.raskol.zones.territory.transition.event;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.raskol.zones.territory.transition.TerritoryTransition;

public final class TerritoryTransitionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final TerritoryTransition transition;

    public TerritoryTransitionEvent(TerritoryTransition transition) {
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    public TerritoryTransition transition() {
        return transition;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

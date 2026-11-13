package ru.raskol.zones.territory.transition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import ru.raskol.zones.territory.Territory;
import ru.raskol.zones.territory.spatial.SpatialQueryResult;
import ru.raskol.zones.territory.spatial.SpatialQueryService;

public final class TerritoryTransitionEngine {
    private static final double MAX_SAMPLE_DISTANCE = 1.0;
    private static final int MAX_INTERMEDIATE_SAMPLES = 512;

    private final SpatialQueryService spatialQueries;
    private final Map<UUID, PlayerState> players = new HashMap<>();

    public TerritoryTransitionEngine(SpatialQueryService spatialQueries) {
        this.spatialQueries = Objects.requireNonNull(spatialQueries, "spatialQueries");
    }

    public synchronized List<TerritoryTransition> initialize(
            UUID playerId,
            PlayerPosition position,
            TransitionReason reason
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(reason, "reason");
        SpatialQueryResult result = query(position);
        if (!result.isReady()) {
            players.remove(playerId);
            return List.of();
        }
        Map<UUID, Territory> current = territoryMap(result.territories());
        players.put(playerId, new PlayerState(position, current));
        return transitions(playerId, Map.of(), current, reason, position);
    }

    public synchronized List<TerritoryTransition> move(
            UUID playerId,
            PlayerPosition destination,
            TransitionReason requestedReason
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(requestedReason, "requestedReason");

        PlayerState previous = players.get(playerId);
        if (previous == null) {
            return initialize(playerId, destination, requestedReason);
        }

        TransitionReason reason = previous.position().worldId().equals(destination.worldId())
                ? requestedReason
                : TransitionReason.WORLD_CHANGE;
        List<PlayerPosition> samples = samples(previous.position(), destination, reason);
        Map<UUID, Territory> known = previous.territories();
        List<TerritoryTransition> emitted = new ArrayList<>();

        for (PlayerPosition sample : samples) {
            SpatialQueryResult result = query(sample);
            if (!result.isReady()) {
                players.remove(playerId);
                return List.of();
            }
            Map<UUID, Territory> current = territoryMap(result.territories());
            if (!known.keySet().equals(current.keySet())) {
                emitted.addAll(transitions(playerId, known, current, reason, sample));
            }
            known = current;
        }

        players.put(playerId, new PlayerState(destination, known));
        return List.copyOf(emitted);
    }

    public synchronized void forget(UUID playerId) {
        players.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized void clear() {
        players.clear();
    }

    public synchronized int trackedPlayers() {
        return players.size();
    }

    private SpatialQueryResult query(PlayerPosition position) {
        return spatialQueries.query(position.worldId(), position.x(), position.y(), position.z());
    }

    private static List<PlayerPosition> samples(
            PlayerPosition from,
            PlayerPosition to,
            TransitionReason reason
    ) {
        if (reason != TransitionReason.MOVEMENT
                || !from.worldId().equals(to.worldId())) {
            return List.of(to);
        }
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(distance / MAX_SAMPLE_DISTANCE);
        if (steps <= 1) {
            return List.of(to);
        }
        if (steps > MAX_INTERMEDIATE_SAMPLES) {
            return List.of(to);
        }
        List<PlayerPosition> result = new ArrayList<>(steps);
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            result.add(new PlayerPosition(
                    to.worldId(),
                    from.x() + dx * fraction,
                    (int) Math.floor(from.y() + dy * fraction),
                    from.z() + dz * fraction
            ));
        }
        return result;
    }

    private static Map<UUID, Territory> territoryMap(List<Territory> territories) {
        Map<UUID, Territory> result = new LinkedHashMap<>();
        territories.stream()
                .sorted(Comparator.comparing(territory -> territory.id().toString()))
                .forEach(territory -> result.put(territory.id(), territory));
        return Map.copyOf(result);
    }

    private static List<TerritoryTransition> transitions(
            UUID playerId,
            Map<UUID, Territory> previous,
            Map<UUID, Territory> current,
            TransitionReason reason,
            PlayerPosition position
    ) {
        Set<UUID> exited = new LinkedHashSet<>(previous.keySet());
        exited.removeAll(current.keySet());
        Set<UUID> entered = new LinkedHashSet<>(current.keySet());
        entered.removeAll(previous.keySet());
        List<TerritoryTransition> result = new ArrayList<>(exited.size() + entered.size());
        exited.stream().sorted(Comparator.comparing(UUID::toString)).forEach(id ->
                result.add(new TerritoryTransition(
                        playerId, previous.get(id), TransitionType.EXIT, reason, position
                )));
        entered.stream().sorted(Comparator.comparing(UUID::toString)).forEach(id ->
                result.add(new TerritoryTransition(
                        playerId, current.get(id), TransitionType.ENTER, reason, position
                )));
        return result;
    }

    private record PlayerState(PlayerPosition position, Map<UUID, Territory> territories) {
        private PlayerState {
            Objects.requireNonNull(position, "position");
            territories = Map.copyOf(territories);
        }
    }
}

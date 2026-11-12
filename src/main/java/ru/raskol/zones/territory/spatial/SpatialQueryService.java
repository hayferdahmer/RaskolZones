package ru.raskol.zones.territory.spatial;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import ru.raskol.zones.territory.Territory;
import ru.raskol.zones.territory.TerritoryRepository;

public final class SpatialQueryService {
    private final TerritoryRepository repository;
    private final AtomicReference<Map<UUID, WorldSpatialIndex>> worlds =
            new AtomicReference<>(Map.of());
    private final ConcurrentHashMap<UUID, AtomicLong> refreshSequences = new ConcurrentHashMap<>();
    private final AtomicLong snapshotVersions = new AtomicLong();

    public SpatialQueryService(TerritoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Перестраивает снимок мира вне основного потока и публикует его только целиком.
     */
    public CompletionStage<Void> refreshWorld(UUID worldId) {
        Objects.requireNonNull(worldId, "worldId");
        long sequence = refreshSequences.computeIfAbsent(worldId, ignored -> new AtomicLong())
                .incrementAndGet();
        invalidateSnapshot(worldId);

        return repository.listByWorld(worldId).thenAccept(territories -> {
            if (refreshSequences.get(worldId).get() != sequence) {
                return;
            }
            WorldSpatialIndex index = WorldSpatialIndex.build(
                    worldId, snapshotVersions.incrementAndGet(), territories
            );
            worlds.updateAndGet(current -> {
                if (refreshSequences.get(worldId).get() != sequence) {
                    return current;
                }
                var updated = new java.util.HashMap<>(current);
                updated.put(worldId, index);
                return Map.copyOf(updated);
            });
        });
    }

    /**
     * Немедленно делает старый снимок недоступным до завершения следующего построения.
     */
    public void invalidateWorld(UUID worldId) {
        Objects.requireNonNull(worldId, "worldId");
        refreshSequences.computeIfAbsent(worldId, ignored -> new AtomicLong()).incrementAndGet();
        invalidateSnapshot(worldId);
    }

    /**
     * Выгрузка мира удаляет только runtime-снимок, но не сохранённые территории.
     */
    public void unloadWorld(UUID worldId) {
        invalidateWorld(worldId);
    }

    public SpatialQueryResult query(UUID worldId, double x, int y, double z) {
        Objects.requireNonNull(worldId, "worldId");
        WorldSpatialIndex index = worlds.get().get(worldId);
        if (index == null) {
            return new SpatialQueryResult(worldId, SpatialQueryStatus.NOT_READY, 0, 0, List.of());
        }
        return index.query(x, y, z);
    }

    public boolean isWorldReady(UUID worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return worlds.get().containsKey(worldId);
    }

    private void invalidateSnapshot(UUID worldId) {
        worlds.updateAndGet(current -> {
            if (!current.containsKey(worldId)) {
                return current;
            }
            var updated = new java.util.HashMap<>(current);
            updated.remove(worldId);
            return Map.copyOf(updated);
        });
    }
}

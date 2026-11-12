package ru.raskol.zones.territory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import ru.raskol.zones.territory.spatial.SpatialQueryService;
import ru.raskol.zones.territory.type.TerritoryTypeRegistry;

public final class TerritoryService {
    private final TerritoryRepository repository;
    private final TerritoryTypeRegistry types;
    private final SpatialQueryService spatialQueries;

    public TerritoryService(TerritoryRepository repository, TerritoryTypeRegistry types) {
        this(repository, types, null);
    }

    public TerritoryService(
            TerritoryRepository repository,
            TerritoryTypeRegistry types,
            SpatialQueryService spatialQueries
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.types = Objects.requireNonNull(types, "types");
        this.spatialQueries = spatialQueries;
    }

    public CompletionStage<Territory> create(TerritoryDraft draft) {
        Objects.requireNonNull(draft, "draft");
        types.require(draft.typeId());
        Instant now = Instant.now();
        Territory territory = new Territory(
                UUID.randomUUID(),
                draft.worldId(),
                draft.parentId(),
                draft.typeId(),
                draft.name(),
                draft.weight(),
                draft.geometry(),
                TerritoryStatus.ACTIVE,
                0,
                now,
                now
        );
        return persistAndRefresh(territory.worldId(), () -> repository.create(territory));
    }

    public CompletionStage<Optional<Territory>> find(UUID territoryId) {
        return repository.find(Objects.requireNonNull(territoryId, "territoryId"));
    }

    public CompletionStage<List<Territory>> listByWorld(UUID worldId) {
        return repository.listByWorld(Objects.requireNonNull(worldId, "worldId"));
    }

    public CompletionStage<Territory> update(UUID territoryId, long expectedVersion, TerritoryDraft draft) {
        Objects.requireNonNull(territoryId, "territoryId");
        Objects.requireNonNull(draft, "draft");
        types.require(draft.typeId());
        return repository.find(territoryId).thenCompose(existingOptional -> {
            Territory existing = existingOptional.orElseThrow(() -> new TerritoryException(
                    TerritoryException.Code.NOT_FOUND,
                    "Территория не найдена: " + territoryId
            ));
            if (existing.version() != expectedVersion) {
                throw new TerritoryException(TerritoryException.Code.VERSION_CONFLICT,
                        "Устаревшая версия территории: ожидалась " + expectedVersion
                                + ", текущая " + existing.version());
            }
            if (!existing.worldId().equals(draft.worldId())) {
                throw new IllegalArgumentException("Мир территории нельзя изменить обычным обновлением");
            }
            if (existing.status() == TerritoryStatus.ARCHIVED) {
                throw new IllegalStateException("Архивную территорию нельзя редактировать");
            }
            Territory updated = new Territory(
                    existing.id(),
                    existing.worldId(),
                    draft.parentId(),
                    draft.typeId(),
                    draft.name(),
                    draft.weight(),
                    draft.geometry(),
                    existing.status(),
                    expectedVersion + 1,
                    existing.createdAt(),
                    Instant.now()
            );
            return persistAndRefresh(updated.worldId(), () -> repository.update(updated, expectedVersion));
        });
    }

    private CompletionStage<Territory> persistAndRefresh(
            UUID worldId,
            Supplier<CompletionStage<Territory>> persistence
    ) {
        if (spatialQueries == null) {
            return persistence.get();
        }

        spatialQueries.invalidateWorld(worldId);
        CompletableFuture<Territory> result = new CompletableFuture<>();
        persistence.get().whenComplete((saved, persistenceFailure) ->
                spatialQueries.refreshWorld(worldId).whenComplete((ignored, refreshFailure) -> {
                    if (persistenceFailure != null) {
                        result.completeExceptionally(unwrap(persistenceFailure));
                    } else if (refreshFailure != null) {
                        result.completeExceptionally(unwrap(refreshFailure));
                    } else {
                        result.complete(saved);
                    }
                })
        );
        return result;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public CompletionStage<Territory> archive(UUID territoryId, long expectedVersion) {
        Objects.requireNonNull(territoryId, "territoryId");
        return repository.find(territoryId).thenCompose(existingOptional -> {
            Territory existing = existingOptional.orElseThrow(() -> new TerritoryException(
                    TerritoryException.Code.NOT_FOUND,
                    "Территория не найдена: " + territoryId
            ));
            if (existing.version() != expectedVersion) {
                throw new TerritoryException(TerritoryException.Code.VERSION_CONFLICT,
                        "Устаревшая версия территории: ожидалась " + expectedVersion
                                + ", текущая " + existing.version());
            }
            if (existing.status() == TerritoryStatus.ARCHIVED) {
                return java.util.concurrent.CompletableFuture.completedFuture(existing);
            }
            Territory archived = new Territory(
                    existing.id(),
                    existing.worldId(),
                    existing.parentId(),
                    existing.typeId(),
                    existing.name(),
                    existing.weight(),
                    existing.geometry(),
                    TerritoryStatus.ARCHIVED,
                    expectedVersion + 1,
                    existing.createdAt(),
                    Instant.now()
            );
            return persistAndRefresh(archived.worldId(), () -> repository.update(archived, expectedVersion));
        });
    }
}

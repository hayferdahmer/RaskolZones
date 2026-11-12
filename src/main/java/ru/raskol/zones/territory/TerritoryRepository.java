package ru.raskol.zones.territory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface TerritoryRepository {
    CompletionStage<Territory> create(Territory territory);

    CompletionStage<Optional<Territory>> find(UUID territoryId);

    CompletionStage<List<Territory>> listByWorld(UUID worldId);

    CompletionStage<Territory> update(Territory territory, long expectedVersion);
}

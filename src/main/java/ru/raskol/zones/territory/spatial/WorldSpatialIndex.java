package ru.raskol.zones.territory.spatial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.raskol.zones.territory.Territory;
import ru.raskol.zones.territory.TerritoryStatus;

final class WorldSpatialIndex {
    private static final int CELL_SIZE = 128;
    private static final long MAX_CELLS_PER_TERRITORY = 4_096;

    private final UUID worldId;
    private final long version;
    private final Map<Cell, List<Territory>> cells;
    private final List<Territory> oversizedTerritories;

    private WorldSpatialIndex(UUID worldId, long version,
                              Map<Cell, List<Territory>> cells,
                              List<Territory> oversizedTerritories) {
        this.worldId = worldId;
        this.version = version;
        Map<Cell, List<Territory>> immutableCells = new HashMap<>();
        cells.forEach((cell, territories) -> immutableCells.put(cell, List.copyOf(territories)));
        this.cells = Map.copyOf(immutableCells);
        this.oversizedTerritories = List.copyOf(oversizedTerritories);
    }

    static WorldSpatialIndex build(UUID worldId, long version, List<Territory> territories) {
        Map<Cell, List<Territory>> cells = new HashMap<>();
        List<Territory> oversized = new ArrayList<>();

        for (Territory territory : territories) {
            if (!worldId.equals(territory.worldId())) {
                throw new IllegalArgumentException("Снимок содержит территорию из другого мира");
            }
            if (territory.status() != TerritoryStatus.ACTIVE) {
                continue;
            }

            long minCellX = cellCoordinate(territory.geometry().minX());
            long maxCellX = cellCoordinate(territory.geometry().maxX());
            long minCellZ = cellCoordinate(territory.geometry().minZ());
            long maxCellZ = cellCoordinate(territory.geometry().maxZ());
            long xCount = maxCellX - minCellX + 1;
            long zCount = maxCellZ - minCellZ + 1;
            if (xCount > MAX_CELLS_PER_TERRITORY
                    || zCount > MAX_CELLS_PER_TERRITORY
                    || xCount * zCount > MAX_CELLS_PER_TERRITORY) {
                oversized.add(territory);
                continue;
            }

            for (long cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (long cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    cells.computeIfAbsent(new Cell(cellX, cellZ), ignored -> new ArrayList<>())
                            .add(territory);
                }
            }
        }
        return new WorldSpatialIndex(worldId, version, cells, oversized);
    }

    SpatialQueryResult query(double x, int y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Координаты X и Z должны быть конечными числами");
        }

        Map<UUID, Territory> candidates = new LinkedHashMap<>();
        List<Territory> local = cells.get(new Cell(cellCoordinate(x), cellCoordinate(z)));
        if (local != null) {
            for (Territory territory : local) {
                candidates.put(territory.id(), territory);
            }
        }
        for (Territory territory : oversizedTerritories) {
            candidates.put(territory.id(), territory);
        }

        List<Territory> matches = candidates.values().stream()
                .filter(territory -> territory.geometry().contains(x, y, z))
                .sorted(Comparator.comparing(territory -> territory.id().toString()))
                .toList();
        return new SpatialQueryResult(worldId, SpatialQueryStatus.READY,
                version, candidates.size(), matches);
    }

    private static long cellCoordinate(double coordinate) {
        return (long) Math.floor(coordinate / CELL_SIZE);
    }

    private record Cell(long x, long z) {
    }
}

package ru.raskol.zones.territory.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raskol.zones.foundation.storage.SqliteStorageService;
import ru.raskol.zones.territory.SqliteTerritoryRepository;
import ru.raskol.zones.territory.Territory;
import ru.raskol.zones.territory.TerritoryStatus;
import ru.raskol.zones.territory.geometry.CuboidGeometry;
import ru.raskol.zones.territory.spatial.SpatialQueryService;

class TerritoryRulePipelineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void evaluatesStoredRuleAgainstSpatialSnapshot() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("rules.db"))) {
            storage.initialize().toCompletableFuture().get();
            UUID worldId = UUID.randomUUID();
            storage.transaction(connection -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO world_registry(
                            world_uuid, world_name, first_seen_at, last_seen_at, availability
                        ) VALUES (?, 'test-world', ?, ?, 'LOADED')
                        """)) {
                    statement.setString(1, worldId.toString());
                    statement.setString(2, Instant.now().toString());
                    statement.setString(3, Instant.now().toString());
                    statement.executeUpdate();
                }
                return null;
            }).toCompletableFuture().get();

            var territoryRepository = new SqliteTerritoryRepository(storage);
            Instant now = Instant.now();
            Territory territory = new Territory(
                    UUID.randomUUID(), worldId, null, "region", "Test Region", 10,
                    new CuboidGeometry(0, 20, 0, 100, 0, 20),
                    TerritoryStatus.ACTIVE, 0, now, now
            );
            territoryRepository.create(territory).toCompletableFuture().get();

            var spatial = new SpatialQueryService(territoryRepository);
            spatial.refreshWorld(worldId).toCompletableFuture().get();
            var ruleRepository = new SqliteTerritoryRuleRepository(storage);
            ruleRepository.create(new TerritoryRuleDefinition(
                    "build.deny", territory.id(), "block.place",
                    RuleEffect.DENY, RuleSafetyClass.ORDINARY,
                    new RuleSpecificity(100), "raskol-core",
                    true, 0, Instant.now()
            )).toCompletableFuture().get();

            var pipeline = new TerritoryRuleEvaluationPipeline(
                    spatial, ruleRepository,
                    new TerritoryRuleResolutionEngine(SourceAuthorityRegistry.coreOnly()),
                    territoryRepository
            );
            var result = pipeline.evaluate(worldId, 5, 64, 5, "block.place")
                    .toCompletableFuture().get();

            assertEquals(RuleEvaluationStatus.RESOLVED, result.status());
            assertEquals("build.deny", result.resolution().orElseThrow()
                    .winner().orElseThrow().ruleId());
            assertEquals(1, result.spatialSnapshotVersion());
        }
    }

    @Test
    void doesNotTreatUnavailableSpatialSnapshotAsAnEmptyPermissionDecision() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("not-ready.db"))) {
            storage.initialize().toCompletableFuture().get();
            var territoryRepository = new SqliteTerritoryRepository(storage);
            var spatial = new SpatialQueryService(territoryRepository);
            var ruleRepository = new SqliteTerritoryRuleRepository(storage);
            var pipeline = new TerritoryRuleEvaluationPipeline(
                    spatial, ruleRepository,
                    new TerritoryRuleResolutionEngine(SourceAuthorityRegistry.coreOnly()),
                    territoryRepository
            );

            var result = pipeline.evaluate(UUID.randomUUID(), 0, 64, 0, "pvp.attack")
                    .toCompletableFuture().get();

            assertEquals(RuleEvaluationStatus.INDETERMINATE, result.status());
            assertTrue(result.resolution().isEmpty());
        }
    }
}

package ru.raskol.zones.territory.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raskol.zones.foundation.storage.SqliteStorageService;

class RuleConditionPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsConditionsForDirectRulesAndProfiles() throws Exception {
        try (var storage = new SqliteStorageService(temporaryDirectory.resolve("conditions.db"))) {
            storage.initialize().toCompletableFuture().get();
            UUID worldId = UUID.randomUUID();
            UUID territoryId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            storage.transaction(connection -> {
                try (var world = connection.prepareStatement("""
                        INSERT INTO world_registry(world_uuid, world_name, first_seen_at, last_seen_at, availability)
                        VALUES (?, 'world', ?, ?, 'LOADED')
                        """)) {
                    world.setString(1, worldId.toString());
                    world.setString(2, Instant.now().toString());
                    world.setString(3, Instant.now().toString());
                    world.executeUpdate();
                }
                try (var territory = connection.prepareStatement("""
                        INSERT INTO territories(
                            territory_uuid, world_uuid, parent_uuid, type_id, name, weight,
                            lifecycle_status, version, created_at, updated_at
                        ) VALUES (?, ?, NULL, 'town', 'Town', 10, 'ACTIVE', 0, ?, ?)
                        """)) {
                    territory.setString(1, territoryId.toString());
                    territory.setString(2, worldId.toString());
                    territory.setString(3, Instant.now().toString());
                    territory.setString(4, Instant.now().toString());
                    territory.executeUpdate();
                }
                return null;
            }).toCompletableFuture().get();

            var directConditions = List.<RuleCondition>of(
                    new RuleCondition.ActorIs(actorId),
                    new RuleCondition.YRange(0, 120)
            );
            var rule = new TerritoryRuleDefinition(
                    "town.build", territoryId, "block.place", RuleEffect.DENY,
                    RuleSafetyClass.ORDINARY, new RuleSpecificity(500), "raskol-zones",
                    true, 0, Instant.now(), directConditions
            );
            var ruleRepository = new SqliteTerritoryRuleRepository(storage);
            ruleRepository.create(rule).toCompletableFuture().get();
            assertEquals(directConditions, ruleRepository.findApplicable(
                    List.of(territoryId.toString()), "block.place"
            ).toCompletableFuture().get().getFirst().conditions());

            var profileConditions = List.<RuleCondition>of(
                    new RuleCondition.TargetTypeIs("container.chest"),
                    new RuleCondition.EventIs("inventory-open")
            );
            var profile = new TerritoryRuleProfileDefinition(
                    "town.container", "town", "container.open", RuleEffect.DENY,
                    RuleSafetyClass.ORDINARY, new RuleSpecificity(400), "raskol-zones",
                    true, 0, Instant.now(), profileConditions
            );
            var profileRepository = new SqliteTerritoryRuleProfileRepository(storage);
            profileRepository.create(profile).toCompletableFuture().get();
            assertEquals(profileConditions, profileRepository.findApplicable(
                    List.of("town"), "container.open"
            ).toCompletableFuture().get().getFirst().conditions());
        }
    }
}

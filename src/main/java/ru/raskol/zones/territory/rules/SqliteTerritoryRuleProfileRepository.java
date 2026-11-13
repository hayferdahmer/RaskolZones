package ru.raskol.zones.territory.rules;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import ru.raskol.zones.foundation.storage.StorageService;

public final class SqliteTerritoryRuleProfileRepository implements TerritoryRuleProfileRepository {
    private final StorageService storage;

    public SqliteTerritoryRuleProfileRepository(StorageService storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<TerritoryRuleProfileDefinition> create(TerritoryRuleProfileDefinition profile) {
        Objects.requireNonNull(profile, "profile");
        if (profile.version() != 0) {
            throw new IllegalArgumentException("Новый профиль должен иметь версию 0");
        }
        return storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO territory_rule_profiles(
                        profile_id, territory_type_id, action_key, effect, safety_class,
                        specificity_rank, source_id, enabled, version, updated_at, conditions
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                bind(statement, profile);
                statement.executeUpdate();
            }
            return profile;
        });
    }

    @Override
    public CompletionStage<List<TerritoryRuleProfileDefinition>> findApplicable(
            Collection<String> territoryTypeIds,
            String actionKey
    ) {
        Objects.requireNonNull(territoryTypeIds, "territoryTypeIds");
        Objects.requireNonNull(actionKey, "actionKey");
        List<String> ids = territoryTypeIds.stream().map(Objects::requireNonNull).distinct().sorted().toList();
        if (ids.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return storage.transaction(connection -> {
            List<TerritoryRuleProfileDefinition> profiles = new ArrayList<>();
            String sql = "SELECT profile_id, territory_type_id, action_key, effect, safety_class, "
                    + "specificity_rank, source_id, enabled, version, updated_at, conditions "
                    + "FROM territory_rule_profiles WHERE enabled = 1 AND action_key = ? "
                    + "AND territory_type_id IN (" + placeholders + ") "
                    + "ORDER BY profile_id, territory_type_id";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, actionKey.strip().toLowerCase(java.util.Locale.ROOT));
                for (int index = 0; index < ids.size(); index++) {
                    statement.setString(index + 2, ids.get(index));
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        profiles.add(read(result));
                    }
                }
            }
            return List.copyOf(profiles);
        });
    }

    @Override
    public CompletionStage<TerritoryRuleProfileDefinition> update(
            TerritoryRuleProfileDefinition profile,
            long expectedVersion
    ) {
        Objects.requireNonNull(profile, "profile");
        if (expectedVersion < 0 || profile.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("Версия профиля должна увеличиться ровно на единицу");
        }
        return storage.transaction(connection -> {
            int changed;
            try (var statement = connection.prepareStatement("""
                    UPDATE territory_rule_profiles
                    SET territory_type_id = ?, action_key = ?, effect = ?, safety_class = ?,
                        specificity_rank = ?, source_id = ?, enabled = ?, version = ?, updated_at = ?, conditions = ?
                    WHERE profile_id = ? AND version = ?
                    """)) {
                statement.setString(1, profile.territoryTypeId());
                statement.setString(2, profile.actionKey());
                statement.setString(3, profile.effect().name());
                statement.setString(4, profile.safetyClass().name());
                statement.setInt(5, profile.specificity().rank());
                statement.setString(6, profile.sourceId());
                statement.setInt(7, profile.enabled() ? 1 : 0);
                statement.setLong(8, profile.version());
                statement.setString(9, profile.updatedAt().toString());
                statement.setString(10, RuleConditionCodec.encode(profile.conditions()));
                statement.setString(11, profile.profileId());
                statement.setLong(12, expectedVersion);
                changed = statement.executeUpdate();
            }
            if (changed != 1) {
                throw new IllegalStateException("Профиль не найден или его версия уже изменилась: "
                        + profile.profileId());
            }
            return profile;
        });
    }

    private static void bind(java.sql.PreparedStatement statement, TerritoryRuleProfileDefinition profile)
            throws SQLException {
        statement.setString(1, profile.profileId());
        statement.setString(2, profile.territoryTypeId());
        statement.setString(3, profile.actionKey());
        statement.setString(4, profile.effect().name());
        statement.setString(5, profile.safetyClass().name());
        statement.setInt(6, profile.specificity().rank());
        statement.setString(7, profile.sourceId());
        statement.setInt(8, profile.enabled() ? 1 : 0);
        statement.setLong(9, profile.version());
        statement.setString(10, profile.updatedAt().toString());
        statement.setString(11, RuleConditionCodec.encode(profile.conditions()));
    }

    private static TerritoryRuleProfileDefinition read(ResultSet result) throws SQLException {
        return new TerritoryRuleProfileDefinition(
                result.getString("profile_id"),
                result.getString("territory_type_id"),
                result.getString("action_key"),
                RuleEffect.valueOf(result.getString("effect")),
                RuleSafetyClass.valueOf(result.getString("safety_class")),
                new RuleSpecificity(result.getInt("specificity_rank")),
                result.getString("source_id"),
                result.getInt("enabled") == 1,
                result.getLong("version"),
                Instant.parse(result.getString("updated_at")),
                RuleConditionCodec.decode(result.getString("conditions"))
        );
    }
}

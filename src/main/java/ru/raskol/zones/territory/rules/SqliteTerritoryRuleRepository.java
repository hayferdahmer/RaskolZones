package ru.raskol.zones.territory.rules;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import ru.raskol.zones.foundation.storage.StorageService;

public final class SqliteTerritoryRuleRepository implements TerritoryRuleRepository {
    private final StorageService storage;

    public SqliteTerritoryRuleRepository(StorageService storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<TerritoryRuleDefinition> create(TerritoryRuleDefinition rule) {
        Objects.requireNonNull(rule, "rule");
        if (rule.version() != 0) {
            throw new IllegalArgumentException("Новое правило должно иметь версию 0");
        }
        return storage.transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO territory_rules(
                        rule_id, territory_uuid, action_key, effect, safety_class,
                        specificity_rank, source_id, enabled, version, updated_at, conditions
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                bind(statement, rule);
                statement.executeUpdate();
            }
            return rule;
        });
    }

    @Override
    public CompletionStage<List<TerritoryRuleDefinition>> findApplicable(
            Collection<String> territoryIds,
            String actionKey
    ) {
        Objects.requireNonNull(territoryIds, "territoryIds");
        Objects.requireNonNull(actionKey, "actionKey");
        List<String> ids = territoryIds.stream().map(Objects::requireNonNull).distinct().sorted().toList();
        if (ids.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return storage.transaction(connection -> {
            List<TerritoryRuleDefinition> rules = new ArrayList<>();
            String sql = "SELECT rule_id, territory_uuid, action_key, effect, safety_class, "
                    + "specificity_rank, source_id, enabled, version, updated_at, conditions "
                    + "FROM territory_rules WHERE enabled = 1 AND action_key = ? "
                    + "AND territory_uuid IN (" + placeholders + ") "
                    + "ORDER BY rule_id, territory_uuid";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, actionKey.strip().toLowerCase(java.util.Locale.ROOT));
                for (int index = 0; index < ids.size(); index++) {
                    statement.setString(index + 2, ids.get(index));
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rules.add(read(result));
                    }
                }
            }
            return List.copyOf(rules);
        });
    }

    @Override
    public CompletionStage<TerritoryRuleDefinition> update(
            TerritoryRuleDefinition rule,
            long expectedVersion
    ) {
        Objects.requireNonNull(rule, "rule");
        if (expectedVersion < 0 || rule.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("Версия правила должна увеличиться ровно на единицу");
        }
        return storage.transaction(connection -> {
            int changed;
            try (var statement = connection.prepareStatement("""
                    UPDATE territory_rules
                    SET territory_uuid = ?, action_key = ?, effect = ?, safety_class = ?,
                        specificity_rank = ?, source_id = ?, enabled = ?, version = ?, updated_at = ?, conditions = ?
                    WHERE rule_id = ? AND version = ?
                    """)) {
                statement.setString(1, rule.territoryId().toString());
                statement.setString(2, rule.actionKey());
                statement.setString(3, rule.effect().name());
                statement.setString(4, rule.safetyClass().name());
                statement.setInt(5, rule.specificity().rank());
                statement.setString(6, rule.sourceId());
                statement.setInt(7, rule.enabled() ? 1 : 0);
                statement.setLong(8, rule.version());
                statement.setString(9, rule.updatedAt().toString());
                statement.setString(10, RuleConditionCodec.encode(rule.conditions()));
                statement.setString(11, rule.ruleId());
                statement.setLong(12, expectedVersion);
                changed = statement.executeUpdate();
            }
            if (changed != 1) {
                throw new IllegalStateException("Правило не найдено или его версия уже изменилась: " + rule.ruleId());
            }
            return rule;
        });
    }

    private static void bind(java.sql.PreparedStatement statement, TerritoryRuleDefinition rule)
            throws SQLException {
        statement.setString(1, rule.ruleId());
        statement.setString(2, rule.territoryId().toString());
        statement.setString(3, rule.actionKey());
        statement.setString(4, rule.effect().name());
        statement.setString(5, rule.safetyClass().name());
        statement.setInt(6, rule.specificity().rank());
        statement.setString(7, rule.sourceId());
        statement.setInt(8, rule.enabled() ? 1 : 0);
        statement.setLong(9, rule.version());
        statement.setString(10, rule.updatedAt().toString());
        statement.setString(11, RuleConditionCodec.encode(rule.conditions()));
    }

    private static TerritoryRuleDefinition read(ResultSet result) throws SQLException {
        return new TerritoryRuleDefinition(
                result.getString("rule_id"),
                UUID.fromString(result.getString("territory_uuid")),
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

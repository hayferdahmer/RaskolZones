package ru.raskol.zones.territory.rules;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SourceAuthorityRegistry {
    private final Map<String, Integer> authorityLevels;

    public SourceAuthorityRegistry(Map<String, Integer> authorityLevels) {
        Objects.requireNonNull(authorityLevels, "authorityLevels");
        authorityLevels.forEach((sourceId, level) -> {
            if (sourceId == null || !sourceId.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("Некорректный ID доверенного источника");
            }
            if (level == null || level < 0 || level > 1_000) {
                throw new IllegalArgumentException("Уровень авторитетности должен находиться в диапазоне 0..1000");
            }
        });
        this.authorityLevels = Map.copyOf(authorityLevels);
    }

    public static SourceAuthorityRegistry coreOnly() {
        return new SourceAuthorityRegistry(Map.of("raskol-core", 1_000));
    }

    public Optional<Integer> authorityOf(String sourceId) {
        return Optional.ofNullable(authorityLevels.get(Objects.requireNonNull(sourceId, "sourceId")));
    }
}

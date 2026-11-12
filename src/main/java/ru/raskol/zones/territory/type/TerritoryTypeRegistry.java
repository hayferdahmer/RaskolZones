package ru.raskol.zones.territory.type;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryTypeRegistry {
    private final Map<String, TerritoryTypeDefinition> definitions = new ConcurrentHashMap<>();

    public TerritoryTypeRegistry(Collection<TerritoryTypeDefinition> builtIns) {
        Objects.requireNonNull(builtIns, "builtIns");
        for (TerritoryTypeDefinition definition : builtIns) {
            if (!definition.builtIn()) {
                throw new IllegalArgumentException("Начальный реестр принимает только встроенные типы");
            }
            if (definitions.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Повторный тип территории: " + definition.id());
            }
        }
    }

    public static TerritoryTypeRegistry standard() {
        return new TerritoryTypeRegistry(java.util.List.of(
                builtIn("continent", "Континент", "geography"),
                builtIn("kingdom", "Королевство / государство", "geography"),
                builtIn("region", "Регион", "geography"),
                builtIn("city", "Город", "settlement"),
                builtIn("village", "Деревня", "settlement"),
                builtIn("dungeon", "Подземелье", "location"),
                builtIn("castle", "Замок", "location"),
                builtIn("arena", "Арена", "activity"),
                builtIn("pvp", "PvP-зона", "activity"),
                builtIn("pve", "PvE-зона", "activity"),
                builtIn("safe", "Безопасная зона", "special"),
                builtIn("resource", "Ресурсная зона", "location"),
                builtIn("quest", "Квестовая локация", "location"),
                builtIn("clan", "Клановая территория", "settlement")
        ));
    }

    public void registerCustom(TerritoryTypeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definition.builtIn()) {
            throw new IllegalArgumentException("Пользовательский тип не может объявляться встроенным");
        }
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Тип территории уже зарегистрирован: " + definition.id());
        }
    }

    public Optional<TerritoryTypeDefinition> find(String typeId) {
        Objects.requireNonNull(typeId, "typeId");
        return Optional.ofNullable(definitions.get(typeId.strip().toLowerCase(java.util.Locale.ROOT)));
    }

    public TerritoryTypeDefinition require(String typeId) {
        return find(typeId).orElseThrow(() -> new TerritoryException(
                TerritoryException.Code.TYPE_NOT_FOUND,
                "Неизвестный тип территории: " + typeId
        ));
    }

    public Collection<TerritoryTypeDefinition> all() {
        return definitions.values().stream()
                .sorted(java.util.Comparator.comparing(TerritoryTypeDefinition::id))
                .toList();
    }

    private static TerritoryTypeDefinition builtIn(String id, String name, String category) {
        return new TerritoryTypeDefinition(id, name, category, true);
    }
}

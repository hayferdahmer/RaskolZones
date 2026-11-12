package ru.raskol.zones.world;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorldRecord(
        UUID worldId,
        String name,
        Instant firstSeenAt,
        Instant lastSeenAt,
        WorldAvailability availability
) {
    public WorldRecord {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        Objects.requireNonNull(availability, "availability");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Имя мира не может быть пустым");
        }
        if (lastSeenAt.isBefore(firstSeenAt)) {
            throw new IllegalArgumentException("Последнее обнаружение мира раньше первого");
        }
    }
}

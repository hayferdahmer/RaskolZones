package ru.raskol.zones.foundation.health;

import java.time.Instant;
import java.util.Objects;

public record HealthSnapshot(HealthStatus status, Instant changedAt, String detail) {

    public HealthSnapshot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(changedAt, "changedAt");
        detail = detail == null ? "" : detail;
    }
}
